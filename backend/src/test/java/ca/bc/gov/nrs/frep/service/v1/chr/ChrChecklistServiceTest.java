package ca.bc.gov.nrs.frep.service.v1.chr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.ChrConstants;
import ca.bc.gov.nrs.frep.configuration.ObjectStorageProperties;
import ca.bc.gov.nrs.frep.service.v1.ObjectStorageService;
import ca.bc.gov.nrs.frep.entity.ChrChecklist;
import ca.bc.gov.nrs.frep.exception.FrepApiRuntimeException;
import ca.bc.gov.nrs.frep.exception.InvalidParameterException;
import ca.bc.gov.nrs.frep.service.v1.ChrChecklistPersistenceService;
import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
import ca.bc.gov.nrs.frep.struct.v1.frep.Picture;
import ca.bc.gov.nrs.frep.validation.ChrSubmitValidationService;
import ca.bc.gov.nrs.frep.repository.v1.ChrChecklistRepository;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import java.util.ArrayList;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChrChecklistServiceTest {

  @Mock
  private ChrChecklistPersistenceService persistenceService;
  @Mock
  private ChrChecklistRepository checklistRepository;
  @Mock
  private ChrSubmitValidationService submitValidationService;
  @Mock
  private ObjectStorageService objectStorageService;
  @Mock
  private LoggedUserHelper loggedUserHelper;
  @Mock
  private ca.bc.gov.nrs.frep.service.v1.frep.FamUserDirectoryService famUserDirectoryService;
  @Mock
  private ca.bc.gov.nrs.frep.service.v1.VirusScanner virusScanner;

  private ChrChecklistService service;

  @BeforeEach
  void setUp() {
    service = new ChrChecklistService(
        persistenceService,
        checklistRepository,
        submitValidationService,
        objectStorageService,
        new ObjectStorageProperties("http://s3", "bucket", "key", "secret"),
        loggedUserHelper,
        famUserDirectoryService,
        virusScanner
    );
  }

  @Test
  void saveChecklistRejectsMismatchedStatusTransition() {
    when(checklistRepository.getChecklistStatus(1001L)).thenReturn(ChrConstants.FrepChecklistStatusCode.SUB);

    CheckList checklist = new CheckList();
    checklist.setChecklistID("1001");
    checklist.setStatus(ChrConstants.FrepChecklistStatusCode.ACT);
    checklist.setRevisionCount("1");

    assertThrows(InvalidParameterException.class, () -> service.saveChecklist(checklist));
  }

  @Test
  void submitChecklistPersistsSubmittedStatusWhenValidationPasses() {
    when(loggedUserHelper.getLoggedUserId()).thenReturn("IDIR\\user");
    when(checklistRepository.getChecklistStatus(1001L)).thenReturn(ChrConstants.FrepChecklistStatusCode.ACT);
    when(submitValidationService.validateBeforeSubmit(any())).thenReturn(List.of());

    ChrChecklist entity = new ChrChecklist();
    entity.setChrChecklistId(1001L);
    when(persistenceService.getAcceptedSiteForChr(1001L)).thenReturn(entity);

    CheckList checklist = new CheckList();
    checklist.setChecklistID("1001");
    checklist.setStatus(ChrConstants.FrepChecklistStatusCode.ACT);
    checklist.setRevisionCount("1");

    assertThrows(FrepApiRuntimeException.class, () -> service.submitChecklist(1001L, checklist));
    verify(persistenceService).saveChecklist(any(), eq("IDIR\\user"));
  }

  @Test
  void unsubmitChecklistDelegatesToPersistenceWhenSubmitted() {
    when(loggedUserHelper.getLoggedUserId()).thenReturn("IDIR\\user");
    when(checklistRepository.getChecklistStatus(1001L)).thenReturn(ChrConstants.FrepChecklistStatusCode.SUB);
    when(persistenceService.getAcceptedSiteForChr(1001L)).thenReturn(new ChrChecklist());

    // The trailing getChecklist() maps a bare entity and throws (wrapped), like the submit test above;
    // we only assert that a submitted checklist passes the guard and delegates the SUB → ACT transition.
    assertThrows(FrepApiRuntimeException.class, () -> service.unsubmitChecklist(1001L));
    verify(persistenceService).unsubmitChecklist(1001L, "IDIR\\user");
  }

  @Test
  void unsubmitChecklistRejectsWhenNotSubmitted() {
    when(checklistRepository.getChecklistStatus(1001L)).thenReturn(ChrConstants.FrepChecklistStatusCode.ACT);

    assertThrows(InvalidParameterException.class, () -> service.unsubmitChecklist(1001L));
    verify(persistenceService, never()).unsubmitChecklist(anyLong(), anyString());
  }

  @Test
  void releaseCheckoutActivatesWhenGuidMatches() {
    UUID guid = UUID.fromString("00000000-0000-0000-0000-000000000001");
    when(loggedUserHelper.getLoggedUserId()).thenReturn("IDIR\\user");
    when(checklistRepository.getChecklistStatus(1001L)).thenReturn(ChrConstants.FrepChecklistStatusCode.RDO);
    when(checklistRepository.getDeviceCheckoutGuid(1001L)).thenReturn(guid);
    when(persistenceService.getAcceptedSiteForChr(1001L)).thenReturn(new ChrChecklist());

    // Trailing getChecklist() maps a bare entity and throws (wrapped); we assert the release delegated.
    assertThrows(FrepApiRuntimeException.class, () -> service.releaseCheckout(1001L, guid.toString()));
    verify(persistenceService).activateChecklist(1001L, "IDIR\\user");
  }

  @Test
  void releaseCheckoutRejectsWhenGuidHeldByAnotherDevice() {
    when(checklistRepository.getChecklistStatus(1001L)).thenReturn(ChrConstants.FrepChecklistStatusCode.RDO);
    when(checklistRepository.getDeviceCheckoutGuid(1001L))
        .thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    assertThrows(
        InvalidParameterException.class,
        () -> service.releaseCheckout(1001L, "00000000-0000-0000-0000-000000000001"));
    verify(persistenceService, never()).activateChecklist(anyLong(), anyString());
  }

  @Test
  void releaseCheckoutIsNoOpWhenNotCheckedOut() {
    when(checklistRepository.getChecklistStatus(1001L)).thenReturn(ChrConstants.FrepChecklistStatusCode.ACT);
    when(persistenceService.getAcceptedSiteForChr(1001L)).thenReturn(new ChrChecklist());

    // Already active — returns current state (getChecklist maps a bare entity → wrapped throw) without
    // touching the guid or activating.
    assertThrows(FrepApiRuntimeException.class, () -> service.releaseCheckout(1001L, "any-guid"));
    verify(checklistRepository, never()).getDeviceCheckoutGuid(anyLong());
    verify(persistenceService, never()).activateChecklist(anyLong(), anyString());
  }

  @Test
  void addPhotoRejectsABlankDescription() {
    when(checklistRepository.getChecklistStatus(1001L))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.ACT);
    MockMultipartFile photo =
        new MockMultipartFile("file", "site.jpg", "image/jpeg", new byte[] {1, 2, 3});

    InvalidParameterException ex = assertThrows(InvalidParameterException.class,
        () -> service.addPhoto(1001L, photo, "  ", null, null, null));

    assertTrue(ex.getMessage().contains("description is required"));
    verifyNoInteractions(virusScanner);
  }

  @Test
  void addPhotoRejectsANonImageFile() {
    when(checklistRepository.getChecklistStatus(1001L))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.ACT);
    MockMultipartFile notAnImage =
        new MockMultipartFile("file", "notes.pdf", "application/pdf", new byte[] {1, 2, 3});

    InvalidParameterException ex = assertThrows(InvalidParameterException.class,
        () -> service.addPhoto(1001L, notAnImage, "A description", null, null, null));

    assertTrue(ex.getMessage().contains("Only image files"));
  }

  @Test
  void addPhotoRejectsAnEmptyFile() {
    when(checklistRepository.getChecklistStatus(1001L))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.ACT);
    MockMultipartFile empty = new MockMultipartFile("file", "site.jpg", "image/jpeg", new byte[0]);

    assertThrows(InvalidParameterException.class,
        () -> service.addPhoto(1001L, empty, "A description", null, null, null));
    verifyNoInteractions(virusScanner);
  }

  @Test
  void addPhotoScansBeforePersisting() {
    when(checklistRepository.getChecklistStatus(1001L))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.ACT);
    when(loggedUserHelper.getLoggedUserId()).thenReturn("IDIR\\tester");
    byte[] content = {1, 2, 3};
    MockMultipartFile photo = new MockMultipartFile("file", "site.jpg", "image/jpeg", content);

    service.addPhoto(1001L, photo, " A description ", "2026-05-01", 42L, null);

    InOrder order = inOrder(virusScanner, persistenceService);
    order.verify(virusScanner).scanOrThrow(content, "site.jpg");
    order.verify(persistenceService).addPhoto(
        eq(1001L), eq("site.jpg"), eq("A description"), eq("2026-05-01"), eq(42L), eq("image/jpeg"),
        eq(content), eq("IDIR\\tester"));
  }

  @Test
  void photoOperationsAreAllowedOnACheckedOutChecklistWithTheMatchingToken() {
    // RDO must be editable or offline check-in cannot work: photos are flushed BEFORE the document
    // save, and the RDO → ACT flip happens inside that save — so the checklist is still checked out
    // when the flush runs. An ACT-only guard failed every offline photo upload.
    UUID guid = UUID.fromString("11111111-2222-3333-4444-555555555555");
    when(checklistRepository.getChecklistStatus(1001L))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.RDO);
    when(checklistRepository.getDeviceCheckoutGuid(1001L)).thenReturn(guid);
    when(loggedUserHelper.getLoggedUserId()).thenReturn("IDIR\\tester");
    MockMultipartFile photo =
        new MockMultipartFile("file", "site.jpg", "image/jpeg", new byte[] {1, 2, 3});

    assertDoesNotThrow(
        () -> service.addPhoto(1001L, photo, "A description", null, null, guid.toString()));
    assertDoesNotThrow(() -> service.deletePhoto(1001L, 7L, guid.toString()));
  }

  @Test
  void photoOperationsAreRefusedOnACheckedOutChecklistFromAnotherDevice() {
    // Allowing RDO must not mean "anyone may edit a checked-out checklist" — only the device holding
    // the checkout. Same rule releaseCheckout and uploadChecklist apply.
    when(checklistRepository.getChecklistStatus(1001L))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.RDO);
    when(checklistRepository.getDeviceCheckoutGuid(1001L))
        .thenReturn(UUID.fromString("11111111-2222-3333-4444-555555555555"));
    MockMultipartFile photo =
        new MockMultipartFile("file", "site.jpg", "image/jpeg", new byte[] {1, 2, 3});

    InvalidParameterException wrongToken = assertThrows(InvalidParameterException.class,
        () -> service.addPhoto(1001L, photo, "A description", null, null, "not-my-checkout"));
    assertTrue(wrongToken.getMessage().contains("checked out on another device"));

    // A caller with no token at all is refused the same way.
    assertThrows(InvalidParameterException.class,
        () -> service.deletePhoto(1001L, 7L, null));
    verifyNoInteractions(persistenceService);
  }

  @Test
  void photoOperationsAreRefusedOnASubmittedChecklist() {
    // The photo endpoints are token-neutral leaves, so they bypass the section-save status gate —
    // the check has to be applied explicitly or a submitted checklist stays editable.
    when(checklistRepository.getChecklistStatus(1001L))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.SUB);
    MockMultipartFile photo =
        new MockMultipartFile("file", "site.jpg", "image/jpeg", new byte[] {1, 2, 3});

    assertThrows(InvalidParameterException.class,
        () -> service.addPhoto(1001L, photo, "A description", null, null, null));
    assertThrows(InvalidParameterException.class, () -> service.deletePhoto(1001L, 7L, null));
    verifyNoInteractions(persistenceService);
  }

  @Test
  void submitValidatesThePhotosOnTheRecordNotTheOnesInThePayload() {
    // Photos are independent resources now, so a caller could submit with pictures omitted (or
    // fabricated) and skip the per-photo checks. Submit must read them from the record.
    Picture onTheRecord = new Picture();
    onTheRecord.setId("77");
    onTheRecord.setDescription("");             // blank — must reach the validator
    when(persistenceService.getPhotoMetadata(1001L)).thenReturn(List.of(onTheRecord));
    when(checklistRepository.getChecklistStatus(1001L))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.ACT);
    when(submitValidationService.validateBeforeSubmit(any())).thenReturn(List.of());

    ChrChecklist entity = new ChrChecklist();
    entity.setChrChecklistId(1001L);
    when(persistenceService.getAcceptedSiteForChr(1001L)).thenReturn(entity);

    CheckList payload = new CheckList();
    payload.setChecklistID("1001");
    payload.setStatus(ChrConstants.FrepChecklistStatusCode.ACT);
    payload.setRevisionCount("1");
    payload.setPictures(new ArrayList<>());     // client claims there are no photos

    assertThrows(FrepApiRuntimeException.class, () -> service.submitChecklist(1001L, payload));

    ArgumentCaptor<CheckList> validated = ArgumentCaptor.forClass(CheckList.class);
    verify(submitValidationService).validateBeforeSubmit(validated.capture());
    assertEquals(1, validated.getValue().getPictures().size(),
        "the record's photos must be validated, not the payload's");
    assertEquals("77", validated.getValue().getPictures().get(0).getId());
  }

  @Test
  void submitDoesNotDeletePhotos() {
    // Submit funnels through the same full-document save as an ordinary save and an offline check-in,
    // and used to reconcile the whole picture set on the way through. Photos must survive it.
    when(checklistRepository.getChecklistStatus(1001L))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.ACT);
    when(submitValidationService.validateBeforeSubmit(any())).thenReturn(List.of());
    when(persistenceService.getPhotoMetadata(1001L)).thenReturn(List.of());
    ChrChecklist entity = new ChrChecklist();
    entity.setChrChecklistId(1001L);
    when(persistenceService.getAcceptedSiteForChr(1001L)).thenReturn(entity);

    CheckList payload = new CheckList();
    payload.setChecklistID("1001");
    payload.setStatus(ChrConstants.FrepChecklistStatusCode.ACT);
    payload.setRevisionCount("1");

    assertThrows(FrepApiRuntimeException.class, () -> service.submitChecklist(1001L, payload));

    // Object storage is reached only by the photo endpoints; a submit touching it is the bug.
    verifyNoInteractions(objectStorageService);
  }

  @Test
  void theOfflineCheckInSequenceIsAcceptedServerSide() {
    // The server half of the check-in seam: photos are flushed while the checklist is still RDO,
    // because the RDO -> ACT flip happens inside the document save that follows. An ACT-only guard
    // rejected every one of these calls.
    UUID guid = UUID.fromString("11111111-2222-3333-4444-555555555555");
    when(checklistRepository.getChecklistStatus(1001L))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.RDO);
    when(checklistRepository.getDeviceCheckoutGuid(1001L)).thenReturn(guid);
    when(loggedUserHelper.getLoggedUserId()).thenReturn("IDIR\\tester");
    MockMultipartFile photo =
        new MockMultipartFile("file", "site.jpg", "image/jpeg", new byte[] {1, 2, 3});

    // 1. delete a photo removed offline, 2. upload one captured offline — both still RDO
    assertDoesNotThrow(() -> service.deletePhoto(1001L, 7L, guid.toString()));
    assertDoesNotThrow(() -> service.addPhoto(1001L, photo, "Captured offline", null, null, guid.toString()));

    // 3. the document save then performs the RDO -> ACT check-in
    CheckList payload = new CheckList();
    payload.setChecklistID("1001");
    payload.setStatus(ChrConstants.FrepChecklistStatusCode.RDO);
    payload.setDeviceCheckoutGuid(guid.toString());

    service.saveChecklist(payload);

    verify(persistenceService).uploadChecklist(payload, "IDIR\\tester");
  }

  // Authorization is enforced entirely by @PreAuthorize on ChrChecklistApiEndpoint: reads via the
  // coarse CHR_EDIT, writes via the per-district @chrAuth.canEditChecklist(...) — see
  // ApiAuthorizationSecurityTest / ChrChecklistAuthorizerTest.
}

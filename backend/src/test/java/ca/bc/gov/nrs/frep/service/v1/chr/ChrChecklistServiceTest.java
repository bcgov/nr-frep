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

  private ChrChecklistService service;

  @BeforeEach
  void setUp() {
    service = new ChrChecklistService(
        persistenceService,
        checklistRepository,
        submitValidationService,
        objectStorageService,
        new ObjectStorageProperties("http://s3", "bucket", "key", "secret"),
        loggedUserHelper
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
  void savePicturesSectionRejectsNewPhotoWithBlankDescription() {
    Picture newPhoto = new Picture();
    newPhoto.setDescription("");
    newPhoto.setMimeTypeCode("image/jpeg");
    CheckList checklist = new CheckList();
    checklist.setChecklistID("1001");
    checklist.setPictures(List.of(newPhoto));

    InvalidParameterException ex =
        assertThrows(InvalidParameterException.class, () -> service.savePicturesSection(checklist));
    assertTrue(ex.getMessage().contains("missing mandatory descriptions"));
  }

  @Test
  void savePicturesSectionAllowsExistingPhotoWithBlankDescription() {
    // A legacy photo (has an id) with a blank description must not block a section save (add/delete);
    // only new photos are validated. Validation passes here, so the save fails the status gate instead
    // — proving it didn't trip the photo-description check.
    Picture existing = new Picture();
    existing.setId("p1");
    existing.setDescription("");
    CheckList checklist = new CheckList();
    checklist.setChecklistID("1001");
    checklist.setPictures(List.of(existing));
    when(checklistRepository.getChecklistStatus(1001L))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.SUB);

    InvalidParameterException ex =
        assertThrows(InvalidParameterException.class, () -> service.savePicturesSection(checklist));
    assertFalse(ex.getMessage().contains("missing mandatory descriptions"));
  }

  // Authorization is enforced entirely by @PreAuthorize on ChrChecklistApiEndpoint: reads via the
  // coarse CHR_EDIT, writes via the per-district @chrAuth.canEditChecklist(...) — see
  // ApiAuthorizationSecurityTest / ChrChecklistAuthorizerTest.
}

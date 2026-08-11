package ca.bc.gov.nrs.frep.service.v1.frep;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.ChrConstants;
import ca.bc.gov.nrs.frep.exception.AccessForbiddenException;
import ca.bc.gov.nrs.frep.exception.ConflictFoundException;
import ca.bc.gov.nrs.frep.exception.InvalidParameterException;
import ca.bc.gov.nrs.frep.exception.InvalidPayloadException;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioCheckout;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioSnapshot;
import ca.bc.gov.nrs.frep.struct.v1.frep.RiparianNotes;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioSnapshotUpload;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratum;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlot;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStandRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlotRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratumRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BiodiversityOpening;
import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistHeaderData;
import ca.bc.gov.nrs.frep.repository.v1.ChecklistRepository;
import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistSectionData;
import ca.bc.gov.nrs.frep.repository.v1.CodeListRepository;
import ca.bc.gov.nrs.frep.repository.v1.ProtocolChecklistWriteRepository;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import ca.bc.gov.nrs.frep.service.v1.VirusScanner;
import ca.bc.gov.nrs.frep.service.v1.frep.ProtocolChecklistService.ProtocolSubmitValidationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProtocolChecklistServiceTest {

  @Mock
  private ChecklistRepository checklistRepository;

  @Mock
  private CodeListRepository codeListRepository;

  @Mock
  private ProtocolChecklistWriteRepository writeRepository;

  @Mock
  private LoggedUserHelper loggedUserHelper;

  @Mock
  private FamUserDirectoryService famUserDirectoryService;

  private static final java.util.UUID CHECKOUT_TOKEN =
      java.util.UUID.fromString("11111111-2222-3333-4444-555555555555");

  @Mock
  private VirusScanner virusScanner;

  // The attachment list fills each row's real size with a HEAD per row against object storage
  // (PR 3a) — so any test that returns a non-empty attachment page needs this wired.
  @Mock
  private ca.bc.gov.nrs.frep.service.v1.ObjectStorageService objectStorage;

  @InjectMocks
  private ProtocolChecklistService service;

  @BeforeEach
  void stubRecordType() {
    // Type is resolved from the record (not the URL). New/editable biodiversity records are SLR;
    // historical SLB records are view-only (mutations 403). Default the editable path to SLR; the
    // few view-only/read tests override this stub to SLB explicitly.
    lenient().when(checklistRepository.resolveResourceType(anyString())).thenReturn("SLR");
    // Writes now also consult status. Default to ACT so the existing tests exercise the realistic
    // editable path; the status tests below override it.
    lenient().when(checklistRepository.getBioChecklistStatus(anyString()))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.ACT);
  }

  // ── Status enforcement (BE-1) ────────────────────────────────────────
  //
  // Until now nothing on the Biodiversity write path read status at all: assertEditable rejected only
  // legacy SLB records, so a SUB or RDO SLR checklist was still writable through the API and the UI
  // was the sole gate. Verified against DEV on 2026-08-07 — the notes proc accepted a write to a
  // record the app treats as read-only.

  private BioStratum aStratum() {
    return stratum("A1", "CC", "Y", "3", "2.5", "HNR", "CWH", "ds", null);
  }

  // ── Checkout state read (Track A addendum) ───────────────────────────

  @Test
  void checkoutStateSaysHeldForTheDeviceThatHoldsIt() {
    givenCheckedOutToThisDevice();

    var state = service.getCheckoutState("9001", CHECKOUT_TOKEN.toString());

    assertTrue(state.heldByThisDevice());
    assertEquals("RDO", state.statusCode());
  }

  @Test
  void checkoutStateSaysNotHeldForAnotherDevice() {
    // The reclaimed case the offline list needs to warn about before a check-in is attempted.
    givenCheckedOutToThisDevice();

    assertFalse(service.getCheckoutState("9001", "not-my-checkout").heldByThisDevice());
  }

  @Test
  void checkoutStateSaysNotHeldWhenTheChecklistIsNotCheckedOut() {
    when(checklistRepository.getBioChecklistStatus("9001"))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.ACT);

    var state = service.getCheckoutState("9001", CHECKOUT_TOKEN.toString());

    assertFalse(state.heldByThisDevice());
    assertEquals("ACT", state.statusCode());
    // Not checked out ⇒ no reason to read the token at all.
    verify(checklistRepository, never()).getBioDeviceCheckoutGuid(any());
  }

  // ── Snapshot check-in (BE-5) ─────────────────────────────────────────
  //
  // Each of these pins a step the week-1 spike verified against DEV. They are ordering guarantees,
  // so they assert with InOrder rather than just "was called".

  private BioSnapshotUpload anUpload(List<BioSnapshotUpload.BioStratumUpload> strata,
      List<BioSnapshotUpload.Tombstone> tombstones) {
    return new BioSnapshotUpload(
        BioSnapshot.CURRENT_SCHEMA_VERSION, CHECKOUT_TOKEN.toString(),
        opening(null, "loc", "N", null, "N", null, null),
        new RiparianNotes("9001", "field notes", "3"),
        strata, tombstones);
  }

  private void givenCheckedOutToThisDevice() {
    when(checklistRepository.getBioChecklistStatus("9001"))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.RDO);
    when(checklistRepository.getBioDeviceCheckoutGuid("9001")).thenReturn(CHECKOUT_TOKEN);
  }

  @Test
  void checkInThreadsTheOpeningsTokenIntoTheNotesSave() {
    // The opening and notes write the SAME BIODIVERSITY_CHECKLIST row and share one revision_count.
    // Sending the device's stale token on the notes save fails record.modified2 every time.
    givenCheckedOutToThisDevice();
    when(writeRepository.saveBiodiversityOpening(any(), any()))
        .thenReturn(opening(null, "loc", "N", null, "N", null, null).withIdentity("9001", "4"));
    when(writeRepository.activate(eq("9001"), any())).thenReturn("");

    service.uploadSnapshot("9001", anUpload(List.of(), List.of()));

    ArgumentCaptor<RiparianNotes> notes = ArgumentCaptor.forClass(RiparianNotes.class);
    verify(writeRepository).saveNotes(notes.capture(), any(), any());
    assertEquals("4", notes.getValue().revisionCount(),
        "notes must use the token the opening returned, not the device's stale '3'");
  }

  @Test
  void checkInAssignsRealIdsAndRepointsPlotsAtThem() {
    givenCheckedOutToThisDevice();
    when(writeRepository.saveBiodiversityOpening(any(), any()))
        .thenReturn(opening(null, "loc", "N", null, "N", null, null).withIdentity("9001", "4"));
    when(writeRepository.saveBioStratum(any(), any())).thenReturn(
        stratum("A1", "CC", "Y", "3", "2.5", "HNR", "CWH", "ds", null).withIdentity("5001", "1"));
    when(writeRepository.activate(eq("9001"), any())).thenReturn("");

    BioPlot offlinePlot = plot("someone", null, null, "1", "N", List.of())
        .withIdentity("tmp:plot-1", "tmp:stratum-1", null);
    BioStratum offlineStratum =
        stratum("A1", "CC", "Y", "3", "2.5", "HNR", "CWH", "ds", null)
            .withIdentity("tmp:stratum-1", null);

    service.uploadSnapshot("9001", anUpload(
        List.of(new BioSnapshotUpload.BioStratumUpload(offlineStratum, List.of(offlinePlot))),
        List.of()));

    ArgumentCaptor<BioStratum> savedStratum = ArgumentCaptor.forClass(BioStratum.class);
    verify(writeRepository).saveBioStratum(savedStratum.capture(), any());
    assertEquals(null, savedStratum.getValue().stratumId(), "a tmp: id must be cleared for insert");

    ArgumentCaptor<BioPlot> savedPlot = ArgumentCaptor.forClass(BioPlot.class);
    verify(writeRepository).saveBioPlot(savedPlot.capture(), any());
    assertEquals("5001", savedPlot.getValue().stratumId(),
        "the plot must point at the stratum's real id, not the tmp: one it was captured against");
    assertEquals(null, savedPlot.getValue().plotId());
  }

  @Test
  void checkInDeletesPlotsBeforeTheirStratum() {
    // The stratum delete REFUSES while any plot references it — it does not cascade. Wrong order
    // aborts the whole sync on an error that reads like a data problem.
    givenCheckedOutToThisDevice();
    when(writeRepository.saveBiodiversityOpening(any(), any()))
        .thenReturn(opening(null, "loc", "N", null, "N", null, null).withIdentity("9001", "4"));
    when(writeRepository.deleteBioPlot(any(), any())).thenReturn("");
    when(writeRepository.deleteBioStratum(any(), any())).thenReturn("");
    when(writeRepository.activate(eq("9001"), any())).thenReturn("");

    service.uploadSnapshot("9001", anUpload(List.of(), List.of(
        new BioSnapshotUpload.Tombstone("STRATUM", "5001", "1"),
        new BioSnapshotUpload.Tombstone("PLOT", "7001", "1"))));

    // Listed stratum-first in the payload; must still execute plot-first.
    InOrder order = inOrder(writeRepository);
    order.verify(writeRepository).deleteBioPlot("7001", "1");
    order.verify(writeRepository).deleteBioStratum("5001", "1");
  }

  @Test
  void checkInSavesBeforeItDeletesAndReleasesTheCheckoutLast() {
    givenCheckedOutToThisDevice();
    when(writeRepository.saveBiodiversityOpening(any(), any()))
        .thenReturn(opening(null, "loc", "N", null, "N", null, null).withIdentity("9001", "4"));
    when(writeRepository.deleteBioPlot(any(), any())).thenReturn("");
    when(writeRepository.activate(eq("9001"), any())).thenReturn("");

    service.uploadSnapshot("9001", anUpload(List.of(),
        List.of(new BioSnapshotUpload.Tombstone("PLOT", "7001", "1"))));

    // Deletes after saves, so an id assigned in this sync can't be removed by a stale reference; and
    // the checkout is released only once everything else has landed.
    InOrder order = inOrder(writeRepository);
    order.verify(writeRepository).saveBiodiversityOpening(any(), any());
    order.verify(writeRepository).deleteBioPlot("7001", "1");
    order.verify(writeRepository).activate(eq("9001"), any());
  }

  @Test
  void aFailedDeleteAbortsTheCheckIn() {
    // The delete procs report failure as a message, not an exception. Ignoring it would silently drop
    // the deletion and the row would reappear on the device — what tombstones exist to prevent.
    givenCheckedOutToThisDevice();
    when(writeRepository.saveBiodiversityOpening(any(), any()))
        .thenReturn(opening(null, "loc", "N", null, "N", null, null).withIdentity("9001", "4"));
    when(writeRepository.deleteBioStratum(any(), any()))
        .thenReturn("frep.error.usr.childexists:STRATUM_ID;");

    assertThrows(ConflictFoundException.class, () -> service.uploadSnapshot("9001",
        anUpload(List.of(), List.of(new BioSnapshotUpload.Tombstone("STRATUM", "5001", "1")))));

    verify(writeRepository, never()).activate(any(), any());
  }

  @Test
  void checkInFromAnotherDeviceIsRefused() {
    when(checklistRepository.getBioChecklistStatus("9001"))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.RDO);
    when(checklistRepository.getBioDeviceCheckoutGuid("9001"))
        .thenReturn(java.util.UUID.fromString("99999999-9999-9999-9999-999999999999"));

    assertThrows(ResponseStatusException.class,
        () -> service.uploadSnapshot("9001", anUpload(List.of(), List.of())));
    verify(writeRepository, never()).saveBiodiversityOpening(any(), any());
  }

  @Test
  void anUnreadableSnapshotVersionIsBlockedNotMigrated() {
    // Never silently reinterpret an older graph — that is how a sync corrupts rather than fails.
    BioSnapshotUpload old = new BioSnapshotUpload(
        "0", CHECKOUT_TOKEN.toString(), opening(null, "loc", "N", null, "N", null, null),
        null, List.of(), List.of());

    assertThrows(InvalidParameterException.class, () -> service.uploadSnapshot("9001", old));
    verify(writeRepository, never()).saveBiodiversityOpening(any(), any());
  }

  // ── Offline snapshot (BE-4) ──────────────────────────────────────────

  @Test
  void theSnapshotDoesNotClaimTheCheckout() {
    // Reads first, checkout last (decision 18): an abandoned download must leave the checklist
    // editable online rather than stranded in RDO with no local copy to show for it.
    when(writeRepository.getBiodiversityOpening("9001"))
        .thenReturn(opening(null, "loc", "N", null, "N", null, null));
    when(writeRepository.listBioStrata("9001")).thenReturn(List.of());
    when(writeRepository.getAttachments(eq("9001"), any(), anyInt(), anyInt()))
        .thenReturn(List.of());

    service.getSnapshot("9001");

    verify(writeRepository, never()).takeOffline(any(), any(), any());
  }

  @Test
  void theSnapshotIsRefusedForANonSlrRecord() {
    when(checklistRepository.resolveResourceType("9001")).thenReturn("SLB");

    assertThrows(ResponseStatusException.class, () -> service.getSnapshot("9001"));
    verify(writeRepository, never()).listBioStrata(any());
  }

  @Test
  void theSnapshotCarriesEveryAttachmentNotJustTheFirstPage() {
    // The list read is paginated for the online tab, but an offline copy needs the complete set —
    // page 1 would silently truncate both what the device can see and what it can flush back.
    when(writeRepository.getBiodiversityOpening("9001"))
        .thenReturn(opening(null, "loc", "N", null, "N", null, null));
    when(writeRepository.listBioStrata("9001")).thenReturn(List.of());
    List<AttachmentRow> firstPage = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      firstPage.add(new AttachmentRow(String.valueOf(i), "f" + i + ".pdf", "d", "PDF", "1.00"));
    }
    when(writeRepository.getAttachments(eq("9001"), any(), eq(0), anyInt())).thenReturn(firstPage);
    when(writeRepository.getAttachments(eq("9001"), any(), eq(1), anyInt()))
        .thenReturn(List.of(new AttachmentRow("50", "f50.pdf", "d", "PDF", "1.00")));

    BioSnapshot snapshot = service.getSnapshot("9001");

    assertEquals(51, snapshot.attachments().size());
  }

  @Test
  void theSnapshotIsNotTruncatedByAWrongTotalCount() {
    // countAttachments is a separate query from the page read. If the snapshot terminated on it, a
    // disagreement between the two would silently drop attachments the device can then neither see
    // nor flush back. Here the count says 0 while two full pages exist.
    when(checklistRepository.resolveResourceType("9001")).thenReturn("SLR");
    when(writeRepository.getBiodiversityOpening("9001"))
        .thenReturn(opening(null, "loc", "N", null, "N", null, null));
    when(writeRepository.listBioStrata("9001")).thenReturn(List.of());
    when(writeRepository.countAttachments(any(), any())).thenReturn(0);
    List<AttachmentRow> fullPage = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      fullPage.add(new AttachmentRow(String.valueOf(i), "f" + i + ".pdf", "d", "PDF", "1.00"));
    }
    when(writeRepository.getAttachments(eq("9001"), any(), eq(0), anyInt())).thenReturn(fullPage);
    when(writeRepository.getAttachments(eq("9001"), any(), eq(1), anyInt()))
        .thenReturn(List.of(new AttachmentRow("50", "f50.pdf", "d", "PDF", "1.00")));

    assertEquals(51, service.getSnapshot("9001").attachments().size());
  }

  @Test
  void theSnapshotStopsOnAShortPageRatherThanSpinning() {
    // Defensive: an empty first page must end the walk immediately.
    when(writeRepository.getBiodiversityOpening("9001"))
        .thenReturn(opening(null, "loc", "N", null, "N", null, null));
    when(writeRepository.listBioStrata("9001")).thenReturn(List.of());
    when(writeRepository.getAttachments(eq("9001"), any(), anyInt(), anyInt()))
        .thenReturn(List.of());

    BioSnapshot snapshot = service.getSnapshot("9001");

    assertEquals(0, snapshot.attachments().size());
    assertEquals(BioSnapshot.CURRENT_SCHEMA_VERSION, snapshot.schemaVersion());
  }

  @Test
  void theSnapshotNestsPlotsUnderTheirStratum() {
    when(writeRepository.getBiodiversityOpening("9001"))
        .thenReturn(opening(null, "loc", "N", null, "N", null, null));
    when(writeRepository.listBioStrata("9001")).thenReturn(
        List.of(new BioStratumRow("5001", "A1", "CC", null, "1", "2.5", "1")));
    when(writeRepository.getBioStratum("5001")).thenReturn(
        stratum("A1", "CC", "Y", "3", "2.5", "HNR", "CWH", "ds", null).withIdentity("5001", "1"));
    when(writeRepository.listBioPlots("5001")).thenReturn(
        List.of(new BioPlotRow("7001", "P1", "someone", null, "1")));
    when(writeRepository.getBioPlot("7001"))
        .thenReturn(plot("someone", null, null, "1", "N", List.of()));
    when(writeRepository.getAttachments(eq("9001"), any(), anyInt(), anyInt()))
        .thenReturn(List.of());

    BioSnapshot snapshot = service.getSnapshot("9001");

    assertEquals(1, snapshot.strata().size());
    assertEquals("5001", snapshot.strata().get(0).stratum().stratumId());
    assertEquals(1, snapshot.strata().get(0).plots().size());
  }

  // ── Attachment leaf guard (BE-3) ─────────────────────────────────────
  //
  // saveAttachment previously had no editability guard at all — not even the SLB exclusion — so an
  // attachment could be added to a historical or submitted checklist. Both leaves now run the same
  // three-way check as the section saves, and as CHR's photo endpoints.

  private static MockMultipartFile anAttachment() {
    return new MockMultipartFile("file", "map.pdf", "application/pdf", new byte[] {1, 2, 3});
  }

  @Test
  void uploadingAnAttachmentToASubmittedChecklistIsForbidden() {
    when(checklistRepository.getBioChecklistStatus("9001"))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.SUB);

    assertThrows(ResponseStatusException.class,
        () -> service.saveAttachment("bio", "9001", anAttachment(), "a map", null));
    verifyNoInteractions(virusScanner);
  }

  @Test
  void uploadingAnAttachmentWhileCheckedOutNeedsTheDevicesToken() {
    when(checklistRepository.getBioChecklistStatus("9001"))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.RDO);
    when(checklistRepository.getBioDeviceCheckoutGuid("9001"))
        .thenReturn(java.util.UUID.fromString("11111111-2222-3333-4444-555555555555"));

    assertThrows(ResponseStatusException.class,
        () -> service.saveAttachment("bio", "9001", anAttachment(), "a map", "not-my-checkout"));
  }

  @Test
  void theOfflineFlushCanUploadWhileCheckedOutWithItsOwnToken() {
    // RDO must be allowed for the holder, or offline check-in cannot work: attachments are flushed
    // before the snapshot lands, and the RDO -> ACT flip happens at the end of that sync.
    java.util.UUID token = java.util.UUID.fromString("11111111-2222-3333-4444-555555555555");
    when(checklistRepository.getBioChecklistStatus("9001"))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.RDO);
    when(checklistRepository.getBioDeviceCheckoutGuid("9001")).thenReturn(token);

    assertDoesNotThrow(() -> service.saveAttachment(
        "bio", "9001", anAttachment(), "a map", token.toString()));
    verify(writeRepository).saveAttachment(
        eq("9001"), any(), any(), any(), any(), any(), any());
  }

  @Test
  void deletingAnAttachmentOnASubmittedChecklistIsForbidden() {
    when(checklistRepository.getBioChecklistStatus("9001"))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.SUB);

    assertThrows(ResponseStatusException.class,
        () -> service.deleteAttachment("bio", "9001", "77", null));
    verify(writeRepository, never()).deleteAttachment(any(), any(), any());
  }

  // ── Offline checkout (BE-2) ──────────────────────────────────────────

  @Test
  void takeOfflineIssuesAServerMintedToken() {
    when(writeRepository.takeOffline(eq("9001"), any(), any())).thenReturn("");

    BioCheckout result = service.takeOffline("9001");

    assertEquals("RDO", result.statusCode());
    // The token must be server-issued, and must be the one actually written.
    ArgumentCaptor<java.util.UUID> written = ArgumentCaptor.forClass(java.util.UUID.class);
    verify(writeRepository).takeOffline(eq("9001"), written.capture(), any());
    assertEquals(written.getValue().toString(), result.deviceCheckoutGuid());
  }

  @Test
  void takeOfflineIsRefusedForAHistoricalSlbRecord() {
    // Checking out an SLB record would hand a device a copy it could never sync back.
    when(checklistRepository.resolveResourceType("9001")).thenReturn("SLB");

    // Refused by the pre-existing SLB guard, which throws ResponseStatusException rather than
    // AccessForbiddenException — different type, same 403, and it keeps the more specific
    // "historical record" wording instead of the generic SLR-only one.
    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class, () -> service.takeOffline("9001"));
    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    assertTrue(ex.getReason().contains("historical"));
    verify(writeRepository, never()).takeOffline(any(), any(), any());
  }

  @Test
  void takeOfflineIsRefusedWhenTheProcSaysTheStatusIsWrong() {
    // The ACT guard lives in the proc's WHERE clause, so "already checked out" surfaces as an error
    // string rather than a thrown exception — it still has to become a refusal, not a silent success.
    when(writeRepository.takeOffline(eq("9001"), any(), any()))
        .thenReturn("frep.error.usr.checkout.unavailable;");

    assertThrows(AccessForbiddenException.class, () -> service.takeOffline("9001"));
  }

  @Test
  void releaseRequiresTheCallersOwnToken() {
    when(checklistRepository.getBioChecklistStatus("9001")).thenReturn("RDO");
    when(checklistRepository.getBioDeviceCheckoutGuid("9001"))
        .thenReturn(java.util.UUID.fromString("11111111-2222-3333-4444-555555555555"));

    assertThrows(AccessForbiddenException.class,
        () -> service.releaseCheckout("9001", "not-my-checkout"));
    verify(writeRepository, never()).activate(any(), any());
  }

  @Test
  void releaseWithTheMatchingTokenActivates() {
    java.util.UUID token = java.util.UUID.fromString("11111111-2222-3333-4444-555555555555");
    when(checklistRepository.getBioChecklistStatus("9001")).thenReturn("RDO");
    when(checklistRepository.getBioDeviceCheckoutGuid("9001")).thenReturn(token);
    when(writeRepository.activate(eq("9001"), any())).thenReturn("");

    BioCheckout result = service.releaseCheckout("9001", token.toString());

    assertEquals("ACT", result.statusCode());
    assertEquals(null, result.deviceCheckoutGuid());
    verify(writeRepository).activate(eq("9001"), any());
  }

  @Test
  void releasingAChecklistThatIsNotCheckedOutIsANoOp() {
    // Idempotent, matching CHR: the common cause is a device discarding a copy the server already
    // reclaimed, and failing that would leave the user stuck with an undismissable local copy.
    when(checklistRepository.getBioChecklistStatus("9001")).thenReturn("ACT");

    BioCheckout result = service.releaseCheckout("9001", "anything");

    assertEquals("ACT", result.statusCode());
    verify(writeRepository, never()).activate(any(), any());
  }

  @Test
  void adminActivateNeedsNoToken() {
    // The stranded-device fallback: no token is available by definition.
    when(writeRepository.activate(eq("9001"), any())).thenReturn("");

    BioCheckout result = service.activate("9001");

    assertEquals("ACT", result.statusCode());
    assertEquals(null, result.deviceCheckoutGuid());
    verify(checklistRepository, never()).getBioDeviceCheckoutGuid(any());
  }

  @Test
  void activateIsRefusedWhenNothingIsCheckedOut() {
    when(writeRepository.activate(eq("9001"), any()))
        .thenReturn("frep.error.usr.checkout.notheld;");

    assertThrows(AccessForbiddenException.class, () -> service.activate("9001"));
  }

  @Test
  void saveOnASubmittedChecklistIsForbidden() {
    when(checklistRepository.getBioChecklistStatus("9001"))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.SUB);

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class, () -> service.saveBioStratum(aStratum()));

    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    assertTrue(ex.getReason().contains("submitted"));
    verify(writeRepository, never()).saveBioStratum(any(), any());
  }

  @Test
  void onlineSaveOnACheckedOutChecklistIsForbidden() {
    // The per-section endpoints send no checkout token, so RDO must refuse them — that is what makes
    // a checked-out checklist read-only online while the device still holds it.
    when(checklistRepository.getBioChecklistStatus("9001"))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.RDO);
    when(checklistRepository.getBioDeviceCheckoutGuid("9001"))
        .thenReturn(java.util.UUID.fromString("11111111-2222-3333-4444-555555555555"));

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class, () -> service.saveBioStratum(aStratum()));

    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    assertTrue(ex.getReason().contains("checked out"));
    verify(writeRepository, never()).saveBioStratum(any(), any());
  }

  @Test
  void saveOnAnActiveChecklistIsAllowed() {
    // Control: proves the refusals above are the status check firing, not the guard blocking
    // everything. Without this, a bug that refused every write would still pass the two tests above.
    when(checklistRepository.getBioChecklistStatus("9001"))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.ACT);

    assertDoesNotThrow(() -> service.saveBioStratum(aStratum()));

    verify(writeRepository).saveBioStratum(any(), any());
  }

  @Test
  void saveOnAMissingChecklistDefersToTheProcNotFound() {
    // A null status means no such row — nothing writes a null status, so this is the not-found path.
    // It must stay permissive: turning it into a 403 would report a bogus id as "read-only".
    when(checklistRepository.getBioChecklistStatus("9001")).thenReturn(null);

    assertDoesNotThrow(() -> service.saveBioStratum(aStratum()));

    verify(writeRepository).saveBioStratum(any(), any());
  }

  @Test
  void theSlbExclusionIsCheckedBeforeStatus() {
    // A historical SLB record must be refused as SLB regardless of its status — the message the user
    // sees should say "historical", not "submitted".
    when(checklistRepository.resolveResourceType("9001")).thenReturn("SLB");
    lenient().when(checklistRepository.getBioChecklistStatus("9001"))
        .thenReturn(ChrConstants.FrepChecklistStatusCode.SUB);

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class, () -> service.saveBioStratum(aStratum()));

    assertTrue(ex.getReason().contains("historical"));
  }

  @Test
  void inferFieldKindDetectsCommonRenderHints() {
    assertEquals("YES_NO", ProtocolChecklistService.inferFieldKind("Cut block harvested?", "Y"));
    assertEquals("DATE", ProtocolChecklistService.inferFieldKind("Harvest complete date", "2024-06-15"));
    assertEquals("NUMBER", ProtocolChecklistService.inferFieldKind("Stand age (yrs)", "82"));
    assertEquals("MULTILINE", ProtocolChecklistService.inferFieldKind("Field crew comments", "Looks good"));
    assertEquals("TEXT", ProtocolChecklistService.inferFieldKind("Stream class", "S4"));
  }

  @Test
  void toSectionMapsRepositoryFields() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("Stand age (yrs)", "82");
    var section = ProtocolChecklistService.toSection(
        "opening",
        "Opening info",
        ChecklistSectionData.fieldsOnly(fields)
    );

    assertEquals("opening", section.id());
    assertEquals("Opening info", section.title());
    assertEquals(1, section.fields().size());
    assertEquals("NUMBER", section.fields().get(0).kind());
  }

  @Test
  void mergeHeadersPrefersFirstNonBlankValues() {
    var merged = ProtocolChecklistService.mergeHeaders(List.of(
        ChecklistSectionData.of(
            new ChecklistHeaderData("", "A12345", "2024", "RDY", "", ""),
            Map.of()
        ),
        ChecklistSectionData.of(
            new ChecklistHeaderData("1001", "", "", "", "IDIR\\JDOE", "2024-08-12"),
            Map.of()
        )
    ));

    assertEquals("1001", merged.frepSelectedSiteId());
    assertEquals("A12345", merged.openingNumber());
    assertEquals("2024", merged.effectiveYear());
    assertEquals("RDY", merged.statusCode());
    assertEquals("IDIR\\JDOE", merged.evaluatorUserid());
    assertEquals("2024-08-12", merged.evaluationDate());
  }

  @Test
  void findChecklistBuildsBioResponseFromRepositorySections() {
    when(checklistRepository.resolveResourceType("9001")).thenReturn("SLB"); // historical view-only record
    Map<String, Object> slb = new LinkedHashMap<>();
    slb.put("CODE", "SLB");
    slb.put("DESCRIPTION", "Biodiversity");
    when(codeListRepository.getResourceValue()).thenReturn(List.of(slb));

    when(checklistRepository.getBioOpening("9001")).thenReturn(sectionWithHeader(
        new ChecklistHeaderData("", "A12345", "2024", "RDY", "IDIR\\JDOE", "2024-08-12"),
        Map.of("Stand age (yrs)", "82")
    ));
    when(checklistRepository.getBioStratum("9001")).thenReturn(ChecklistSectionData.fieldsOnly(Map.of()));
    when(checklistRepository.getBioPlots("9001")).thenReturn(ChecklistSectionData.fieldsOnly(Map.of()));

    var response = service.findChecklist("bio", "9001");

    assertTrue(response.isPresent());
    assertEquals("9001", response.get().checklistId());
    assertEquals("SLB", response.get().protocolType());
    assertEquals("Biodiversity", response.get().protocolName());
    assertEquals("RDY", response.get().statusCode());
    assertEquals("RDY", response.get().statusLabel());
    // Opening info leads now that the Administration tab is retired.
    assertEquals("opening", response.get().sections().get(0).id());
  }

  @Test
  void findChecklistDegradesSectionWithNoData() {
    when(checklistRepository.resolveResourceType("9001")).thenReturn("SLB"); // historical view-only record
    Map<String, Object> slb = new LinkedHashMap<>();
    slb.put("CODE", "SLB");
    slb.put("DESCRIPTION", "Biodiversity");
    when(codeListRepository.getResourceValue()).thenReturn(List.of(slb));

    when(checklistRepository.getBioOpening("9001")).thenReturn(sectionWithHeader(
        new ChecklistHeaderData("", "A12345", "2024", "RDY", "IDIR\\JDOE", "2024-08-12"),
        Map.of("Stand age (yrs)", "82")
    ));
    // FREP_211 raises ORA-01403 (no data found) when the stratum/resource ids can't be resolved.
    when(checklistRepository.getBioStratum("9001")).thenThrow(
        new org.springframework.dao.DataIntegrityViolationException(
            "no data", new java.sql.SQLException("ORA-01403: no data found", "99999", 1403)));
    when(checklistRepository.getBioPlots("9001")).thenReturn(ChecklistSectionData.fieldsOnly(Map.of()));

    var response = service.findChecklist("bio", "9001");

    assertTrue(response.isPresent());
    // opening, stratum, plots, notes, attachments
    assertEquals(5, response.get().sections().size());
    assertEquals("stratum", response.get().sections().get(1).id());
    assertTrue(response.get().sections().get(1).fields().isEmpty());
  }

  @Test
  void findChecklistReturnsEmptyForUnknownProtocol() {
    assertTrue(service.findChecklist("CHR", "9001").isEmpty());
  }

  @Test
  void submitSucceedsForEditableSlrRecordWhenNoValidationError() {
    when(loggedUserHelper.getLoggedUserId()).thenReturn("IDIR\\u");
    when(writeRepository.submit("SLR", "9001", "IDIR\\u")).thenReturn("");

    service.submit("bio", "9001");

    verify(writeRepository).submit("SLR", "9001", "IDIR\\u");
  }

  @Test
  void submitThrowsValidationExceptionWithSplitMessages() {
    when(loggedUserHelper.getLoggedUserId()).thenReturn("u");
    when(writeRepository.submit("SLR", "9001", "u"))
        .thenReturn("frep.submit.common.evaluation;frep.submit.common.teamlead;");

    ProtocolSubmitValidationException ex = assertThrows(
        ProtocolSubmitValidationException.class, () -> service.submit("bio", "9001"));
    assertEquals(2, ex.getMessages().size());
    assertTrue(ex.getMessages().contains("frep.submit.common.teamlead"));
  }

  @Test
  void unsubmitSucceedsForEditableSlrRecord() {
    when(loggedUserHelper.getLoggedUserId()).thenReturn("u");
    when(writeRepository.unsubmit("SLR", "9001", "u")).thenReturn("");

    service.unsubmit("bio", "9001");

    verify(writeRepository).unsubmit("SLR", "9001", "u");
  }

  @Test
  void submitOnHistoricalSlbRecordIsForbidden() {
    when(checklistRepository.resolveResourceType("9001")).thenReturn("SLB");

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class, () -> service.submit("bio", "9001"));
    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    verify(writeRepository, never()).submit(any(), any(), any());
  }

  @Test
  void saveBioStratumOnHistoricalSlbRecordIsForbidden() {
    when(checklistRepository.resolveResourceType("9001")).thenReturn("SLB");

    BioStratum stratum = stratum("A1", "CC", "Y", "3", "2.5", "HNR", "CWH", "ds", null);
    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class, () -> service.saveBioStratum(stratum));
    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    verify(writeRepository, never()).saveBioStratum(any(), any());
  }

  @Test
  void getBiodiversityOpeningThrowsNotFoundWhenMissing() {
    when(writeRepository.getBiodiversityOpening("9001")).thenReturn(null);
    assertThrows(ResponseStatusException.class, () -> service.getBiodiversityOpening("9001"));
  }

  // checklistId, resourceValueId, statusCode, frepWtpOverride, locationDescription,
  // patchReservesOnBlock, patchReservesSampled, innovativePracticeInd, innovativePracticesComment,
  // invasivePlantIndicator, invasivePlantComment, frepSiteEvaluationCode, evaluatorOpinionComment,
  // evaluationDate, revisionCount, grossArea, netArea, harvestDate,
  // teamLeadNameId, teamLeadName, teamLeadRevisionCount
  private static BiodiversityOpening opening(String frepWtpOverride, String locationDescription,
      String innovativePracticeInd, String innovativePracticesComment, String invasivePlantIndicator,
      String invasivePlantComment, String frepSiteEvaluationCode) {
    return new BiodiversityOpening("9001", "500", "ACT", frepWtpOverride, locationDescription, "N",
        "N", innovativePracticeInd, innovativePracticesComment, invasivePlantIndicator,
        invasivePlantComment, frepSiteEvaluationCode, "ok", null, "3", null, null, null, null, null,
        null);
  }

  @Test
  void openingLengthLimitsAreMeasuredInBytesNotCharacters() {
    // No loggedUserHelper stub: validation runs before the caller is resolved, so the save is
    // rejected without ever reaching it.
    // BIODIVERSITY_CHECKLIST.LOCATION_DESCRIPTION is VARCHAR2(50 BYTE). 26 curly quotes are 26
    // characters but 78 bytes, so a character count would wave this through and the insert would
    // fail with ORA-12899.
    String smartQuotes = "\u2019".repeat(26);
    assertEquals(26, smartQuotes.length());

    InvalidPayloadException thrown = assertThrows(InvalidPayloadException.class,
        () -> service.saveBiodiversityOpening("9001",
            opening(null, smartQuotes, "N", null, "N", null, "W")));

    assertTrue(thrown.getError().getMessage().contains("the limit is 50 and this entry uses 78"),
        thrown.getError().getMessage());
    verify(writeRepository, never()).saveBiodiversityOpening(any(), any());
  }

  @Test
  void openingAcceptsAValueThatFillsTheByteLimitExactly() {
    when(loggedUserHelper.getLoggedUserId()).thenReturn("u");
    BiodiversityOpening opening = opening(null, "x".repeat(50), "N", null, "N", null, "W");
    when(writeRepository.saveBiodiversityOpening(opening, "u")).thenReturn(opening);
    when(writeRepository.getBiodiversityOpening("9001")).thenReturn(opening);

    service.saveBiodiversityOpening("9001", opening);

    verify(writeRepository).saveBiodiversityOpening(opening, "u");
  }

  @Test
  void saveBiodiversityOpeningDelegatesToRepositoryWhenWritable() {
    when(loggedUserHelper.getLoggedUserId()).thenReturn("u");
    BiodiversityOpening opening = opening(null, "loc", "N", null, "N", null, "W");
    when(writeRepository.saveBiodiversityOpening(opening, "u")).thenReturn(opening);
    // Save re-reads the opening (to return the evaluator + fresh revision).
    when(writeRepository.getBiodiversityOpening("9001")).thenReturn(opening);

    BiodiversityOpening saved = service.saveBiodiversityOpening("9001", opening);

    assertEquals("9001", saved.checklistId());
    verify(writeRepository).saveBiodiversityOpening(opening, "u");
  }

  @Test
  void saveBiodiversityOpeningAssignsCallerAsLeadWhenClaimed() {
    when(loggedUserHelper.getLoggedUserId()).thenReturn("IDIR\\ME");
    // The payload claims the caller as the evaluator; the current lead is someone else.
    BiodiversityOpening claimed = opening(null, "loc", "N", null, "N", null, "W").withTeamLead(
        "IDIR\\ME", null, null);
    BiodiversityOpening current = opening(null, "loc", "N", null, "N", null, "W").withTeamLead(
        "IDIR\\OTHER", "Other", "7");
    when(writeRepository.saveBiodiversityOpening(claimed, "IDIR\\ME")).thenReturn(claimed);
    when(writeRepository.getBiodiversityOpening("9001")).thenReturn(current);
    // The resource value type comes from the record, not the URL — a new record resolves to SLR.
    when(checklistRepository.resolveResourceType("9001")).thenReturn("SLR");

    service.saveBiodiversityOpening("9001", claimed);

    verify(writeRepository).assignBiodiversityLead("9001", "SLR", "IDIR\\ME", "IDIR\\OTHER", "7",
        "IDIR\\ME");
  }

  @Test
  void saveBiodiversityOpeningRejectsMissingRequiredFields() {
    // Blank location, invasive plant indicator, innovative practice and rating.
    BiodiversityOpening bad = opening(null, "", null, null, null, null, null);
    assertThrows(InvalidPayloadException.class, () -> service.saveBiodiversityOpening("9001", bad));
  }

  @Test
  void saveBiodiversityOpeningRequiresInnovativeCommentWhenPracticeIsYes() {
    BiodiversityOpening bad = opening(null, "loc", "Y", null, "N", null, "W");
    assertThrows(InvalidPayloadException.class, () -> service.saveBiodiversityOpening("9001", bad));
  }

  @Test
  void saveBiodiversityOpeningRejectsOutOfRangeOverride() {
    BiodiversityOpening bad = opening("0", "loc", "N", null, "N", null, "W");
    assertThrows(InvalidPayloadException.class, () -> service.saveBiodiversityOpening("9001", bad));
  }

  // A BioStratum with the relevant fields set and everything else null/empty. Constructor order
  // matches BioStratum: ...6 nulls before harvestAreaCode, 31 nulls after it, 8 nulls before
  // revisionCount, then the windthrow-treatment list.
  private static BioStratum stratum(String stratumNumber, String strataTypeCode,
      String consistentMapInd, String plotCount, String size, String harvestAreaCode,
      String bgcZoneCode, String bgcSubzoneCode, String estimatedSize) {
    return new BioStratum(
        "S1", "9001", strataTypeCode, stratumNumber, null, null, plotCount, size, consistentMapInd,
        estimatedSize,
        null, null, null, null, null, null, // patch* / constraintInd / wetlandPct (11-16)
        harvestAreaCode, // 17
        null, null, null, null, null, null, null, null, null, null, null, null, null, // 18-30
        null, null, // otherConstraintPct, ecoIndicator (31-32)
        null, null, null, null, null, null, null, null, // eco counts (33-40)
        null, null, null, null, null, null, // eco checkboxes (41-46)
        null, null, // otherEcoAnchorCnt, otherEcoAnchorDesc (47-48)
        bgcZoneCode, bgcSubzoneCode, // 49-50
        null, null, null, null, null, null, null, null, // bgc* / windthrow* / constrainedTotal (51-58)
        "2", List.of());
  }

  @Test
  void saveBioStratumRejectsMissingRequiredFields() {
    BioStratum bad = stratum(null, null, null, null, null, null, null, null, null);
    assertThrows(InvalidPayloadException.class, () -> service.saveBioStratum(bad));
  }

  @Test
  void saveBioStratumRejectsMissingBgcSubzone() {
    // Valid except BGC subzone is blank (legacy PT #43888 requires it).
    BioStratum bad = stratum("A1", "CC", "Y", "3", "2.5", "HNR", "CWH", null, null);
    assertThrows(InvalidPayloadException.class, () -> service.saveBioStratum(bad));
  }

  @Test
  void saveBioStratumRejectsZeroPlotsOnNonPatchType() {
    BioStratum bad = stratum("A1", "CC", "Y", "0", "2.5", "HNR", "CWH", "ds", null);
    assertThrows(InvalidPayloadException.class, () -> service.saveBioStratum(bad));
  }

  @Test
  void saveBioStratumAcceptsValidStratumAndDelegates() {
    BioStratum valid = stratum("A1", "CC", "Y", "3", "2.5", "HNR", "CWH", "ds", null);
    when(loggedUserHelper.getLoggedUserId()).thenReturn("u");
    when(writeRepository.saveBioStratum(valid, "u")).thenReturn(valid);

    BioStratum saved = service.saveBioStratum(valid);

    assertEquals("A1", saved.stratumNumber());
    verify(writeRepository).saveBioStratum(valid, "u");
  }

  @Test
  void listBioStrataDelegatesToRepository() {
    BioStratumRow row = new BioStratumRow("900", "1", "MAT", "2024-05-01", "5", "3.2", "2");
    when(writeRepository.listBioStrata("1001")).thenReturn(List.of(row));

    assertEquals(1, service.listBioStrata("1001").size());
  }

  @Test
  void getBioStratumThrowsNotFoundWhenMissing() {
    when(writeRepository.getBioStratum("900")).thenReturn(null);
    assertThrows(ResponseStatusException.class, () -> service.getBioStratum("900"));
  }

  @Test
  void deleteBioStratumDelegatesWhenWritable() {
    when(writeRepository.deleteBioStratum("900", "2")).thenReturn("");

    service.deleteBioStratum("900", "2");

    verify(writeRepository).deleteBioStratum("900", "2");
  }

  @Test
  void listBioPlotsDelegatesToRepository() {
    when(writeRepository.listBioPlots("900"))
        .thenReturn(List.of(new BioPlotRow("500", "1", "jdoe", null, "2")));

    assertEquals(1, service.listBioPlots("900").size());
  }

  @Test
  void listBioPlotsResolvesTheAssessorDisplayNameButKeepsTheUserid() {
    when(writeRepository.listBioPlots("900"))
        .thenReturn(List.of(new BioPlotRow("500", "1", "jdoe", null, "2")));
    when(famUserDirectoryService.resolveName("jdoe")).thenReturn(Optional.of("Jane Doe (jdoe)"));

    BioPlotRow row = service.listBioPlots("900").get(0);

    assertEquals("Jane Doe (jdoe)", row.assessorDisplayName());
    assertEquals("jdoe", row.assessorName()); // the stored userid must not be overwritten
  }

  @Test
  void listBioPlotsFallsBackToTheUseridWhenFamHasNoName() {
    when(writeRepository.listBioPlots("900"))
        .thenReturn(List.of(new BioPlotRow("500", "1", "jdoe", null, "2")));
    when(famUserDirectoryService.resolveName("jdoe")).thenReturn(Optional.empty());

    assertEquals("jdoe", service.listBioPlots("900").get(0).assessorDisplayName());
  }

  @Test
  void getBioPlotThrowsNotFoundWhenMissing() {
    when(writeRepository.getBioPlot("500")).thenReturn(null);
    assertThrows(ResponseStatusException.class, () -> service.getBioPlot("500"));
  }

  // BioPlot order: plotId, stratumId, plotNumber, assessorName, utmSignal, utmZone, utmEasting,
  // utmNorthing, treeIndicator, basalAreaFactor, fixedAreaRadius, fullCountArea,
  // cwdTransectIndicator, firstLegTransect, secondLegTransect, plotComment, revisionCount,
  // standTable, cwdTable, assessorDisplayName. (No UTM signal → UTM fields exempt.)
  private static BioPlot plot(String assessorName, String firstLeg, String secondLeg, String baf,
      String treeIndicator, List<BioStandRow> standTable) {
    return new BioPlot("P1", "S1", "1", assessorName, "N", null, null, null, treeIndicator, baf,
        null, null, "N", firstLeg, secondLeg, null, "1", standTable, List.of(), null);
  }

  @Test
  void saveBioPlotRejectsMissingRequiredFields() {
    BioPlot bad = plot(null, null, null, null, "N", List.of());
    assertThrows(InvalidPayloadException.class, () -> service.saveBioPlot(bad));
  }

  @Test
  void saveBioPlotRejectsMissingMeasurementMethod() {
    // Valid except no measurement method (BAF/fixed-area/full-count all blank).
    BioPlot bad = plot("IDIR\\JDOE", "120", "240", null, "N", List.of());
    assertThrows(InvalidPayloadException.class, () -> service.saveBioPlot(bad));
  }

  @Test
  void saveBioPlotRejectsIncompleteStandRow() {
    // Trees exist, but the single stand row is missing DBH.
    BioStandRow row = new BioStandRow(null, null, "FD", null, "1", "", "18.2", null, "1", null,
        null, null, null);
    BioPlot bad = plot("IDIR\\JDOE", "120", "240", "10", "Y", List.of(row));
    assertThrows(InvalidPayloadException.class, () -> service.saveBioPlot(bad));
  }

  @Test
  void saveBioPlotAcceptsValidPlotAndDelegates() {
    BioPlot valid = plot("IDIR\\JDOE", "120", "240", "10", "N", List.of());
    when(loggedUserHelper.getLoggedUserId()).thenReturn("u");
    when(writeRepository.saveBioPlot(valid, "u")).thenReturn(valid);

    BioPlot saved = service.saveBioPlot(valid);

    assertEquals("P1", saved.plotId());
    verify(writeRepository).saveBioPlot(valid, "u");
  }

  @Test
  void deleteBioPlotDelegatesWhenWritable() {
    when(writeRepository.deleteBioPlot("500", "2")).thenReturn("");

    service.deleteBioPlot("500", "2");

    verify(writeRepository).deleteBioPlot("500", "2");
  }

  private static ChecklistSectionData sectionWithHeader(
      ChecklistHeaderData header,
      Map<String, String> fields
  ) {
    return ChecklistSectionData.of(header, fields);
  }

  // ── Attachment upload (multipart) ────────────────────────────────────

  private static MockMultipartFile upload(String name, byte[] content) {
    return new MockMultipartFile("file", name, "application/pdf", content);
  }

  @Test
  void rejectsAnEmptyUploadBeforeScanningOrWriting() {
    // Legacy rejected zero-byte attachments (frep.web.error.emptyFile); the rewrite lost that, and
    // an empty file produces a metadata row pointing at nothing.
    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.saveAttachment("bio", "1", upload("notes.pdf", new byte[0]), "desc", null));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    verifyNoInteractions(virusScanner);
    verify(writeRepository, never())
        .saveAttachment(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void rejectsAMissingFilePart() {
    assertThrows(ResponseStatusException.class,
        () -> service.saveAttachment("bio", "1", null, "desc", null));
    verifyNoInteractions(virusScanner);
  }

  @Test
  void rejectsAnUnsupportedExtensionBeforeScanning() {
    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.saveAttachment("bio", "1", upload("evil.exe", new byte[] {1, 2, 3}), "desc", null));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    verifyNoInteractions(virusScanner);
  }

  @Test
  void scansTheUploadedBytesBeforePersistingThem() {
    byte[] content = {1, 2, 3, 4};
    when(loggedUserHelper.getLoggedUserId()).thenReturn("IDIR\\SOMEONE");

    service.saveAttachment("bio", "1", upload("notes.pdf", content), " spaced desc ", null);

    InOrder order = inOrder(virusScanner, writeRepository);
    order.verify(virusScanner).scanOrThrow(content, "notes.pdf");
    // SLR, not the {protocol} segment: the type is resolved from the record (@BeforeEach stub).
    order.verify(writeRepository).saveAttachment(
        eq("1"), eq("SLR"), eq("notes.pdf"), eq(" spaced desc "), eq("application/pdf"),
        eq(content), eq("IDIR\\SOMEONE"));
  }

  @Test
  void readsTheUploadedFileOnlyOnce() throws Exception {
    // getBytes() allocates a fresh array per call, so calling it for the scan and again for the
    // write would put two copies of the file on a 400m heap. nr-fspts does exactly that.
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getOriginalFilename()).thenReturn("notes.pdf");
    when(file.getContentType()).thenReturn("application/pdf");
    when(file.getBytes()).thenReturn(new byte[] {9, 9});

    assertDoesNotThrow(() -> service.saveAttachment("bio", "1", file, "desc", null));

    verify(file, times(1)).getBytes();
  }

  @Test
  void turnsAnUnreadableUploadIntoABadRequestRatherThanA500() throws Exception {
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getOriginalFilename()).thenReturn("notes.pdf");
    when(file.getBytes()).thenThrow(new IOException("spool file vanished"));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.saveAttachment("bio", "1", file, "desc", null));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    verifyNoInteractions(virusScanner);
  }

  // getAttachments issues one object-storage HEAD per returned row, so an unclamped `size` turns a
  // single request into that many sequential remote calls. These assert on the values handed to the
  // repository, which is where the cap has to bite — not on the response length.

  @Test
  void getAttachmentsClampsPageSizeBeforeQuerying() {
    when(writeRepository.getAttachments(eq("1"), eq("SLR"), anyInt(), anyInt()))
        .thenReturn(List.of());
    when(writeRepository.countAttachments("1", "SLR")).thenReturn(0);

    service.getAttachments("bio", "1", 0, 5000);

    verify(writeRepository).getAttachments("1", "SLR", 0, 100);
  }

  @Test
  void getAttachmentsFloorsNegativePageAndSize() {
    when(writeRepository.getAttachments(eq("1"), eq("SLR"), anyInt(), anyInt()))
        .thenReturn(List.of());
    when(writeRepository.countAttachments("1", "SLR")).thenReturn(0);

    service.getAttachments("bio", "1", -5, 0);

    // A negative page would reach Oracle as a negative OFFSET; size 0 would return nothing forever.
    verify(writeRepository).getAttachments("1", "SLR", 0, 1);
  }

  @Test
  void getAttachmentsIssuesOneStorageLookupPerReturnedRow() {
    when(writeRepository.getAttachments(eq("1"), eq("SLR"), anyInt(), anyInt()))
        .thenReturn(List.of(
            new AttachmentRow("7", "a.pdf", "first", "PDF", null),
            new AttachmentRow("8", "b.pdf", "second", "PDF", null)));
    when(writeRepository.countAttachments("1", "SLR")).thenReturn(2);
    when(objectStorage.getObjectSize("slr/7")).thenReturn(1024L);
    when(objectStorage.getObjectSize("slr/8")).thenReturn(-1L);

    ProtocolChecklistService.AttachmentPage page = service.getAttachments("bio", "1", 0, 10);

    assertEquals(2, page.attachments().size());
    assertEquals("1024", page.attachments().get(0).fileSize());
    // A negative size means "not found in object storage" and must surface as null, not "-1".
    assertEquals(null, page.attachments().get(1).fileSize());
    assertEquals(2, page.totalCount());
  }
}

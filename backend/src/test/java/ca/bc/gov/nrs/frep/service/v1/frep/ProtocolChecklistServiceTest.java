package ca.bc.gov.nrs.frep.service.v1.frep;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

import ca.bc.gov.nrs.frep.exception.InvalidPayloadException;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentRow;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import ca.bc.gov.nrs.frep.configuration.AttachmentTypes;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentContent;
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

  @Mock
  private VirusScanner virusScanner;

  @Mock
  private ca.bc.gov.nrs.frep.service.v1.ObjectStorageService objectStorage;

  // A real instance rather than a @Mock: the allow-list behaviour these tests exercise IS this
  // bean's behaviour. There is no in-code default, so the value ATTACHMENT_ALLOWED_TYPES is expected
  // to carry is spelled out here. @Spy so @InjectMocks picks it up.
  @Spy
  private AttachmentTypes attachmentTypes = new AttachmentTypes(
      "BMP,CSV,DOC,DOCX,GIF,HTM,IFM,JPG,JPK,MDB,MDE,MP4,OBD,PDF,PNG,PPS,PPT,PPTX,RPT,RTF,TIF,"
          + "TIFF,TXT,WAV,WEBP,XLD,XLS,XLSX,XML,ZIP");

  @InjectMocks
  private ProtocolChecklistService service;

  @BeforeEach
  void stubRecordType() {
    // Type is resolved from the record (not the URL). New/editable biodiversity records are SLR;
    // historical SLB records are view-only (mutations 403). Default the editable path to SLR; the
    // few view-only/read tests override this stub to SLB explicitly.
    lenient().when(checklistRepository.resolveResourceType(anyString())).thenReturn("SLR");
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
        () -> service.saveAttachment("bio", "1", upload("notes.pdf", new byte[0]), "desc"));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    verifyNoInteractions(virusScanner);
    verify(writeRepository, never())
        .saveAttachment(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void rejectsAMissingFilePart() {
    assertThrows(ResponseStatusException.class,
        () -> service.saveAttachment("bio", "1", null, "desc"));
    verifyNoInteractions(virusScanner);
  }

  @Test
  void rejectsAnUnsupportedExtensionBeforeScanning() {
    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.saveAttachment("bio", "1", upload("evil.exe", new byte[] {1, 2, 3}), "desc"));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    verifyNoInteractions(virusScanner);
  }

  // The modern Office formats and the four-letter image spellings. These are the only allowed
  // extensions longer than three characters, and MIME_TYPE_CODE is VARCHAR2(3 BYTE) — the write
  // path maps them down (EXTENSION_MIME_CODE), so accepting them here must stay paired with that.
  @ParameterizedTest
  @ValueSource(strings = {"report.docx", "data.xlsx", "deck.pptx", "scan.tiff", "photo.webp"})
  void acceptsTheFourCharacterExtensions(String fileName) {
    assertDoesNotThrow(
        () -> service.saveAttachment("bio", "1", upload(fileName, new byte[] {1, 2, 3}), "desc"));
  }

  @Test
  void listsTheNewTypesInTheRejectionMessageSoTheUserCanSeeThemAllowed() {
    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.saveAttachment("bio", "1", upload("evil.exe", new byte[] {1, 2, 3}), "desc"));

    String reason = String.valueOf(ex.getReason());
    // The display string is maintained separately from the enforcing Set; if they drift, the error
    // tells the user a type is unsupported while the validator happily accepts it.
    assertTrue(reason.contains("DOCX"), reason);
    assertTrue(reason.contains("XLSX"), reason);
    assertTrue(reason.contains("PPTX"), reason);
    assertTrue(reason.contains("TIFF"), reason);
    assertTrue(reason.contains("WEBP"), reason);
  }

  @Test
  void scansTheUploadedBytesBeforePersistingThem() {
    byte[] content = {1, 2, 3, 4};
    when(loggedUserHelper.getLoggedUserId()).thenReturn("IDIR\\SOMEONE");

    service.saveAttachment("bio", "1", upload("notes.pdf", content), " spaced desc ");

    InOrder order = inOrder(virusScanner, writeRepository);
    order.verify(virusScanner).scanOrThrow(content, "notes.pdf");
    // SLR, not the {protocol} segment: the type is resolved from the record (@BeforeEach stub).
    order.verify(writeRepository).saveAttachment(
        eq("1"), eq("SLR"), eq("notes.pdf"), eq(" spaced desc "), eq("application/pdf"),
        eq(content), eq("IDIR\\SOMEONE"));
  }

  @Test
  void storesOurMediaTypeRatherThanTheOneTheBrowserClaimed() {
    // The value handed to the repository is what object storage records as the object's
    // Content-Type. The browser's claim varies by OS/browser for the same format and is blank for
    // the legacy MoF types, so it must not be what we persist — .webp here is deliberately uploaded
    // as a generic binary, which is exactly what some browsers send.
    when(loggedUserHelper.getLoggedUserId()).thenReturn("IDIR\\SOMEONE");
    byte[] content = {1, 2};

    service.saveAttachment("bio", "1",
        new MockMultipartFile("file", "map.webp", "application/octet-stream", content), "desc");

    verify(writeRepository).saveAttachment(
        eq("1"), eq("SLR"), eq("map.webp"), eq("desc"), eq("image/webp"), eq(content),
        eq("IDIR\\SOMEONE"));
  }

  @Test
  void readsTheUploadedFileOnlyOnce() throws Exception {
    // getBytes() allocates a fresh array per call, so calling it for the scan and again for the
    // write would put two copies of the file on a 400m heap. nr-fspts does exactly that.
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getOriginalFilename()).thenReturn("notes.pdf");
    when(file.getBytes()).thenReturn(new byte[] {9, 9});

    assertDoesNotThrow(() -> service.saveAttachment("bio", "1", file, "desc"));

    verify(file, times(1)).getBytes();
  }

  @Test
  void turnsAnUnreadableUploadIntoABadRequestRatherThanA500() throws Exception {
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getOriginalFilename()).thenReturn("notes.pdf");
    when(file.getBytes()).thenThrow(new IOException("spool file vanished"));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.saveAttachment("bio", "1", file, "desc"));
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

  // --- Media types (the extension -> MIME map that replaced MIME_TYPE_CODE.DESCRIPTION) ---

  @Test
  void resolvesTheMediaTypeFromTheExtensionRatherThanTheDatabase() {
    // The proc's out-param is NULL for anything the shared code table never had (WEBP is the
    // motivating case: it is previewable, so a null used to yield data:WEBP;base64,...).
    when(writeRepository.getAttachmentContent("1", "SLR", "9"))
        .thenReturn(new AttachmentContent("map.webp", null, new byte[] {1}));

    AttachmentContent content = service.getAttachmentContent("bio", "1", "9");

    assertEquals("image/webp", content.mimeType());
  }

  @Test
  void prefersTheMappedMediaTypeOverAStaleStoredValue() {
    when(writeRepository.getAttachmentContent("1", "SLR", "9"))
        .thenReturn(new AttachmentContent("report.pdf", "text/plain", new byte[] {1}));

    assertEquals("application/pdf", service.getAttachmentContent("bio", "1", "9").mimeType());
  }

  @Test
  void fallsBackForAnExtensionThatIsNotMapped() {
    // Rows predating the map (or a file stored without an extension) must still download, just
    // without a specific type — never null, which is what the client cannot handle.
    when(writeRepository.getAttachmentContent("1", "SLR", "9"))
        .thenReturn(new AttachmentContent("legacy", null, new byte[] {1}));
    assertEquals("application/octet-stream",
        service.getAttachmentContent("bio", "1", "9").mimeType());

    when(writeRepository.getAttachmentContent("1", "SLR", "8"))
        .thenReturn(new AttachmentContent("legacy.odd", "application/x-odd", new byte[] {1}));
    assertEquals("application/x-odd",
        service.getAttachmentContent("bio", "1", "8").mimeType());
  }
}

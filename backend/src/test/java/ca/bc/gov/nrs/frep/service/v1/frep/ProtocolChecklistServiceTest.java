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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

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
  void saveBiodiversityOpeningAcceptsMissingRequiredFields() {
    // Blank location, invasive plant indicator, innovative practice and rating. An evaluator part-way
    // through the tab keeps what they have entered; the missing answers are reported on the tab and
    // block submit, not the save.
    when(loggedUserHelper.getLoggedUserId()).thenReturn("u");
    BiodiversityOpening incomplete = opening(null, "", null, null, null, null, null);
    when(writeRepository.saveBiodiversityOpening(incomplete, "u")).thenReturn(incomplete);
    when(writeRepository.getBiodiversityOpening("9001")).thenReturn(incomplete);

    service.saveBiodiversityOpening("9001", incomplete);

    verify(writeRepository).saveBiodiversityOpening(incomplete, "u");
  }

  @Test
  void saveBiodiversityOpeningAcceptsAMissingInnovativeCommentWhenPracticeIsYes() {
    when(loggedUserHelper.getLoggedUserId()).thenReturn("u");
    BiodiversityOpening incomplete = opening(null, "loc", "Y", null, "N", null, "W");
    when(writeRepository.saveBiodiversityOpening(incomplete, "u")).thenReturn(incomplete);
    when(writeRepository.getBiodiversityOpening("9001")).thenReturn(incomplete);

    service.saveBiodiversityOpening("9001", incomplete);

    verify(writeRepository).saveBiodiversityOpening(incomplete, "u");
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

  /**
   * The constrained total only ever *guarded* the two cross-field rules below it, so a value outside
   * 0-100 — or not a number at all — skipped every check and went to the proc.
   * {@code CALC_CONSTRAINED_TOTAL} is NUMBER(3), so "500" stored as nonsense and "abc" failed as a
   * conversion error.
   */
  @Test
  void saveBioStratumRejectsAConstrainedTotalOutsideItsRange() {
    assertThrows(InvalidPayloadException.class,
        () -> service.saveBioStratum(stratumWithConstrainedTotal("500")));
    assertThrows(InvalidPayloadException.class,
        () -> service.saveBioStratum(stratumWithConstrainedTotal("abc")));
  }

  @Test
  void saveBioStratumAcceptsAConstrainedTotalInRange() {
    when(loggedUserHelper.getLoggedUserId()).thenReturn("u");
    BioStratum ok = stratumWithConstrainedTotal("0");
    when(writeRepository.saveBioStratum(ok, "u")).thenReturn(ok);

    service.saveBioStratum(ok);

    verify(writeRepository).saveBioStratum(ok, "u");
  }

  /** A valid stratum carrying one constrained total. */
  private static BioStratum stratumWithConstrainedTotal(String total) {
    BioStratum base = stratum("A1", "CC", "Y", "3", "2.5", "HNR", "CWH", "ds", null);
    return new BioStratum(
        base.stratumId(), base.checklistId(), base.strataTypeCode(), base.stratumNumber(),
        base.summaryDate(), base.assessorName(), base.plotCount(), base.size(),
        base.consistentMapInd(), base.estimatedSize(),
        null, null, null, null, null, null,
        base.harvestAreaCode(),
        null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null,
        null, null, null, null, null, null, null, null,
        null, null, null, null, null, null,
        null, null,
        base.bgcZoneCode(), base.bgcSubzoneCode(),
        null, null, null, null, null, null, null, total,
        base.revisionCount(), List.of());
  }

  @Test
  void saveBioStratumRejectsMissingRequiredFields() {
    BioStratum bad = stratum(null, null, null, null, null, null, null, null, null);
    assertThrows(InvalidPayloadException.class, () -> service.saveBioStratum(bad));
  }

  @Test
  void saveBioStratumAcceptsAMissingNumberTypeAndSize() {
    // Nullable columns: a stratum entered before its type or size is known still stores. Plot count,
    // map-consistency, harvest area and BGC zone/subzone are the floor and are still supplied.
    when(loggedUserHelper.getLoggedUserId()).thenReturn("u");
    BioStratum partial = stratum(null, null, "Y", "3", null, "HNR", "CWH", "ds", null);
    when(writeRepository.saveBioStratum(partial, "u")).thenReturn(partial);

    service.saveBioStratum(partial);

    verify(writeRepository).saveBioStratum(partial, "u");
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

  /**
   * {@code BIODIVERSITY_PLOT.UTM_ZONE} is NUMBER(2) and had no rule at all: the picker only offers
   * 7-11, but the API is open to any client and the offline check-in path reaches the same code, so
   * a non-numeric zone went to Oracle and came back as "A database error occurred" naming nothing.
   */
  @Test
  void saveBioPlotRejectsAZoneTheColumnCannotHold() {
    BioPlot bad = plotWithZone("abc");
    assertThrows(InvalidPayloadException.class, () -> service.saveBioPlot(bad));
    assertThrows(InvalidPayloadException.class, () -> service.saveBioPlot(plotWithZone("100")));
  }

  @Test
  void saveBioPlotAcceptsAZoneOutsideThePickerButInsideTheColumn() {
    // Bounded by the column, not by the five BC zones: a legacy row on another zone still re-saves.
    when(loggedUserHelper.getLoggedUserId()).thenReturn("u");
    BioPlot ok = plotWithZone("12");
    when(writeRepository.saveBioPlot(ok, "u")).thenReturn(ok);

    service.saveBioPlot(ok);

    verify(writeRepository).saveBioPlot(ok, "u");
  }

  /** A valid plot carrying one UTM zone, with a signal recorded. */
  private static BioPlot plotWithZone(String zone) {
    return new BioPlot("P1", "S1", "1", "IDIR\\JDOE", "Y", zone, "123456", "1234567", "N", "5",
        null, null, "N", "120", "240", null, "1", List.of(), List.of(), null);
  }

  @Test
  void saveBioPlotRejectsABlankAssessor() {
    // BIODIVERSITY_PLOT.ASSESSOR_NAME is NOT NULL, so this one cannot be waved through. The blank
    // bearings alongside it are advisory now — the assessor is what refuses the save.
    BioPlot bad = plot(null, null, null, null, "N", List.of());
    assertThrows(InvalidPayloadException.class, () -> service.saveBioPlot(bad));
  }

  @Test
  void saveBioPlotAcceptsMissingBearingsAndUtm() {
    // A plot recorded before the transect is walked or the GPS gets a fix still stores.
    when(loggedUserHelper.getLoggedUserId()).thenReturn("u");
    BioPlot partial = plot("IDIR\\JDOE", null, null, "5", "N", List.of());
    when(writeRepository.saveBioPlot(partial, "u")).thenReturn(partial);

    service.saveBioPlot(partial);

    verify(writeRepository).saveBioPlot(partial, "u");
  }

  @Test
  void saveBioPlotAcceptsNoMeasurementMethodYet() {
    // No measurement method (BAF/fixed-area/full-count all blank) is a plot not measured yet, not a
    // contradiction — it stores, and the tab counts it against submit.
    when(loggedUserHelper.getLoggedUserId()).thenReturn("u");
    BioPlot unmeasured = plot("IDIR\\JDOE", "120", "240", null, "N", List.of());
    when(writeRepository.saveBioPlot(unmeasured, "u")).thenReturn(unmeasured);

    service.saveBioPlot(unmeasured);

    verify(writeRepository).saveBioPlot(unmeasured, "u");
  }

  @Test
  void saveBioPlotRejectsTwoMeasurementMethods() {
    // Naming both a BAF and a full-count area is a contradiction: still refused.
    BioPlot bad = new BioPlot("P1", "S1", "1", "IDIR\\JDOE", "N", null, null, null, "N", "5",
        null, "2.5", "N", "120", "240", null, "1", List.of(), List.of(), null);
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
  void readsTheUploadedFileOnlyOnce() throws Exception {
    // getBytes() allocates a fresh array per call, so calling it for the scan and again for the
    // write would put two copies of the file on a 400m heap. nr-fspts does exactly that.
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getOriginalFilename()).thenReturn("notes.pdf");
    when(file.getContentType()).thenReturn("application/pdf");
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

  @Nested
  @DisplayName("getBioPlot id validation")
  class GetBioPlotIdValidation {

    /**
     * BIODIVERSITY_PLOT_ID is a NUMBER. Binding a blank or non-numeric string fails inside Oracle as
     * "Invalid Input Number", which reached the evaluator as "A database error occurred… contact the
     * FREP help desk" and was logged as a system fault naming nothing.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "bio", "107a", "undefined"})
    @DisplayName("rejects a non-numeric plot id as a 400 that quotes it")
    void rejectsNonNumericPlotId(String plotId) {
      ResponseStatusException thrown =
          assertThrows(ResponseStatusException.class, () -> service.getBioPlot(plotId));

      assertThat(thrown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(thrown.getReason()).contains(plotId);
      // Never reaches the driver, so no "Database error" and no 500.
      verifyNoInteractions(writeRepository);
    }

    @Test
    @DisplayName("still reads a numeric plot id")
    void acceptsNumericPlotId() {
      when(writeRepository.getBioPlot("107")).thenReturn(null);

      assertThrows(ResponseStatusException.class, () -> service.getBioPlot("107"));
      verify(writeRepository).getBioPlot("107");
    }
  }
}

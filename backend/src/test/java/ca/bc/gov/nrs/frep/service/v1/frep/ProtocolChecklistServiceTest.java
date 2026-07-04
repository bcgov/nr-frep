package ca.bc.gov.nrs.frep.service.v1.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.exception.InvalidPayloadException;
import ca.bc.gov.nrs.frep.struct.v1.frep.AdministrationData;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratum;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlot;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStandRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlotRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratumRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BiodiversityOpening;
import ca.bc.gov.nrs.frep.struct.v1.frep.EvaluatorRow;
import java.time.LocalDate;
import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistHeaderData;
import ca.bc.gov.nrs.frep.repository.v1.ChecklistRepository;
import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistSectionData;
import ca.bc.gov.nrs.frep.repository.v1.CodeListRepository;
import ca.bc.gov.nrs.frep.repository.v1.ProtocolChecklistWriteRepository;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import ca.bc.gov.nrs.frep.service.v1.frep.ProtocolChecklistService.ProtocolSubmitValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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
    // Administration (FREP301) leads, mirroring the legacy tab bar; opening follows.
    assertEquals("administration", response.get().sections().get(0).id());
    assertEquals("opening", response.get().sections().get(1).id());
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
    // administration, opening, stratum, plots, notes, attachments
    assertEquals(6, response.get().sections().size());
    assertEquals("stratum", response.get().sections().get(2).id());
    assertTrue(response.get().sections().get(2).fields().isEmpty());
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

  // --- Administration save validation (legacy FREP301 FrepCostResourceValidatingManager) ---

  private static AdministrationData admin(String evaluationDate, String blockAccessTime,
      String hoursOnBlock, String peopleOnBlock, String teamLeadNameId, List<EvaluatorRow> members) {
    return new AdministrationData("9001", "500", "rv1", "SLB", "ACT", evaluationDate, null,
        blockAccessTime, hoursOnBlock, peopleOnBlock, null, teamLeadNameId, null, null, null, null,
        members);
  }

  @Test
  void saveAdministrationRejectsFutureEvaluationDate() {
    AdministrationData future = admin(LocalDate.now().plusDays(1).toString(), null, null, null,
        null, null);
    assertThrows(InvalidPayloadException.class, () -> service.saveAdministration("bio", future));
  }

  @Test
  void saveAdministrationRejectsOutOfRangeOrImpreciseHours() {
    AdministrationData bad = admin("2024-01-15", "12345.999", "8", "0", null, null);
    assertThrows(InvalidPayloadException.class, () -> service.saveAdministration("bio", bad));
  }

  @Test
  void saveAdministrationRejectsPeopleOnBlockBelowTeamSize() {
    // team = lead + 1 member = 2, but people on block = 1
    AdministrationData bad = admin("2024-01-15", "2.5", "8", "1", "LEAD",
        List.of(new EvaluatorRow("M1", null, "N", null, null)));
    assertThrows(InvalidPayloadException.class, () -> service.saveAdministration("bio", bad));
  }

  @Test
  void saveAdministrationAcceptsValidDataAndDelegates() {
    AdministrationData valid = admin("2024-01-15", "2.5", "8", "0", null, null);
    when(loggedUserHelper.getLoggedUserId()).thenReturn("tester");
    when(writeRepository.saveAdministration(valid, "tester")).thenReturn(valid);

    AdministrationData saved = service.saveAdministration("bio", valid);

    assertEquals("2024-01-15", saved.evaluationDate());
    verify(writeRepository).saveAdministration(valid, "tester");
  }

  @Test
  void getBiodiversityOpeningThrowsNotFoundWhenMissing() {
    when(writeRepository.getBiodiversityOpening("9001")).thenReturn(null);
    assertThrows(ResponseStatusException.class, () -> service.getBiodiversityOpening("9001"));
  }

  // checklistId, resourceValueId, statusCode, frepWtpOverride, locationDescription,
  // patchReservesOnBlock, patchReservesSampled, innovativePracticeInd, innovativePracticesComment,
  // invasivePlantIndicator, invasivePlantComment, frepSiteEvaluationCode, evaluatorOpinionComment,
  // revisionCount, grossArea, netArea, harvestDate
  private static BiodiversityOpening opening(String frepWtpOverride, String locationDescription,
      String innovativePracticeInd, String innovativePracticesComment, String invasivePlantIndicator,
      String invasivePlantComment, String frepSiteEvaluationCode) {
    return new BiodiversityOpening("9001", "500", "ACT", frepWtpOverride, locationDescription, "N",
        "N", innovativePracticeInd, innovativePracticesComment, invasivePlantIndicator,
        invasivePlantComment, frepSiteEvaluationCode, "ok", "3", null, null, null);
  }

  @Test
  void saveBiodiversityOpeningDelegatesToRepositoryWhenWritable() {
    when(loggedUserHelper.getLoggedUserId()).thenReturn("u");
    BiodiversityOpening opening = opening(null, "loc", "N", null, "N", null, "W");
    when(writeRepository.saveBiodiversityOpening(opening, "u")).thenReturn(opening);

    BiodiversityOpening saved = service.saveBiodiversityOpening("9001", opening);

    assertEquals("9001", saved.checklistId());
    verify(writeRepository).saveBiodiversityOpening(opening, "u");
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
        .thenReturn(List.of(new BioPlotRow("500", "1", "jdoe", "2")));

    assertEquals(1, service.listBioPlots("900").size());
  }

  @Test
  void getBioPlotThrowsNotFoundWhenMissing() {
    when(writeRepository.getBioPlot("500")).thenReturn(null);
    assertThrows(ResponseStatusException.class, () -> service.getBioPlot("500"));
  }

  // BioPlot order: plotId, stratumId, plotNumber, assessorName, utmSignal, utmZone, utmEasting,
  // utmNorthing, treeIndicator, basalAreaFactor, fixedAreaRadius, fullCountArea,
  // cwdTransectIndicator, firstLegTransect, secondLegTransect, plotComment, revisionCount,
  // standTable, cwdTable. (No UTM signal → UTM fields exempt.)
  private static BioPlot plot(String assessorName, String firstLeg, String secondLeg, String baf,
      String treeIndicator, List<BioStandRow> standTable) {
    return new BioPlot("P1", "S1", "1", assessorName, "N", null, null, null, treeIndicator, baf,
        null, null, "N", firstLeg, secondLeg, null, "1", standTable, List.of());
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
}

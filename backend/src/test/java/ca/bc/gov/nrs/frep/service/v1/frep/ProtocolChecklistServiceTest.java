package ca.bc.gov.nrs.frep.service.v1.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlot;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlotRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratumRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BiodiversityOpening;
import ca.bc.gov.nrs.frep.repository.v1.frep.ChecklistHeaderData;
import ca.bc.gov.nrs.frep.repository.v1.frep.ChecklistRepository;
import ca.bc.gov.nrs.frep.repository.v1.frep.ChecklistSectionData;
import ca.bc.gov.nrs.frep.repository.v1.frep.CodeListRepository;
import ca.bc.gov.nrs.frep.repository.v1.frep.ProtocolChecklistWriteRepository;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import ca.bc.gov.nrs.frep.service.v1.frep.ProtocolChecklistService.ProtocolSubmitValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

  @InjectMocks
  private ProtocolChecklistService service;

  @Test
  void normalizeProtocolTypeMapsBioAndRejectsOutOfScope() {
    assertEquals(Optional.of("SLB"), ProtocolChecklistService.normalizeProtocolType("bio"));
    assertEquals(Optional.of("SLB"), ProtocolChecklistService.normalizeProtocolType("SLB"));
    // Riparian + Water are out of scope — no longer recognised.
    assertTrue(ProtocolChecklistService.normalizeProtocolType("rip").isEmpty());
    assertTrue(ProtocolChecklistService.normalizeProtocolType("wat").isEmpty());
    assertTrue(ProtocolChecklistService.normalizeProtocolType("wtr").isEmpty());
    assertTrue(ProtocolChecklistService.normalizeProtocolType("CHR").isEmpty());
    assertTrue(ProtocolChecklistService.normalizeProtocolType("").isEmpty());
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
        "Opening info (FREP210)",
        ChecklistSectionData.fieldsOnly(fields)
    );

    assertEquals("opening", section.id());
    assertEquals("Opening info (FREP210)", section.title());
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
  void submitMapsBioToSlbAndSucceedsWhenNoValidationError() {
    when(loggedUserHelper.canWrite()).thenReturn(true);
    when(loggedUserHelper.getLoggedUserId()).thenReturn("IDIR\\u");
    when(writeRepository.submit("SLB", "9001", "IDIR\\u")).thenReturn("");

    service.submit("bio", "9001");

    verify(writeRepository).submit("SLB", "9001", "IDIR\\u");
  }

  @Test
  void submitThrowsValidationExceptionWithSplitMessages() {
    when(loggedUserHelper.canWrite()).thenReturn(true);
    when(loggedUserHelper.getLoggedUserId()).thenReturn("u");
    when(writeRepository.submit("SLB", "9001", "u"))
        .thenReturn("frep.submit.common.evaluation;frep.submit.common.teamlead;");

    ProtocolSubmitValidationException ex = assertThrows(
        ProtocolSubmitValidationException.class, () -> service.submit("bio", "9001"));
    assertEquals(2, ex.getMessages().size());
    assertTrue(ex.getMessages().contains("frep.submit.common.teamlead"));
  }

  @Test
  void submitForbiddenWhenUserCannotWrite() {
    when(loggedUserHelper.canWrite()).thenReturn(false);
    assertThrows(ResponseStatusException.class, () -> service.submit("bio", "9001"));
  }

  @Test
  void unsubmitMapsBioToSlb() {
    when(loggedUserHelper.canWrite()).thenReturn(true);
    when(loggedUserHelper.getLoggedUserId()).thenReturn("u");
    when(writeRepository.unsubmit("SLB", "9001", "u")).thenReturn("");

    service.unsubmit("bio", "9001");

    verify(writeRepository).unsubmit("SLB", "9001", "u");
  }

  @Test
  void getBiodiversityOpeningThrowsNotFoundWhenMissing() {
    when(writeRepository.getBiodiversityOpening("9001")).thenReturn(null);
    assertThrows(ResponseStatusException.class, () -> service.getBiodiversityOpening("9001"));
  }

  @Test
  void saveBiodiversityOpeningDelegatesToRepositoryWhenWritable() {
    when(loggedUserHelper.canWrite()).thenReturn(true);
    when(loggedUserHelper.getLoggedUserId()).thenReturn("u");
    BiodiversityOpening opening = new BiodiversityOpening(
        "9001", "500", "ACT", "N", "loc", "N", "N", "N", null, "N", null, "W", "ok", "3",
        null, null, null);
    when(writeRepository.saveBiodiversityOpening(opening, "u")).thenReturn(opening);

    BiodiversityOpening saved = service.saveBiodiversityOpening("9001", opening);

    assertEquals("9001", saved.checklistId());
    verify(writeRepository).saveBiodiversityOpening(opening, "u");
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
  void saveBioStratumForbiddenWhenUserCannotWrite() {
    when(loggedUserHelper.canWrite()).thenReturn(false);
    assertThrows(ResponseStatusException.class, () -> service.saveBioStratum(null));
  }

  @Test
  void deleteBioStratumDelegatesWhenWritable() {
    when(loggedUserHelper.canWrite()).thenReturn(true);
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

  @Test
  void saveBioPlotForbiddenWhenUserCannotWrite() {
    when(loggedUserHelper.canWrite()).thenReturn(false);
    assertThrows(ResponseStatusException.class, () -> service.saveBioPlot(new BioPlot(
        null, "900", null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null)));
  }

  @Test
  void deleteBioPlotDelegatesWhenWritable() {
    when(loggedUserHelper.canWrite()).thenReturn(true);
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

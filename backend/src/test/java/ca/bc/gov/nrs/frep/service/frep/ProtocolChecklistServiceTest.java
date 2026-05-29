package ca.bc.gov.nrs.frep.service.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.repository.frep.ChecklistHeaderData;
import ca.bc.gov.nrs.frep.repository.frep.ChecklistRepository;
import ca.bc.gov.nrs.frep.repository.frep.ChecklistSectionData;
import ca.bc.gov.nrs.frep.repository.frep.CodeListRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProtocolChecklistServiceTest {

  @Mock
  private ChecklistRepository checklistRepository;

  @Mock
  private CodeListRepository codeListRepository;

  @InjectMocks
  private ProtocolChecklistService service;

  @Test
  void normalizeProtocolTypeMapsLegacyAliases() {
    assertEquals(Optional.of("SLB"), ProtocolChecklistService.normalizeProtocolType("bio"));
    assertEquals(Optional.of("SLB"), ProtocolChecklistService.normalizeProtocolType("SLB"));
    assertEquals(Optional.of("RIP"), ProtocolChecklistService.normalizeProtocolType("rip"));
    assertEquals(Optional.of("WTR"), ProtocolChecklistService.normalizeProtocolType("wat"));
    assertEquals(Optional.of("WTR"), ProtocolChecklistService.normalizeProtocolType("wtr"));
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
    assertEquals("opening", response.get().sections().get(0).id());
  }

  @Test
  void findChecklistReturnsEmptyForUnknownProtocol() {
    assertTrue(service.findChecklist("CHR", "9001").isEmpty());
  }

  private static ChecklistSectionData sectionWithHeader(
      ChecklistHeaderData header,
      Map<String, String> fields
  ) {
    return ChecklistSectionData.of(header, fields);
  }
}

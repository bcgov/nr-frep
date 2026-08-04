package ca.bc.gov.nrs.frep.service.v1.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.repository.v1.CodeListRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

  @Mock
  private CodeListRepository codeListRepository;

  @InjectMocks
  private ConfigurationService service;

  @Test
  void mapFromProcedureRowParsesLegacyDescriptionFormat() {
    var mapped = ConfigurationService.mapFromProcedureRow(
        "56",
        "DCK - Chilliwack Forest District"
    );

    assertEquals("56", mapped.orgUnitNo());
    assertEquals("DCK", mapped.orgUnitCode());
    assertEquals("Chilliwack Forest District", mapped.orgUnitName());
  }

  @Test
  void formatMasterListYearLabelMatchesLegacyFormat() {
    assertEquals("2024/2025", ConfigurationService.formatMasterListYearLabel("2024"));
  }

  @Test
  void toMasterListYearResponseParsesProcedureRow() {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("CODE", "2024");
    row.put("DESCRIPTION", "2024/2025");

    var mapped = ConfigurationService.toMasterListYearResponse(row, true);

    assertEquals("2024", mapped.effectiveYear());
    assertEquals("2024/2025", mapped.label());
    assertTrue(mapped.current());
  }

  @Test
  void getMasterListYearsLoadsFromCodeListsDao() {
    Map<String, Object> current = new LinkedHashMap<>();
    current.put("CODE", "2024");
    current.put("DESCRIPTION", "2024/2025");
    Map<String, Object> prior = new LinkedHashMap<>();
    prior.put("CODE", "2023");
    prior.put("DESCRIPTION", "2023/2024");
    when(codeListRepository.getMasterListYearCode()).thenReturn(List.of(current, prior));

    var years = service.getMasterListYears();

    assertEquals(2, years.size());
    assertTrue(years.get(0).current());
    assertFalse(years.get(1).current());
  }

  @Test
  void getNewMasterListYearsOffersNextYearButKeepsCurrentOnLatestExisting() {
    Map<String, Object> existingCurrent = new LinkedHashMap<>();
    existingCurrent.put("CODE", "2026");
    existingCurrent.put("DESCRIPTION", "2026/2027");
    Map<String, Object> existingPrior = new LinkedHashMap<>();
    existingPrior.put("CODE", "2025");
    existingPrior.put("DESCRIPTION", "2025/2026");
    when(codeListRepository.getMasterListYearCode())
        .thenReturn(List.of(existingCurrent, existingPrior));

    // get_new_masterlist_code = existing years UNION MAX+1 (the synthetic "next" year), ordered DESC.
    Map<String, Object> nextYear = new LinkedHashMap<>();
    nextYear.put("CODE", "2027");
    nextYear.put("DESCRIPTION", "2027/2028");
    when(codeListRepository.getNewMasterListYearCode())
        .thenReturn(List.of(nextYear, existingCurrent, existingPrior));

    var years = service.getNewMasterListYears();

    assertEquals(3, years.size());
    assertEquals("2027", years.get(0).effectiveYear()); // the next year is offered for generation
    assertFalse(years.get(0).current()); // ...but the synthetic next year is never "current"
    assertEquals("2026", years.get(1).effectiveYear());
    assertTrue(years.get(1).current()); // current = latest EXISTING year (default selection)
    assertFalse(years.get(2).current());
  }

  @Test
  void getOrgUnitsLoadsFromCodeListsDao() {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("CODE", "58");
    row.put("DESCRIPTION", "DKA - Kamloops Forest District");
    when(codeListRepository.getDistrictOrgUnitCode()).thenReturn(List.of(row));

    var orgUnits = service.getOrgUnits();

    assertEquals(1, orgUnits.size());
    assertEquals("DKA", orgUnits.get(0).orgUnitCode());
  }

  @Test
  void toProtocolResponseParsesProcedureRow() {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("CODE", "SLB");
    row.put("DESCRIPTION", "Biodiversity");

    var mapped = ConfigurationService.toProtocolResponse(row);

    assertEquals("SLB", mapped.code());
    assertEquals("Biodiversity", mapped.name());
  }

  @Test
  void getProtocolsLoadsFromCodeListsDao() {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("CODE", "RIP");
    row.put("DESCRIPTION", "Riparian");
    when(codeListRepository.getResourceValue()).thenReturn(List.of(row));

    var protocols = service.getProtocols();

    assertEquals(1, protocols.size());
    assertEquals("RIP", protocols.get(0).code());
  }

  @Test
  void getCwdDecayCodesDropsTheUnsampledClassFive() {
    when(codeListRepository.getCwdDecayClassCode()).thenReturn(List.of(
        decayRow("1", "Sound"),
        decayRow("2", "Some decay"),
        decayRow("3", "Advanced decay"),
        decayRow("4", "Well decayed"),
        decayRow("5", "Reference only — not sampled by FREP")));

    var codes = service.getCwdDecayCodes();

    assertEquals(List.of("1", "2", "3", "4"), codes.stream().map(o -> o.code()).toList());
  }

  private static Map<String, Object> decayRow(String code, String description) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("CODE", code);
    row.put("DESCRIPTION", description);
    return row;
  }
}

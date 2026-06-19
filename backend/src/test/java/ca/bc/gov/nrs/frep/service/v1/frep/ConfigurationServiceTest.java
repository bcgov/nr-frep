package ca.bc.gov.nrs.frep.service.v1.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.repository.v1.frep.CodeListRepository;
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
}

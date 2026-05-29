package ca.bc.gov.nrs.frep.service.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.repository.frep.FrepCodeListRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JdbcConfigurationServiceTest {

  @Mock
  private FrepCodeListRepository frepCodeListRepository;

  @InjectMocks
  private JdbcConfigurationService service;

  @Test
  void mapFromProcedureRowParsesLegacyDescriptionFormat() {
    var mapped = JdbcConfigurationService.mapFromProcedureRow(
        "56",
        "DCK - Chilliwack Forest District"
    );

    assertEquals("56", mapped.orgUnitNo());
    assertEquals("DCK", mapped.orgUnitCode());
    assertEquals("Chilliwack Forest District", mapped.orgUnitName());
  }

  @Test
  void getOrgUnitsLoadsFromCodeListsDao() {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("CODE", "58");
    row.put("DESCRIPTION", "DKA - Kamloops Forest District");
    when(frepCodeListRepository.getDistrictOrgUnitCode()).thenReturn(List.of(row));

    var orgUnits = service.getOrgUnits();

    assertEquals(1, orgUnits.size());
    assertEquals("DKA", orgUnits.get(0).orgUnitCode());
  }

  @Test
  void getProtocolsStillReturnsStubDataUntilPlSqlWired() {
    assertEquals(4, service.getProtocols().size());
  }
}

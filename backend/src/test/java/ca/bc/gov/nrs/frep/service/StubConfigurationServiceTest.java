package ca.bc.gov.nrs.frep.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StubConfigurationServiceTest {

  private final StubConfigurationService service = new StubConfigurationService();

  @Test
  void exposesMasterListYearsWithExactlyOneCurrent() {
    var years = service.getMasterListYears();

    assertFalse(years.isEmpty());
    long currentCount = years.stream().filter(year -> year.current()).count();
    assertEquals(1L, currentCount);
  }

  @Test
  void masterListYearLabelMatchesLegacyFormat() {
    var first = service.getMasterListYears().get(0);

    int year = Integer.parseInt(first.effectiveYear());
    assertEquals(year + "/" + (year + 1), first.label());
  }

  @Test
  void exposesOrgUnits() {
    assertFalse(service.getOrgUnits().isEmpty());
    assertTrue(service.getOrgUnits().stream().anyMatch(unit -> "56".equals(unit.orgUnitNo())));
  }

  @Test
  void exposesProtocols() {
    assertEquals(4, service.getProtocols().size());
    assertTrue(service.getProtocols().stream().anyMatch(protocol -> "BIO".equals(protocol.code())));
  }
}

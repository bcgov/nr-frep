package ca.bc.gov.nrs.frep.service.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StubAcceptedSiteServiceTest {

  private final StubAcceptedSiteService service = new StubAcceptedSiteService();

  @Test
  void filtersByYearOrgUnitAndProtocol() {
    var sites = service.findAcceptedSites("2024", "56", "BIO");

    assertEquals(1, sites.size());
    assertTrue(sites.stream().allMatch(site -> "BIO".equals(site.protocolCode())));
  }

  @Test
  void returnsEmptyListWhenNoMatches() {
    assertTrue(service.findAcceptedSites("2024", "99", null).isEmpty());
  }
}

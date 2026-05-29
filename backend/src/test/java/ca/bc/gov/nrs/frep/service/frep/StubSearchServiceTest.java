package ca.bc.gov.nrs.frep.service.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.frep.dto.frep.ChecklistSearchResult;
import ca.bc.gov.nrs.frep.dto.frep.ClientSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class StubSearchServiceTest {

  private final StubSearchService service = new StubSearchService();

  @Test
  void blankFiltersReturnAllChecklists() {
    List<ChecklistSearchResult> all = service.searchChecklists(
        null, null, null, null, null, null, null, null, null);

    assertTrue(all.size() >= 4, "Stub should include the seeded checklists");
  }

  @Test
  void filterByProtocolType() {
    List<ChecklistSearchResult> bio = service.searchChecklists(
        null, null, "BIO", null, null, null, null, null, null);

    assertTrue(bio.stream().allMatch(c -> "BIO".equals(c.protocolCode())));
  }

  @Test
  void orgUnitNumberMapsToDistrictCode() {
    List<ChecklistSearchResult> dck = service.searchChecklists(
        null, "56", null, null, null, null, null, null, null);

    assertTrue(dck.stream().allMatch(c -> "DCK".equals(c.orgUnitCode())));
  }

  @Test
  void clientSearchByNumberSubstring() {
    List<ClientSearchResult> hits = service.searchClients("0001000", null);

    assertEquals(2, hits.size());
  }

  @Test
  void clientSearchByNameCaseInsensitive() {
    List<ClientSearchResult> hits = service.searchClients(null, "tolko");

    assertEquals(1, hits.size());
    assertEquals("TOLKO INDUSTRIES LTD.", hits.get(0).clientName());
  }
}

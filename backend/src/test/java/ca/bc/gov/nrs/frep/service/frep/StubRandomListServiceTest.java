package ca.bc.gov.nrs.frep.service.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.frep.dto.frep.RandomListSiteResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class StubRandomListServiceTest {

  private final StubRandomListService service = new StubRandomListService();

  @Test
  void returnsAllSitesWhenOrgUnitBlank() {
    List<RandomListSiteResponse> sites = service.findRandomList("2024", null);

    assertFalse(sites.isEmpty(), "Stub should return data when org unit not specified");
  }

  @Test
  void filtersBySingleDistrict() {
    List<RandomListSiteResponse> sites = service.findRandomList("2024", "56");

    assertFalse(sites.isEmpty(), "Stub should have at least one DCK site");
    assertTrue(sites.stream().allMatch(s -> "DCK".equals(s.orgUnitCode())));
  }

  @Test
  void treatsEmptyOrgUnitAsAll() {
    List<RandomListSiteResponse> all = service.findRandomList("2024", null);
    List<RandomListSiteResponse> blank = service.findRandomList("2024", "");

    assertEquals(all.size(), blank.size());
  }
}

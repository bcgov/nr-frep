package ca.bc.gov.nrs.frep.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.frep.dto.GenerateMasterListRequest;
import ca.bc.gov.nrs.frep.dto.MasterListAdminResponse;
import org.junit.jupiter.api.Test;

class StubMasterListAdminServiceTest {

  private final StubMasterListAdminService service = new StubMasterListAdminService();

  @Test
  void generatedYearIncludesStats() {
    MasterListAdminResponse response = service.getMasterListCriteria("2024");

    assertTrue(response.generated());
    assertFalse(response.generationStats().isEmpty());
  }

  @Test
  void futureYearHasNoStats() {
    MasterListAdminResponse response = service.getMasterListCriteria("2026");

    assertFalse(response.generated());
    assertTrue(response.generationStats().isEmpty());
  }

  @Test
  void generateFillsInDefaults() {
    MasterListAdminResponse response = service.generateMasterList(
        new GenerateMasterListRequest("2025", null, null, null, null, null, null));

    assertEquals("2025", response.effectiveYear());
    assertTrue(response.generated());
    assertFalse(response.generationStats().isEmpty());
  }

  @Test
  void generateRequiresYear() {
    assertThrows(IllegalArgumentException.class,
        () -> service.generateMasterList(
            new GenerateMasterListRequest("", null, null, null, null, null, null)));
  }
}

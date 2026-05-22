package ca.bc.gov.nrs.frep.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.frep.dto.SiteDetailResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StubSiteDetailServiceTest {

  private final StubSiteDetailService service = new StubSiteDetailService();

  @Test
  void returnsSiteWhenKnown() {
    Optional<SiteDetailResponse> detail = service.findSiteDetail("1001");

    assertTrue(detail.isPresent());
    assertEquals("A12345", detail.get().opening());
    assertFalse(detail.get().resources().isEmpty());
  }

  @Test
  void returnsEmptyWhenUnknown() {
    assertTrue(service.findSiteDetail("does-not-exist").isEmpty());
  }
}

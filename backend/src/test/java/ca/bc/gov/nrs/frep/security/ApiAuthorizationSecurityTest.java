package ca.bc.gov.nrs.frep.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ca.bc.gov.nrs.frep.controller.v1.ChrChecklistApiController;
import ca.bc.gov.nrs.frep.controller.v1.SiteDetailApiController;
import ca.bc.gov.nrs.frep.endpoint.v1.ChrChecklistApiEndpoint;
import ca.bc.gov.nrs.frep.endpoint.v1.SiteDetailApiEndpoint;
import ca.bc.gov.nrs.frep.service.v1.chr.ChrChecklistService;
import ca.bc.gov.nrs.frep.service.v1.frep.SiteDetailService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Verifies the {@code @PreAuthorize} gates on the API endpoints actually enforce — authorization now
 * lives there, not in the services (see {@link FrepAuthorities}). A minimal method-security context
 * proxies the real controllers (with mocked services) so each {@code @PreAuthorize} is evaluated; the
 * authenticated user's authorities are set per-test with {@code @WithMockUser(authorities = ...)}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ApiAuthorizationSecurityTest.MethodSecurityTestConfig.class)
class ApiAuthorizationSecurityTest {

  @Configuration
  @EnableMethodSecurity
  static class MethodSecurityTestConfig {
    @Bean
    SiteDetailService siteDetailService() {
      return Mockito.mock(SiteDetailService.class);
    }

    @Bean
    SiteDetailApiController siteDetailApiController(SiteDetailService service) {
      return new SiteDetailApiController(service);
    }

    @Bean
    ChrChecklistService chrChecklistService() {
      return Mockito.mock(ChrChecklistService.class);
    }

    @Bean
    ChrChecklistApiController chrChecklistApiController(ChrChecklistService service) {
      return new ChrChecklistApiController(service);
    }
  }

  // Injected by interface type: method security proxies the controllers as JDK dynamic proxies
  // (they implement these endpoint interfaces), so the bean is the interface type, not the concrete class.
  @Autowired
  private SiteDetailApiEndpoint siteDetailApi;

  @Autowired
  private ChrChecklistApiEndpoint chrChecklistApi;

  // ── CONTENT_EDIT (writers) ───────────────────────────────────────────

  @Test
  @WithMockUser(authorities = "FREP_VIEW_ONLY")
  void viewOnlyIsForbiddenFromSavingSiteResources() {
    assertThrows(
        AccessDeniedException.class,
        () -> siteDetailApi.saveResources("1", List.of()));
  }

  @Test
  @WithMockUser(authorities = "FREP_EDITOR")
  void editorMaySaveSiteResources() {
    assertDoesNotThrow(() -> siteDetailApi.saveResources("1", List.of()));
  }

  // ── ADMIN-only (checklist activation) ────────────────────────────────

  @Test
  @WithMockUser(authorities = "FREP_EDITOR")
  void editorIsForbiddenFromActivatingChecklist() {
    assertThrows(
        AccessDeniedException.class,
        () -> chrChecklistApi.activateChecklist(1L));
  }

  @Test
  @WithMockUser(authorities = "FREP_ADMIN")
  void adminMayActivateChecklist() {
    assertDoesNotThrow(() -> chrChecklistApi.activateChecklist(1L));
  }
}

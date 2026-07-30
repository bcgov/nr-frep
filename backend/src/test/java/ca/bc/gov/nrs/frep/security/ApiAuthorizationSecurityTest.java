package ca.bc.gov.nrs.frep.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ca.bc.gov.nrs.frep.controller.v1.ChrChecklistApiController;
import ca.bc.gov.nrs.frep.controller.v1.ProtocolChecklistApiController;
import ca.bc.gov.nrs.frep.controller.v1.SiteDetailApiController;
import ca.bc.gov.nrs.frep.endpoint.v1.ChrChecklistApiEndpoint;
import ca.bc.gov.nrs.frep.endpoint.v1.ProtocolChecklistApiEndpoint;
import ca.bc.gov.nrs.frep.endpoint.v1.SiteDetailApiEndpoint;
import ca.bc.gov.nrs.frep.service.v1.ChrChecklistPersistenceService;
import ca.bc.gov.nrs.frep.service.v1.chr.ChrChecklistService;
import ca.bc.gov.nrs.frep.service.v1.frep.ProtocolChecklistService;
import ca.bc.gov.nrs.frep.service.v1.frep.SiteDetailService;
import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
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

    @Bean
    ProtocolChecklistService protocolChecklistService() {
      return Mockito.mock(ProtocolChecklistService.class);
    }

    @Bean
    ProtocolChecklistApiController protocolChecklistApiController(ProtocolChecklistService service) {
      return new ProtocolChecklistApiController(service);
    }

    /** The {@code @auth} bean referenced by {@code FrepAuthorities.CHR_EDIT}. */
    @Bean(name = "auth")
    LoggedUserHelper auth() {
      return new LoggedUserHelper(Mockito.mock(CognitoUserInfoService.class));
    }

    /**
     * The {@code @chrAuth} bean referenced by the per-district CHR write {@code @PreAuthorize}. The
     * save tests use no-id bodies (the coarse {@code canAnyChr} fallback), so the district lookup is
     * never invoked — a hand-rolled stub avoids a Mockito in-context mock (per-district resolution is
     * covered by {@code ChrChecklistAuthorizerTest}).
     */
    @Bean(name = "chrAuth")
    ChrChecklistAuthorizer chrAuth(LoggedUserHelper auth) {
      ChrChecklistPersistenceService persistence =
          new ChrChecklistPersistenceService(null, null) {
            @Override
            public String getChecklistOrgUnitCode(long checklistId) {
              return null;
            }
          };
      return new ChrChecklistAuthorizer(persistence, auth);
    }
  }

  // Injected by interface type: method security proxies the controllers as JDK dynamic proxies
  // (they implement these endpoint interfaces), so the bean is the interface type, not the concrete class.
  @Autowired
  private SiteDetailApiEndpoint siteDetailApi;

  @Autowired
  private ChrChecklistApiEndpoint chrChecklistApi;

  @Autowired
  private ProtocolChecklistApiEndpoint protocolChecklistApi;

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

  // ── @chrAuth (per-district CHR writers) ──────────────────────────────
  // Writes gate on @PreAuthorize("@chrAuth.canEditChecklist(...)"). These no-id save bodies exercise
  // the coarse "any CHR" fallback; per-district resolution is covered by ChrChecklistAuthorizerTest.

  @Test
  @WithMockUser(authorities = "FREP_EDITOR")
  void editorIsForbiddenFromSavingChrChecklist() {
    // FREP_EDITOR is Biodiversity-only now; CHR writes require a CHR district role (or admin).
    assertThrows(
        AccessDeniedException.class,
        () -> chrChecklistApi.saveChecklist(new CheckList()));
  }

  @Test
  @WithMockUser(authorities = "FREP_CHR_EDITOR_DISTRICT_DCK")
  void chrDistrictEditorPassesTheChrWriteGate() {
    assertDoesNotThrow(() -> chrChecklistApi.saveChecklist(new CheckList()));
  }

  @Test
  @WithMockUser(authorities = "FREP_EDITOR")
  void bioOnlyUserIsForbiddenFromReadingChrChecklist() {
    // Reading a CHR checklist also requires CHR access; a Biodiversity-only user is denied.
    assertThrows(AccessDeniedException.class, () -> chrChecklistApi.getChecklist(1L));
  }

  @Test
  @WithMockUser(authorities = "FREP_CHR_EDITOR_DISTRICT_DCK")
  void chrDistrictEditorMayReadChrChecklist() {
    // Read is the coarse "any CHR" gate; the per-district restriction applies only to writes.
    assertDoesNotThrow(() -> chrChecklistApi.getChecklist(1L));
  }

  // ── FREP_EDIT (protocol-checklist / Biodiversity read gate) ──────────

  @Test
  @WithMockUser(authorities = "FREP_CHR_EDITOR_DISTRICT_DCK")
  void chrOnlyUserIsForbiddenFromReadingBioChecklist() {
    // A CHR district editor has no Bio access, so even reading a Bio checklist is denied.
    assertThrows(
        AccessDeniedException.class,
        () -> protocolChecklistApi.getChecklist("bio", "1"));
  }

  @Test
  @WithMockUser(authorities = "FREP_EDITOR")
  void bioEditorMayReadBioChecklist() {
    assertDoesNotThrow(() -> protocolChecklistApi.getChecklist("bio", "1"));
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

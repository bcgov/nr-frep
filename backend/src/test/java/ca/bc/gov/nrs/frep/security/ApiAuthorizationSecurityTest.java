package ca.bc.gov.nrs.frep.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ca.bc.gov.nrs.frep.controller.v1.ChrChecklistApiController;
import ca.bc.gov.nrs.frep.controller.v1.ReportApiController;
import ca.bc.gov.nrs.frep.controller.v1.ProtocolChecklistApiController;
import ca.bc.gov.nrs.frep.controller.v1.SiteDetailApiController;
import ca.bc.gov.nrs.frep.endpoint.v1.ChrChecklistApiEndpoint;
import ca.bc.gov.nrs.frep.endpoint.v1.ProtocolChecklistApiEndpoint;
import ca.bc.gov.nrs.frep.endpoint.v1.ReportApiEndpoint;
import ca.bc.gov.nrs.frep.endpoint.v1.SiteDetailApiEndpoint;
import ca.bc.gov.nrs.frep.service.v1.ChrChecklistPersistenceService;
import ca.bc.gov.nrs.frep.service.v1.chr.ChrChecklistService;
import ca.bc.gov.nrs.frep.service.v1.frep.ProtocolChecklistService;
import ca.bc.gov.nrs.frep.service.v1.frep.SiteDetailService;
import ca.bc.gov.nrs.frep.service.v1.report.CSVReportService;
import ca.bc.gov.nrs.frep.service.v1.report.ExportSlotLimiter;
import ca.bc.gov.nrs.frep.service.v1.report.ReportResult;
import ca.bc.gov.nrs.frep.service.v1.report.ReportService;
import ca.bc.gov.nrs.frep.struct.v1.report.ReportRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
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
     * Returns a canned result for any report so the controller can complete past the
     * {@code @PreAuthorize} gate — these tests assert on authorization, not on rendering.
     */
    @Bean
    ReportService reportService() {
      ReportService service = Mockito.mock(ReportService.class);
      Mockito.when(service.generateReport(Mockito.anyString(), Mockito.any()))
          .thenReturn(new ReportResult(new byte[0], "report.pdf", MediaType.APPLICATION_PDF));
      return service;
    }

    @Bean
    ReportApiController reportApiController(ReportService reportService) {
      return new ReportApiController(
          reportService, Mockito.mock(CSVReportService.class), Mockito.mock(ExportSlotLimiter.class));
    }

    /** The {@code @reportAuth} bean referenced by the CSV data-extract {@code @PreAuthorize}. */
    @Bean(name = "reportAuth")
    ReportAuthorizer reportAuth(LoggedUserHelper auth) {
      return new ReportAuthorizer(auth);
    }

    /** The district every checklist in this test resolves to; see {@link #chrAuth}. */
    static final String CHECKLIST_DISTRICT = "DCK";

    /**
     * The {@code @chrAuth} bean referenced by the per-district CHR {@code @PreAuthorize}. Every
     * checklist resolves to {@link #CHECKLIST_DISTRICT}, so a caller holding
     * {@code FREP_CHR_EDITOR_DISTRICT_DCK} passes and one holding another district is denied. The
     * save tests use no-id bodies (the coarse {@code canAnyChr} fallback) and never reach the lookup.
     * A hand-rolled stub avoids a Mockito in-context mock; per-district resolution itself is covered
     * by {@code ChrChecklistAuthorizerTest}.
     */
    @Bean(name = "chrAuth")
    ChrChecklistAuthorizer chrAuth(LoggedUserHelper auth) {
      ChrChecklistPersistenceService persistence =
          new ChrChecklistPersistenceService(null) {
            @Override
            public String getChecklistOrgUnitCode(long checklistId) {
              return CHECKLIST_DISTRICT;
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

  @Autowired
  private ReportApiEndpoint reportApi;

  /** A filter-less report request; these tests assert only on the authorization gate. */
  private static ReportRequest emptyRequest() {
    return new ReportRequest(
        null, null, null, null, null, null, null, null, null, null, null, null);
  }

  // ── CONTENT_EDIT (writers) ───────────────────────────────────────────

  @Test
  @WithMockUser
  void userWithNoFrepRoleCannotSaveSiteResources() {
    assertThrows(
        AccessDeniedException.class,
        () -> siteDetailApi.saveResources("1", List.of()));
  }

  @Test
  @WithMockUser(authorities = "FREP_EDITOR")
  void editorMaySaveSiteResources() {
    assertDoesNotThrow(() -> siteDetailApi.saveResources("1", List.of()));
  }

  // ── SITE_EDIT (site resources: editors OR any CHR district editor) ───
  // Site records are shared across protocols, so a CHR district editor must be able to edit the
  // sites their checklists hang off — CONTENT_EDIT locked them out of a record they maintain.

  @Test
  @WithMockUser(authorities = "FREP_CHR_EDITOR_DISTRICT_DCK")
  void chrDistrictEditorMaySaveSiteResources() {
    assertDoesNotThrow(() -> siteDetailApi.saveResources("1", List.of()));
  }

  @Test
  @WithMockUser
  void userWithNoFrepRoleStillCannotSaveSiteResources() {
    assertThrows(
        AccessDeniedException.class,
        () -> siteDetailApi.saveResources("1", List.of()));
  }

  @Test
  @WithMockUser(authorities = "FREP_CHR_EDITOR_DISTRICT_DCK")
  void chrDistrictEditorMayCreateATargetedSite() {
    assertDoesNotThrow(() -> siteDetailApi.createTargetedSite(null));
  }

  @Test
  @WithMockUser(authorities = "FREP_CHR_EDITOR_DISTRICT_DCK")
  void chrDistrictEditorMayReadSiteDetail() {
    assertDoesNotThrow(() -> siteDetailApi.getSiteDetail("1"));
  }

  @Test
  @WithMockUser
  void userWithNoFrepRoleCannotReadSiteDetail() {
    // The read was previously ungated. It now matches the rest of the surface — and the
    // protocol-checklist / CHR reads, which exclude a roleless caller too.
    assertThrows(AccessDeniedException.class, () -> siteDetailApi.getSiteDetail("1"));
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
  void chrEditorMayReadAChecklistInTheirOwnDistrict() {
    // The stub checklist resolves to DCK; this caller holds DCK.
    assertDoesNotThrow(() -> chrChecklistApi.getChecklist(1L));
  }

  @Test
  @WithMockUser(authorities = "FREP_CHR_EDITOR_DISTRICT_DPC")
  void chrEditorIsForbiddenFromReadingAnotherDistrictsChecklist() {
    // Reads are district-scoped like writes: holding CHR somewhere is not enough. Previously this
    // passed under the coarse "any CHR" gate, leaving a cross-district read reachable by guessing a
    // (sequential) checklist id — search and accepted-sites are district-filtered, so it was
    // undiscoverable rather than unauthorized.
    assertThrows(AccessDeniedException.class, () -> chrChecklistApi.getChecklist(1L));
  }

  @Test
  @WithMockUser(authorities = "FREP_ADMIN")
  void sysAdminMayReadAnyDistrictsChecklist() {
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

  // ── Jasper template reports (unrestricted) ───────────────────────────
  // The Reports screen shows Checklist Completion Status / Rejection Reason to everyone
  // (pages/Reports/index.tsx), so the endpoint must not gate them on CONTENT_EDIT — it previously
  // did, and a CHR-only user saw both listed and got a 403 on generate.

  @Test
  @WithMockUser
  void userWithNoFrepRoleMayGenerateAJasperReport() {
    assertDoesNotThrow(
        () -> reportApi.generateReport("checklist-completion-status", emptyRequest()));
  }

  @Test
  @WithMockUser(authorities = "FREP_CHR_EDITOR_DISTRICT_DCK")
  void chrOnlyUserMayGenerateAJasperReport() {
    assertDoesNotThrow(
        () -> reportApi.generateReport("checklist-rejection-reason", emptyRequest()));
  }

  // ── CSV data extracts (still gated by @reportAuth) ───────────────────

  @Test
  @WithMockUser
  void userWithNoFrepRoleIsForbiddenFromTheBiodiversityExtract() {
    assertThrows(
        AccessDeniedException.class,
        () -> reportApi.generateCsvReport("biodiversity-extract-block", emptyRequest()));
  }

  @Test
  @WithMockUser(authorities = "FREP_EDITOR")
  void bioOnlyUserIsForbiddenFromTheChrExtract() {
    // Relaxing the Jasper endpoint must not weaken the district gate on the CHR extract.
    assertThrows(
        AccessDeniedException.class,
        () -> reportApi.generateCsvReport("chr-data-extract", emptyRequest()));
  }
}

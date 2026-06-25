package ca.bc.gov.nrs.frep.service.v1.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.exception.ConflictFoundException;
import ca.bc.gov.nrs.frep.exception.InvalidPayloadException;
import ca.bc.gov.nrs.frep.struct.v1.frep.SiteDetailResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.SiteResourceResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.SiteResourceSaveRequest;
import ca.bc.gov.nrs.frep.repository.v1.bean.SiteDetailData;
import ca.bc.gov.nrs.frep.repository.v1.SiteDetailRepository;
import ca.bc.gov.nrs.frep.repository.v1.bean.SiteResourceRow;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SiteDetailServiceTest {

  @Mock
  private SiteDetailRepository siteDetailRepository;

  @Mock
  private LoggedUserHelper loggedUserHelper;

  @InjectMocks
  private SiteDetailService service;

  private static SiteDetailData siteDetailData(List<SiteResourceRow> resources) {
    return new SiteDetailData(
        "1001", "201920", "DCK - Chilliwack Forest District", "43",
        "00010001", "GORMAN BROS. LUMBER LTD.", "A12345", "987654", "987654",
        "L1234", "L1234", "CP-8891", "CB-442", "FSP-101", "2024", resources);
  }

  @Test
  void formatMasterListLabelUsesConfigurationHelper() {
    assertEquals("2024/2025", SiteDetailService.formatMasterListLabel("2024"));
    assertEquals("", SiteDetailService.formatMasterListLabel(""));
  }

  @Test
  void effectiveYearIsFirstFourCharsOfMasterList() {
    assertEquals("2019", SiteDetailService.effectiveYear("201920"));
    assertEquals("99", SiteDetailService.effectiveYear("99"));
    assertNull(SiteDetailService.effectiveYear(null));
  }

  @Test
  void findSiteDetailMapsRepositoryDataAndResolvesChecklistId() {
    when(siteDetailRepository.findSiteDetail("1001")).thenReturn(siteDetailData(
        List.of(new SiteResourceRow(
            "8001", "R", "SLB", "Biodiversity", "ACC", "RDY", "Within stratum", "", "", "3"
        ))
    ));
    when(siteDetailRepository.resolveChecklistId("8001", "SLB")).thenReturn("9001");

    Optional<SiteDetailResponse> detail = service.findSiteDetail("1001");

    assertTrue(detail.isPresent());
    assertEquals("SLB", detail.get().resources().get(0).resourceType());
    assertEquals("9001", detail.get().resources().get(0).checklistId());
    assertEquals("3", detail.get().resources().get(0).revisionCount());
    verify(siteDetailRepository).resolveChecklistId(eq("8001"), eq("SLB"));
  }

  @Test
  void findSiteDetailReturnsEmptyForBlankId() {
    assertTrue(service.findSiteDetail("").isEmpty());
  }

  @Test
  void toResourceResponseSkipsChecklistLookupWithoutStatus() {
    SiteResourceResponse resource = service.toResourceResponse(new SiteResourceRow(
        "8001", "R", "RIP", "Riparian", "REJ", "", "No stream", "NSTR", "Confirmed", "1"
    ));

    assertNull(resource.checklistId());
    assertEquals("NSTR", resource.rejectionReasonCode());
    assertEquals("8001", resource.resourceValueId());
  }

  // Write authorization is now enforced by @PreAuthorize on SiteDetailApiEndpoint, not the service —
  // see ApiAuthorizationSecurityTest for the 403 coverage.

  @Test
  void saveResourcesDerivesContextAndDelegatesThenReloads() {
    when(loggedUserHelper.getLoggedUserId()).thenReturn("IDIR\\u");
    when(siteDetailRepository.findSiteDetail("1001")).thenReturn(siteDetailData(List.of()));
    when(siteDetailRepository.saveResources(
        eq("1001"), eq("987654"), eq("43"), eq("2019"), any(), eq("IDIR\\u"))).thenReturn("1001");

    SiteResourceSaveRequest req = new SiteResourceSaveRequest(
        "8001", "SLB", "ACC", null, null, null, "3");
    SiteDetailResponse response = service.saveResources("1001", List.of(req));

    assertEquals("1001", response.frepSelectedSiteId());
    verify(siteDetailRepository).saveResources(
        eq("1001"), eq("987654"), eq("43"), eq("2019"), any(), eq("IDIR\\u"));
  }

  private static SiteResourceSaveRequest req(
      String status, String reason, String rationale, String comments) {
    return new SiteResourceSaveRequest("8001", "SLB", status, reason, rationale, comments, "3");
  }

  private static void expectInvalid(SiteResourceSaveRequest request) {
    assertThrows(
        InvalidPayloadException.class,
        () -> SiteDetailService.validateResources(List.of(request), siteDetailData(List.of())));
  }

  @Test
  void validateRejectsAcceptedWithReasonOrRationale() {
    expectInvalid(req("ACC", "OTH", null, null));
    expectInvalid(req("ACC", null, "should be blank", null));
  }

  @Test
  void validateRequiresReasonForRejected() {
    expectInvalid(req("REJ", null, null, null));
  }

  @Test
  void validateRequiresRationaleWhenRejectionReasonIsOther() {
    expectInvalid(req("REJ", "OTH", null, null));
  }

  @Test
  void validateRequiresRationaleForTargeted() {
    expectInvalid(req("TAR", null, null, null));
    expectInvalid(req("TAR", "OTH", "any", null)); // reason must be blank for TAR
  }

  @Test
  void validateEnforcesLengthCaps() {
    expectInvalid(req("REJ", "OTH", "x".repeat(51), null));
    expectInvalid(req("ACC", null, null, "y".repeat(2001)));
  }

  @Test
  void validateAcceptsValidRows() {
    SiteDetailService.validateResources(
        List.of(
            req("ACC", null, null, "some comment"),
            req("REJ", "NSTR", null, null),
            req("REJ", "OTH", "needs a reason", null),
            req("TAR", null, "targeted rationale", null)),
        siteDetailData(List.of()));
  }

  @Test
  void saveResourcesRejectedWhenAllResourcesSubmitted() {
    when(siteDetailRepository.findSiteDetail("1001")).thenReturn(siteDetailData(List.of(
        new SiteResourceRow("8001", "R", "SLB", "Biodiversity", "REJ", "SUB", "", "", "", "3"))));

    ConflictFoundException ex = assertThrows(
        ConflictFoundException.class,
        () -> service.saveResources("1001", List.of(req("REJ", "NSTR", null, null))));
    assertTrue(ex.getMessage().contains("submitted"));
    verify(siteDetailRepository, never()).saveResources(any(), any(), any(), any(), any(), any());
  }

  @Test
  void validateAllowsEmptyStatusRows() {
    // Empty status must not raise errors even with no reason/rationale (it just won't be saved).
    SiteDetailService.validateResources(
        List.of(req("", null, null, null)), siteDetailData(List.of()));
  }

  @Test
  void resourcesToPersistDropsEmptyStatusAndSubmittedRows() {
    SiteDetailData current = siteDetailData(List.of(new SiteResourceRow(
        "8002", "R", "RIP", "Riparian", "REJ", "SUB", "", "", "", "1")));
    var toPersist = SiteDetailService.resourcesToPersist(
        List.of(
            new SiteResourceSaveRequest("8001", "SLB", "ACC", null, null, null, "3"), // keep
            new SiteResourceSaveRequest(null, "WAT", "", null, null, null, null), // empty → drop
            new SiteResourceSaveRequest("8002", "RIP", "REJ", "NSTR", null, null, "1")), // submitted → drop
        current);

    assertEquals(1, toPersist.size());
    assertEquals("8001", toPersist.get(0).resourceValueId());
  }

  @Test
  void validateSkipsSubmittedResources() {
    SiteDetailData current = siteDetailData(List.of(new SiteResourceRow(
        "8001", "R", "SLB", "Biodiversity", "REJ", "SUB", "", "", "", "3")));
    // A REJ row missing its reason would normally fail, but it is submitted → skipped.
    SiteDetailService.validateResources(List.of(req("REJ", null, null, null)), current);
  }
}

package ca.bc.gov.nrs.frep.service.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.dto.frep.SiteDetailResponse;
import ca.bc.gov.nrs.frep.dto.frep.SiteResourceResponse;
import ca.bc.gov.nrs.frep.dto.frep.SiteResourceSaveRequest;
import ca.bc.gov.nrs.frep.repository.frep.SiteDetailData;
import ca.bc.gov.nrs.frep.repository.frep.SiteDetailRepository;
import ca.bc.gov.nrs.frep.repository.frep.SiteResourceRow;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

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

  @Test
  void saveResourcesForbiddenWhenUserCannotWrite() {
    when(loggedUserHelper.canWrite()).thenReturn(false);
    assertThrows(ResponseStatusException.class, () -> service.saveResources("1001", List.of()));
  }

  @Test
  void saveResourcesDerivesContextAndDelegatesThenReloads() {
    when(loggedUserHelper.canWrite()).thenReturn(true);
    when(loggedUserHelper.getLoggedUserId()).thenReturn("IDIR\\u");
    when(siteDetailRepository.findSiteDetail("1001")).thenReturn(siteDetailData(List.of()));
    when(siteDetailRepository.saveResources(
        eq("1001"), eq("987654"), eq("43"), eq("2019"), any(), eq("IDIR\\u"))).thenReturn("1001");

    SiteResourceSaveRequest req = new SiteResourceSaveRequest(
        "8001", "SLB", "ACC", null, "ok", null, "3");
    SiteDetailResponse response = service.saveResources("1001", List.of(req));

    assertEquals("1001", response.frepSelectedSiteId());
    verify(siteDetailRepository).saveResources(
        eq("1001"), eq("987654"), eq("43"), eq("2019"), any(), eq("IDIR\\u"));
  }
}

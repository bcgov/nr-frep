package ca.bc.gov.nrs.frep.service.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.dto.frep.SiteDetailResponse;
import ca.bc.gov.nrs.frep.dto.frep.SiteResourceResponse;
import ca.bc.gov.nrs.frep.repository.frep.SiteDetailData;
import ca.bc.gov.nrs.frep.repository.frep.SiteDetailRepository;
import ca.bc.gov.nrs.frep.repository.frep.SiteResourceRow;
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

  @InjectMocks
  private SiteDetailService service;

  @Test
  void formatMasterListLabelUsesConfigurationHelper() {
    assertEquals("2024/2025", SiteDetailService.formatMasterListLabel("2024"));
    assertEquals("", SiteDetailService.formatMasterListLabel(""));
  }

  @Test
  void findSiteDetailMapsRepositoryDataAndResolvesChecklistId() {
    when(siteDetailRepository.findSiteDetail("1001")).thenReturn(new SiteDetailData(
        "1001",
        "2024",
        "DCK - Chilliwack Forest District",
        "00010001",
        "GORMAN BROS. LUMBER LTD.",
        "A12345",
        "987654",
        "987654",
        "L1234",
        "L1234",
        "CP-8891",
        "CB-442",
        "FSP-101",
        "2024",
        List.of(new SiteResourceRow(
            "8001", "R", "SLB", "Biodiversity", "ACC", "RDY", "Within stratum", "", ""
        ))
    ));
    when(siteDetailRepository.resolveChecklistId("8001", "SLB")).thenReturn("9001");

    Optional<SiteDetailResponse> detail = service.findSiteDetail("1001");

    assertTrue(detail.isPresent());
    assertEquals("2024/2025", detail.get().masterList());
    assertEquals("SLB", detail.get().resources().get(0).resourceType());
    assertEquals("9001", detail.get().resources().get(0).checklistId());
    verify(siteDetailRepository).resolveChecklistId(eq("8001"), eq("SLB"));
  }

  @Test
  void findSiteDetailReturnsEmptyForBlankId() {
    assertTrue(service.findSiteDetail("").isEmpty());
  }

  @Test
  void toResourceResponseSkipsChecklistLookupWithoutStatus() {
    SiteResourceResponse resource = service.toResourceResponse(new SiteResourceRow(
        "8001", "R", "RIP", "Riparian", "REJ", "", "No stream", "NSTR", "Confirmed"
    ));

    assertNull(resource.checklistId());
    assertEquals("NSTR", resource.rejectionReasonCode());
  }
}

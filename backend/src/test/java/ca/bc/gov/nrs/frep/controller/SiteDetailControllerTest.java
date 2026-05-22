package ca.bc.gov.nrs.frep.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.frep.dto.SiteDetailResponse;
import ca.bc.gov.nrs.frep.dto.SiteResourceResponse;
import ca.bc.gov.nrs.frep.service.SiteDetailService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SiteDetailControllerTest {

  @Mock
  private SiteDetailService siteDetailService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new SiteDetailController(siteDetailService)).build();
  }

  @Test
  void returnsSiteDetailWhenFound() throws Exception {
    when(siteDetailService.findSiteDetail("1001"))
        .thenReturn(Optional.of(new SiteDetailResponse(
            "1001",
            "2024/2025",
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
            List.of(
                new SiteResourceResponse("BIO", "Biodiversity", "ACC", null,
                    "Site within target stratum", null, "9001", "RDY")
            )
        )));

    mockMvc.perform(get("/api/v1/sites/1001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.frepSelectedSiteId").value("1001"))
        .andExpect(jsonPath("$.opening").value("A12345"))
        .andExpect(jsonPath("$.resources[0].resourceType").value("BIO"));
  }

  @Test
  void returnsNotFoundWhenMissing() throws Exception {
    when(siteDetailService.findSiteDetail("9999")).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/v1/sites/9999"))
        .andExpect(status().isNotFound());
  }
}

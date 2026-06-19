package ca.bc.gov.nrs.frep.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.frep.dto.frep.AcceptedSiteResponse;
import ca.bc.gov.nrs.frep.dto.frep.MapViewResponse;
import ca.bc.gov.nrs.frep.service.frep.AcceptedSiteService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AcceptedSiteControllerTest {

  @Mock
  private AcceptedSiteService acceptedSiteService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new AcceptedSiteController(acceptedSiteService)).build();
  }

  @Test
  void returnsAcceptedSitesWhenRequiredParamsPresent() throws Exception {
    when(acceptedSiteService.findAcceptedSites(eq("2024"), eq("56"), isNull()))
        .thenReturn(List.of(
            new AcceptedSiteResponse(
                "1001", "Bio", "1", false,
                "A12345", "987654", "1234567", "CP-8891", "CB-442",
                "2024-06-15", "RDY", "Ready for evaluation",
                "BIO", "Biodiversity", "2024", "56"
            )
        ));

    mockMvc.perform(get("/api/v1/accepted-sites")
            .param("effectiveYear", "2024")
            .param("orgUnit", "56"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].checklistId").value("1001"))
        .andExpect(jsonPath("$[0].openingNumber").value("A12345"));
  }

  @Test
  void returnsBadRequestWhenRequiredParamsMissing() throws Exception {
    mockMvc.perform(get("/api/v1/accepted-sites")
            .param("effectiveYear", "2024"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void printAcceptedSitesIsNotYetImplemented() throws Exception {
    mockMvc.perform(get("/api/v1/accepted-sites/print"))
        .andExpect(status().isNotImplemented())
        .andExpect(jsonPath("$.feature").value("print-accepted-sites"));
  }

  @Test
  void openingMapViewReturnsComposedUrl() throws Exception {
    when(acceptedSiteService.buildOpeningMapView(eq("987654")))
        .thenReturn(new MapViewResponse("https://viewer?a=1&extent=1,2,3,4"));

    mockMvc.perform(get("/api/v1/openings/987654/map-view"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("https://viewer?a=1&extent=1,2,3,4"));
  }
}

package ca.bc.gov.nrs.frep.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.frep.dto.frep.ChecklistSearchResult;
import ca.bc.gov.nrs.frep.dto.frep.ClientSearchResult;
import ca.bc.gov.nrs.frep.service.frep.SearchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

  @Mock
  private SearchService searchService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new SearchController(searchService)).build();
  }

  @Test
  void returnsChecklistSearchResults() throws Exception {
    when(searchService.searchChecklists(
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(new ChecklistSearchResult(
            "9001", "SLB", "Biodiversity", "2024", "DCK",
            "L1234", "CP-8891", "CB-442", "987654", "00010001",
            "2024-08-12", "IDIR\\JDOE", "RDY", "RDY")));

    mockMvc.perform(get("/api/v1/search/checklists").param("effectiveYear", "2024"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].checklistId").value("9001"))
        .andExpect(jsonPath("$[0].protocolCode").value("SLB"));
  }

  @Test
  void returnsClientSearchResults() throws Exception {
    when(searchService.searchClients(eq("000100"), any(), any(), any(), any()))
        .thenReturn(List.of(new ClientSearchResult(
            "GORMAN", "00010001", "01", "GORMAN BROS. LUMBER LTD.",
            "Head Office", "Kelowna", "ACT")));

    mockMvc.perform(get("/api/v1/search/clients").param("clientNumber", "000100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].clientNumber").value("00010001"))
        .andExpect(jsonPath("$[0].clientLocnName").value("Head Office"));
  }

  @Test
  void exportChecklistsIsNotYetImplemented() throws Exception {
    mockMvc.perform(get("/api/v1/search/checklists/export"))
        .andExpect(status().isNotImplemented())
        .andExpect(jsonPath("$.status").value("NOT_IMPLEMENTED"))
        .andExpect(jsonPath("$.feature").value("export-checklists"));
  }
}

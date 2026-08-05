package ca.bc.gov.nrs.frep.controller.v1;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.frep.struct.v1.frep.ChecklistSearchResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.ClientSearchResult;
import ca.bc.gov.nrs.frep.service.v1.frep.SearchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SearchApiControllerTest {

  @Mock
  private SearchService searchService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new SearchApiController(searchService)).build();
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

  // CSV export lives on ReportController now (GET /api/v1/reports/checklist-search/csv);
  // see ReportControllerTest.
}

package ca.bc.gov.nrs.frep.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.frep.dto.frep.RandomListSiteResponse;
import ca.bc.gov.nrs.frep.service.frep.RandomListService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RandomListControllerTest {

  @Mock
  private RandomListService randomListService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new RandomListController(randomListService)).build();
  }

  @Test
  void returnsRandomListWhenYearProvided() throws Exception {
    when(randomListService.findRandomList(eq("2024"), eq("56")))
        .thenReturn(List.of(
            new RandomListSiteResponse(
                "1001", true, "DCK", "A12345", "987654", "L1234",
                "CP-8891", "CB-442", 24.5, 22.1,
                "2023-09-15", "2024-06-15", List.of("BIO")
            )
        ));

    mockMvc.perform(get("/api/v1/random-list")
            .param("effectiveYear", "2024")
            .param("orgUnit", "56"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].frepSelectedSiteId").value("1001"))
        .andExpect(jsonPath("$[0].openingNumber").value("A12345"))
        .andExpect(jsonPath("$[0].existingChecklists[0]").value("BIO"));
  }

  @Test
  void treatsBlankOrgUnitAsNull() throws Exception {
    when(randomListService.findRandomList(eq("2024"), isNull()))
        .thenReturn(List.of());

    mockMvc.perform(get("/api/v1/random-list")
            .param("effectiveYear", "2024"))
        .andExpect(status().isOk());
  }

  @Test
  void returnsBadRequestWhenYearMissing() throws Exception {
    mockMvc.perform(get("/api/v1/random-list"))
        .andExpect(status().isBadRequest());
  }
}

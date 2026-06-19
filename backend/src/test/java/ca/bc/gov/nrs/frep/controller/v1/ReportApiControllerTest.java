package ca.bc.gov.nrs.frep.controller.v1;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.frep.struct.v1.report.ReportFormat;
import ca.bc.gov.nrs.frep.service.v1.report.CSVReportService;
import ca.bc.gov.nrs.frep.service.v1.report.ReportResult;
import ca.bc.gov.nrs.frep.service.v1.report.ReportService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ReportApiControllerTest {

  @Mock private ReportService reportService;
  @Mock private CSVReportService csvReportService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
        .standaloneSetup(new ReportApiController(reportService, csvReportService))
        .build();
  }

  @Test
  void exportsRandomListCsvAsAttachment() throws Exception {
    byte[] body = "Opening,Org Unit\nA1,DCK\n".getBytes(StandardCharsets.UTF_8);
    when(csvReportService.generateRandomListCsv(eq("2024"), eq("DCK")))
        .thenReturn(new ReportResult(
            body, "frep_100_random_checklist.csv", ReportFormat.CSV.getMediaType()));

    mockMvc.perform(get("/api/v1/reports/random-list/csv")
            .param("effectiveYear", "2024")
            .param("orgUnit", "DCK"))
        .andExpect(status().isOk())
        .andExpect(header().string(
            "Content-Disposition", org.hamcrest.Matchers.containsString("frep_100_random_checklist.csv")))
        .andExpect(content().bytes(body));
  }

  @Test
  void passesNullOrgUnitWhenOmitted() throws Exception {
    when(csvReportService.generateRandomListCsv(eq("2024"), isNull()))
        .thenReturn(new ReportResult(
            "x".getBytes(StandardCharsets.UTF_8),
            "frep_100_random_checklist.csv",
            ReportFormat.CSV.getMediaType()));

    mockMvc.perform(get("/api/v1/reports/random-list/csv").param("effectiveYear", "2024"))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsBlankEffectiveYear() throws Exception {
    mockMvc.perform(get("/api/v1/reports/random-list/csv").param("effectiveYear", " "))
        .andExpect(status().isBadRequest());
  }

  @Test
  void exportsChecklistSearchCsvAsAttachment() throws Exception {
    byte[] body = "CheckList ID,Org Unit\n100,DCK\n".getBytes(StandardCharsets.UTF_8);
    when(csvReportService.generateChecklistSearchCsv(
            eq("2024"), eq("DCK"), isNull(), isNull(), isNull(), isNull(), isNull(),
            isNull(), eq("SUB"), isNull(), isNull(), isNull()))
        .thenReturn(new ReportResult(
            body, "frep_checklist_search_results.csv", ReportFormat.CSV.getMediaType()));

    mockMvc.perform(get("/api/v1/reports/checklist-search/csv")
            .param("effectiveYear", "2024")
            .param("orgUnit", "DCK")
            .param("checklistStatusCode", "SUB"))
        .andExpect(status().isOk())
        .andExpect(header().string(
            "Content-Disposition",
            org.hamcrest.Matchers.containsString("frep_checklist_search_results.csv")))
        .andExpect(content().bytes(body));
  }
}

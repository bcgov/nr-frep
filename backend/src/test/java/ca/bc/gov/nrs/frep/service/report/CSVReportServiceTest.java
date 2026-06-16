package ca.bc.gov.nrs.frep.service.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.dto.frep.ChecklistSearchResult;
import ca.bc.gov.nrs.frep.dto.frep.RandomListResponse;
import ca.bc.gov.nrs.frep.dto.frep.RandomListSiteResponse;
import ca.bc.gov.nrs.frep.dto.frep.RandomListSummaryResponse;
import ca.bc.gov.nrs.frep.dto.report.ReportFormat;
import ca.bc.gov.nrs.frep.service.frep.RandomListService;
import ca.bc.gov.nrs.frep.service.frep.SearchService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/**
 * Covers the FREP100 District Random List CSV export — the legacy "Export to Excel" button, which
 * emitted a CSV. Asserts the 13 legacy columns render in order, with the legacy number formatting
 * and checklist joining.
 */
class CSVReportServiceTest {

  private final RandomListService randomListService = mock(RandomListService.class);
  private final SearchService searchService = mock(SearchService.class);
  private final CSVReportService service =
      new CSVReportService(mock(ReportExtractRepository.class), randomListService, searchService);

  private static RandomListSiteResponse site() {
    return new RandomListSiteResponse(
        "555", true, "DCK", "A12345", "1709463", "TFL47", "CP01", "BLK2",
        12.5, 30.0, null, "2021-03-01", "2021-09-15", "TSA12", List.of("BIO", "RIP"));
  }

  @Test
  void randomListCsvHasLegacyHeaderAndRowInOrder() {
    when(randomListService.findRandomList("2023", "DCK"))
        .thenReturn(new RandomListResponse(
            new RandomListSummaryResponse("Cariboo", 1, 0, 1, 0), List.of(site())));

    ReportResult result = service.generateRandomListCsv("2023", "DCK");
    String csv = new String(result.content(), StandardCharsets.UTF_8);

    assertThat(result.filename()).isEqualTo("frep_100_random_checklist.csv");
    assertThat(result.mediaType()).isEqualTo(ReportFormat.CSV.getMediaType());
    assertThat(csv)
        .startsWith(
            "Opening,Org Unit,Opening ID,Licence,CP,Blk,Exhibit A(ha),"
                + "Harvest Start Date,Harvest Complete Date,Mgmt. Unit,"
                + "Gross Area(ha),Net Area(ha),Existing Checklists");
    // exhibit 12.5 kept; gross 30.0 -> "30"; net null -> empty; checklists joined + quoted (comma).
    assertThat(csv)
        .contains("A12345,DCK,1709463,TFL47,CP01,BLK2,12.5,2021-03-01,2021-09-15,TSA12,30,,\"BIO, RIP\"");
  }

  private static ChecklistSearchResult checklist() {
    return new ChecklistSearchResult(
        "100", "BIO", "Biodiversity", "2024", "DCK", "A111", "CP1", "BLK9", "987",
        "00010001", "2024-05-01", "IDIR\\jdoe", "SUB", "Submitted");
  }

  @Test
  void checklistSearchCsvHasLegacyHeaderAndRowInOrder() {
    when(searchService.searchChecklists(
            ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
            ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
            ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
            ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(List.of(checklist()));

    ReportResult result = service.generateChecklistSearchCsv(
        "2024", "DCK", "BIO", null, null, null, null, null, "SUB", null, null, null);
    String csv = new String(result.content(), StandardCharsets.UTF_8);

    assertThat(result.filename()).isEqualTo("frep_checklist_search_results.csv");
    assertThat(result.mediaType()).isEqualTo(ReportFormat.CSV.getMediaType());
    assertThat(csv)
        .startsWith(
            "CheckList ID,Resource Value,Master List,Opening ID,Org Unit,Checklist Status,"
                + "Licence No.,Cut Block,CP,Client NO.,Evaluation Date,Team Lead");
    // Status uses the code (legacy faithful), resource value uses the protocol name.
    assertThat(csv)
        .contains("100,Biodiversity,2024,987,DCK,SUB,A111,BLK9,CP1,00010001,2024-05-01,IDIR\\jdoe");
  }

  @Test
  void randomListCsvHandlesNoSites() {
    when(randomListService.findRandomList("2023", null))
        .thenReturn(new RandomListResponse(
            new RandomListSummaryResponse(null, 0, 0, 0, 0), List.of()));

    String csv = new String(service.generateRandomListCsv("2023", null).content(), StandardCharsets.UTF_8);

    // Header only, no data rows.
    assertThat(csv.strip()).isEqualTo(
        "Opening,Org Unit,Opening ID,Licence,CP,Blk,Exhibit A(ha),"
            + "Harvest Start Date,Harvest Complete Date,Mgmt. Unit,"
            + "Gross Area(ha),Net Area(ha),Existing Checklists");
  }
}

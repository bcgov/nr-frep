package ca.bc.gov.nrs.frep.service.v1.report;

import ca.bc.gov.nrs.frep.struct.v1.frep.ChecklistSearchResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.RandomListResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.RandomListSiteResponse;
import ca.bc.gov.nrs.frep.struct.v1.report.ReportFormat;
import ca.bc.gov.nrs.frep.struct.v1.report.ReportRequest;
import ca.bc.gov.nrs.frep.exception.ReportGenerationException;
import ca.bc.gov.nrs.frep.service.v1.frep.RandomListService;
import ca.bc.gov.nrs.frep.service.v1.frep.SearchService;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Generates the biodiversity data-extract reports as CSV directly from the extract proc, using
 * Apache Commons CSV — no JasperReports. These are pure tabular data dumps (the legacy JCRS reports
 * were CSV-only), so a dedicated CSV path is simpler and faithful, and they can never be rendered as
 * PDF. Jasper / {@code ReportService} remains the path for any future templated/PDF reports.
 */
@Service
@Profile("oracle")
public class CSVReportService {

  private static final Logger LOG = LoggerFactory.getLogger(CSVReportService.class);

  /** Legacy column headers for the FREP100 random-list CSV (order matches the legacy export). */
  private static final List<String> RANDOM_LIST_COLUMNS = List.of(
      "Opening", "Org Unit", "Opening ID", "Licence", "CP", "Blk", "Exhibit A(ha)",
      "Harvest Start Date", "Harvest Complete Date", "Mgmt. Unit",
      "Gross Area(ha)", "Net Area(ha)", "Existing Checklists");

  /** Legacy column headers for the FREP400 checklist-search CSV (order matches the legacy export). */
  private static final List<String> CHECKLIST_SEARCH_COLUMNS = List.of(
      "CheckList ID", "Resource Value", "Master List", "Opening ID", "Org Unit",
      "Checklist Status", "Licence No.", "Cut Block", "CP", "Client NO.",
      "Evaluation Date", "Team Lead");

  private final ReportExtractRepository extractRepository;
  private final RandomListService randomListService;
  private final SearchService searchService;

  public CSVReportService(
      ReportExtractRepository extractRepository,
      RandomListService randomListService,
      SearchService searchService) {
    this.extractRepository = extractRepository;
    this.randomListService = randomListService;
    this.searchService = searchService;
  }

  /**
   * Runs the report's extract proc and renders the rows as CSV (header row + data rows). Always
   * produces {@code text/csv} — the format on the request is ignored (these reports are CSV-only).
   */
  public ReportResult generateCsv(String reportName, ReportRequest request) {
    ReportDefinition definition = ReportDefinition.fromId(reportName);
    if (definition.getProcName() == null) {
      throw new IllegalArgumentException(
          "Report " + definition.getId() + " is not a CSV data extract");
    }
    ReportExtract extract;
    try {
      extract = extractRepository.runExtract(definition, request);
    } catch (DataAccessException ex) {
      // e.g. the freprpt_bio_* proc isn't granted to the app's Oracle user.
      LOG.error("Report data query failed for [{}]", definition.getId(), ex);
      throw new ReportGenerationException(
          "Failed to query data for report " + definition.getId(), ex);
    }

    byte[] body = toCsv(extract);
    return new ReportResult(
        body, definition.resolveFilename(ReportFormat.CSV), ReportFormat.CSV.getMediaType());
  }

  /**
   * Exports the FREP100 District Random List as CSV — the legacy "Export to Excel" button on
   * {@code frep100RandomList.jsp}, which actually emitted a CSV ({@code frep_100_random_checklist.csv}).
   * Reuses the random-list lookup ({@code FREP_100_DIST_RAND_LIST.GET}) and renders the same 13
   * columns, in the legacy order.
   */
  public ReportResult generateRandomListCsv(String effectiveYear, String orgUnit) {
    RandomListResponse data;
    try {
      data = randomListService.findRandomList(effectiveYear, orgUnit);
    } catch (DataAccessException ex) {
      LOG.error("Random-list export query failed for year [{}] org [{}]", effectiveYear, orgUnit, ex);
      throw new ReportGenerationException("Failed to query the district random list", ex);
    }
    ReportExtract extract = new ReportExtract(
        RANDOM_LIST_COLUMNS, data.sites().stream().map(CSVReportService::toRandomListRow).toList());
    return new ReportResult(
        toCsv(extract), "frep_100_random_checklist.csv", ReportFormat.CSV.getMediaType());
  }

  private static List<String> toRandomListRow(RandomListSiteResponse site) {
    return List.of(
        nullToEmpty(site.openingNumber()),
        nullToEmpty(site.orgUnitCode()),
        nullToEmpty(site.openingId()),
        nullToEmpty(site.licenceId()),
        nullToEmpty(site.cuttingPermitId()),
        nullToEmpty(site.cutBlockId()),
        formatNumber(site.exhibitArea()),
        nullToEmpty(site.disturbanceStartDate()),
        nullToEmpty(site.disturbanceEndDate()),
        nullToEmpty(site.managementUnit()),
        formatNumber(site.grossArea()),
        formatNumber(site.netArea()),
        String.join(", ", site.existingChecklists()));
  }

  /**
   * Exports the FREP400 Checklist Search results as CSV — the legacy "Export to Excel" button on
   * {@code frep400ChecklistSearch.jsp}, which actually emitted a CSV
   * ({@code frep_checklist_search_results.csv}). Re-runs the same search ({@code SearchService}
   * over {@code FREP_400_CHECKLIST_SEARCH}) with the supplied filters and renders the legacy 12
   * columns in order. A &gt;500-row search surfaces as 400 (via {@code SearchService}), same as the
   * on-screen search.
   */
  public ReportResult generateChecklistSearchCsv(
      String effectiveYear,
      String orgUnit,
      String protocolType,
      String licenceId,
      String cuttingPermitId,
      String cutBlockId,
      String openingId,
      String clientNumber,
      String checklistStatusCode,
      String checklistId,
      String evaluationDateFrom,
      String evaluationDateTo) {
    List<ChecklistSearchResult> results = searchService.searchChecklists(
        effectiveYear, orgUnit, protocolType, licenceId, cuttingPermitId, cutBlockId, openingId,
        clientNumber, checklistStatusCode, checklistId, evaluationDateFrom, evaluationDateTo);
    ReportExtract extract = new ReportExtract(
        CHECKLIST_SEARCH_COLUMNS, results.stream().map(CSVReportService::toChecklistSearchRow).toList());
    return new ReportResult(
        toCsv(extract), "frep_checklist_search_results.csv", ReportFormat.CSV.getMediaType());
  }

  private static List<String> toChecklistSearchRow(ChecklistSearchResult row) {
    return List.of(
        nullToEmpty(row.checklistId()),
        nullToEmpty(row.protocolName()),
        nullToEmpty(row.effectiveYear()),
        nullToEmpty(row.openingId()),
        nullToEmpty(row.orgUnitCode()),
        nullToEmpty(row.checklistStatusCode()),
        nullToEmpty(row.licenceId()),
        nullToEmpty(row.cutBlockId()),
        nullToEmpty(row.cuttingPermitId()),
        nullToEmpty(row.clientNumber()),
        nullToEmpty(row.evaluationDate()),
        nullToEmpty(row.evaluatorUserid()));
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  /** Plain decimal (no scientific notation), trailing {@code .0} dropped; blank for null. */
  private static String formatNumber(Double value) {
    return value == null ? "" : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
  }

  private static byte[] toCsv(ReportExtract extract) {
    StringWriter writer = new StringWriter();
    CSVFormat format = CSVFormat.DEFAULT.builder()
        .setHeader(extract.columns().toArray(String[]::new))
        .build();
    try (CSVPrinter printer = new CSVPrinter(writer, format)) {
      for (List<String> row : extract.rows()) {
        printer.printRecord(row);
      }
    } catch (IOException ex) {
      throw new ReportGenerationException("Failed to write CSV", ex);
    }
    return writer.toString().getBytes(StandardCharsets.UTF_8);
  }
}

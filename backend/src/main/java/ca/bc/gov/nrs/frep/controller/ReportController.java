package ca.bc.gov.nrs.frep.controller;

import ca.bc.gov.nrs.frep.dto.report.ReportRequest;
import ca.bc.gov.nrs.frep.exception.ReportGenerationException;
import ca.bc.gov.nrs.frep.exception.ReportNotFoundException;
import ca.bc.gov.nrs.frep.service.report.CSVReportService;
import ca.bc.gov.nrs.frep.service.report.ReportResult;
import ca.bc.gov.nrs.frep.service.report.ReportService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Report-generation endpoint. Mirrors the nr-fspts {@code FspReportController}:
 * POST a request body, get the rendered report back with a
 * {@code Content-Disposition: attachment} header so the browser saves it.
 *
 * <p>Two entry points, routed by path (not by report metadata):</p>
 * <ul>
 *   <li>{@code POST /api/v1/reports/csv/{reportName}} → {@link CSVReportService}: the data-extract
 *       reports, rendered as CSV from the proc cursor via Apache Commons CSV.</li>
 *   <li>{@code POST /api/v1/reports/{reportId}} → {@link ReportService}: JasperReports template
 *       reports (PDF/CSV). No such reports are registered yet.</li>
 * </ul>
 *
 * <p>The filters travel in the JSON body ({@link ReportRequest}). Errors map to {@code ProblemDetail}:
 * not-found → 404, bad params → 400, Jasper/DB failure → 502.</p>
 */
@RestController
@RequestMapping("/api/v1/reports")
@Validated
public class ReportController {

  private final ReportService reportService;
  private final CSVReportService csvReportService;

  public ReportController(ReportService reportService, CSVReportService csvReportService) {
    this.reportService = reportService;
    this.csvReportService = csvReportService;
  }

  /** CSV data-extract reports (Commons CSV, no Jasper). Always returns {@code text/csv}. */
  @PostMapping("/csv/{reportName}")
  public ResponseEntity<byte[]> generateCsvReport(
      @PathVariable("reportName") String reportName,
      @Valid @RequestBody ReportRequest request) {
    return toResponse(csvReportService.generateCsv(reportName, request));
  }

  /**
   * FREP100 District Random List CSV export (legacy "Export to Excel" button, which actually
   * produced a CSV). GET with query filters to match the random-list screen's own load call.
   */
  @GetMapping("/random-list/csv")
  public ResponseEntity<byte[]> exportRandomListCsv(
      @RequestParam("effectiveYear") String effectiveYear,
      @RequestParam(name = "orgUnit", required = false) String orgUnit) {
    if (!StringUtils.hasText(effectiveYear)) {
      throw new IllegalArgumentException("effectiveYear is required");
    }
    return toResponse(
        csvReportService.generateRandomListCsv(
            effectiveYear.trim(), StringUtils.hasText(orgUnit) ? orgUnit.trim() : null));
  }

  /**
   * FREP400 Checklist Search CSV export (legacy "Export to Excel" button, which actually produced a
   * CSV). Same filters as {@code GET /api/v1/search/checklists}; re-runs the search and streams CSV.
   */
  @GetMapping("/checklist-search/csv")
  public ResponseEntity<byte[]> exportChecklistSearchCsv(
      @RequestParam(name = "effectiveYear", required = false) String effectiveYear,
      @RequestParam(name = "orgUnit", required = false) String orgUnit,
      @RequestParam(name = "protocolType", required = false) String protocolType,
      @RequestParam(name = "licenceId", required = false) String licenceId,
      @RequestParam(name = "cuttingPermitId", required = false) String cuttingPermitId,
      @RequestParam(name = "cutBlockId", required = false) String cutBlockId,
      @RequestParam(name = "openingId", required = false) String openingId,
      @RequestParam(name = "clientNumber", required = false) String clientNumber,
      @RequestParam(name = "checklistStatusCode", required = false) String checklistStatusCode,
      @RequestParam(name = "checklistId", required = false) String checklistId,
      @RequestParam(name = "evaluationDateFrom", required = false) String evaluationDateFrom,
      @RequestParam(name = "evaluationDateTo", required = false) String evaluationDateTo) {
    return toResponse(csvReportService.generateChecklistSearchCsv(
        effectiveYear, orgUnit, protocolType, licenceId, cuttingPermitId, cutBlockId, openingId,
        clientNumber, checklistStatusCode, checklistId, evaluationDateFrom, evaluationDateTo));
  }

  /** JasperReports template reports (PDF/CSV). */
  @PostMapping("/{reportId}")
  public ResponseEntity<byte[]> generateReport(
      @PathVariable("reportId") String reportId,
      @Valid @RequestBody ReportRequest request) {
    return toResponse(reportService.generateReport(reportId, request));
  }

  private static ResponseEntity<byte[]> toResponse(ReportResult result) {
    ContentDisposition disposition = ContentDisposition.attachment()
        .filename(result.filename(), StandardCharsets.UTF_8)
        .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .contentType(result.mediaType())
        .body(result.content());
  }

  @ExceptionHandler(ReportNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(ReportNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleBadRequest(IllegalArgumentException exception) {
    return ResponseEntity.badRequest()
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage()));
  }

  @ExceptionHandler(ReportGenerationException.class)
  public ResponseEntity<ProblemDetail> handleReportFailure(ReportGenerationException exception) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage()));
  }
}

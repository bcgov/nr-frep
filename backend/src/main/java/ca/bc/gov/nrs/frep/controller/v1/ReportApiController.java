package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.endpoint.v1.ReportApiEndpoint;
import ca.bc.gov.nrs.frep.struct.v1.report.ReportFormat;
import ca.bc.gov.nrs.frep.struct.v1.report.ReportRequest;
import ca.bc.gov.nrs.frep.service.v1.report.CSVReportService;
import ca.bc.gov.nrs.frep.service.v1.report.ExportSlotLimiter;
import ca.bc.gov.nrs.frep.service.v1.report.ReportResult;
import ca.bc.gov.nrs.frep.service.v1.report.ReportService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Report-generation endpoints. Mappings declared on {@link ReportApiEndpoint}.
 * {@code ReportsController}: the controller implements its endpoint interface and carries no local
 * {@code @ExceptionHandler}s — {@code ReportNotFoundException} / {@code ReportGenerationException} /
 * bad params are mapped centrally by {@code RestExceptionHandler} into {@code ApiError}.
 *
 * <p>Two entry points, routed by path (not by report metadata):</p>
 * <ul>
 *   <li>{@code POST /api/v1/reports/csv/{reportName}} → {@link CSVReportService}: the data-extract
 *       reports, rendered as CSV from the proc cursor via Apache Commons CSV.</li>
 *   <li>{@code POST /api/v1/reports/{reportId}} → {@link ReportService}: JasperReports template
 *       reports (PDF/CSV). No such reports are registered yet.</li>
 * </ul>
 */
@RestController
public class ReportApiController implements ReportApiEndpoint {

  private final ReportService reportService;
  private final CSVReportService csvReportService;
  private final ExportSlotLimiter exportSlotLimiter;

  public ReportApiController(
      ReportService reportService,
      CSVReportService csvReportService,
      ExportSlotLimiter exportSlotLimiter) {
    this.reportService = reportService;
    this.csvReportService = csvReportService;
    this.exportSlotLimiter = exportSlotLimiter;
  }

  @Override
  public ResponseEntity<byte[]> generateCsvReport(String reportName, ReportRequest request) {
    return toResponse(csvReportService.generateCsv(reportName, request));
  }

  @Override
  public ResponseEntity<byte[]> exportRandomListCsv(String effectiveYear, String orgUnit) {
    if (!StringUtils.hasText(effectiveYear)) {
      throw new IllegalArgumentException("effectiveYear is required");
    }
    return toResponse(
        csvReportService.generateRandomListCsv(
            effectiveYear.trim(), StringUtils.hasText(orgUnit) ? orgUnit.trim() : null));
  }

  @Override
  public ResponseEntity<StreamingResponseBody> exportChecklistSearchCsv(
      String effectiveYear, String orgUnit, String protocolType, String licenceId,
      String cuttingPermitId, String cutBlockId, String openingId, String clientNumber,
      String checklistStatusCode, String checklistId, String evaluationDateFrom,
      String evaluationDateTo) {
    if (!exportSlotLimiter.tryAcquire()) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS,
          "Too many exports are in progress. Please try again in a moment.");
    }
    StreamingResponseBody body = outputStream -> {
      try {
        csvReportService.streamChecklistSearchCsv(
            effectiveYear, orgUnit, protocolType, licenceId, cuttingPermitId, cutBlockId, openingId,
            clientNumber, checklistStatusCode, checklistId, evaluationDateFrom, evaluationDateTo,
            outputStream);
      } finally {
        exportSlotLimiter.release();
      }
    };
    ContentDisposition disposition = ContentDisposition.attachment()
        .filename(CSVReportService.CHECKLIST_SEARCH_FILENAME, StandardCharsets.UTF_8)
        .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .contentType(ReportFormat.CSV.getMediaType())
        .body(body);
  }

  @Override
  public ResponseEntity<byte[]> generateReport(String reportId, ReportRequest request) {
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
}

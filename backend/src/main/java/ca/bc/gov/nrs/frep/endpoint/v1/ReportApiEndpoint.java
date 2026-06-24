package ca.bc.gov.nrs.frep.endpoint.v1;

import ca.bc.gov.nrs.frep.security.FrepAuthorities;
import ca.bc.gov.nrs.frep.struct.v1.report.ReportRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * HTTP contract for the report-generation endpoints. Implemented by
 * {@link ca.bc.gov.nrs.frep.controller.v1.ReportApiController}.
 * {@code ReportsEndpoint}: the mappings live on the interface and errors are mapped centrally by
 * {@code RestExceptionHandler} (report-not-found → 404, Jasper/DB failure → 502) into {@code ApiError},
 * rather than via controller-local {@code @ExceptionHandler}s.
 */
@RequestMapping("/api/v1/reports")
public interface ReportApiEndpoint {

  /** CSV data-extract reports (Commons CSV, no Jasper). Always returns {@code text/csv}. */
  @PreAuthorize(FrepAuthorities.CONTENT_EDIT)
  @PostMapping("/csv/{reportName}")
  ResponseEntity<byte[]> generateCsvReport(
      @PathVariable("reportName") String reportName,
      @Valid @RequestBody ReportRequest request);

  /** FREP100 District Random List CSV export (legacy "Export to Excel"). */
  @GetMapping("/random-list/csv")
  ResponseEntity<byte[]> exportRandomListCsv(
      @RequestParam("effectiveYear") String effectiveYear,
      @RequestParam(name = "orgUnit", required = false) String orgUnit);

  /** FREP400 Checklist Search CSV export — streams the result set row-by-row. */
  @GetMapping("/checklist-search/csv")
  ResponseEntity<StreamingResponseBody> exportChecklistSearchCsv(
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
      @RequestParam(name = "evaluationDateTo", required = false) String evaluationDateTo);

  /** JasperReports template reports (PDF/CSV). */
  @PreAuthorize(FrepAuthorities.CONTENT_EDIT)
  @PostMapping("/{reportId}")
  ResponseEntity<byte[]> generateReport(
      @PathVariable("reportId") String reportId,
      @Valid @RequestBody ReportRequest request);
}

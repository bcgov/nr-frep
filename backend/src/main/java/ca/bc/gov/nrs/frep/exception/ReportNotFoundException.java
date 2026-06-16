package ca.bc.gov.nrs.frep.exception;

/**
 * Thrown when a requested report id is not registered in
 * {@code ReportDefinition}, or is registered but has no corresponding JRXML
 * template on the classpath under {@code reports/}. The report controller's
 * exception handler maps this to HTTP 404.
 *
 * <p>Mirrors the nr-fspts report pattern (FSP {@code ReportNotFoundException}).</p>
 */
public class ReportNotFoundException extends RuntimeException {
  public ReportNotFoundException(String reportId) {
    super("Report not found: " + reportId);
  }

  public ReportNotFoundException(String reportId, Throwable cause) {
    super("Report not found: " + reportId, cause);
  }
}

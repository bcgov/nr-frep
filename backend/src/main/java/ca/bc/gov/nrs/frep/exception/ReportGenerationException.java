package ca.bc.gov.nrs.frep.exception;

/**
 * Thrown when JasperReports fails to compile, fill, or export a report (or the
 * fill-time database connection fails). The report controller's exception
 * handler maps this to HTTP 502 — the failing "service" is Jasper / the
 * database rather than something the caller can correct.
 *
 * <p>Mirrors the nr-fspts report pattern (FSP {@code ReportGenerationException}).</p>
 */
public class ReportGenerationException extends RuntimeException {
  public ReportGenerationException(String message) {
    super(message);
  }

  public ReportGenerationException(String message, Throwable cause) {
    super(message, cause);
  }
}

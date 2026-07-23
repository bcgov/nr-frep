package ca.bc.gov.nrs.frep.exception;

/**
 * Thrown when the upstream FAM identity service fails or is unreachable (non-2xx, timeout, connection
 * refused). {@link RestExceptionHandler} maps it to HTTP 502 — the failing "service" is FAM, not
 * something the caller can correct — keeping the service layer free of Spring web types.
 *
 * <p>Mirrors {@link ReportGenerationException} (the other upstream-failure → 502 case).</p>
 */
public class FamServiceException extends RuntimeException {
  public FamServiceException(String message) {
    super(message);
  }

  public FamServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}

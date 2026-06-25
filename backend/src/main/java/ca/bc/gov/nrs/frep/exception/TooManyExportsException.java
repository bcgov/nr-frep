package ca.bc.gov.nrs.frep.exception;

/**
 * Too many concurrent streaming exports are in progress (the {@code ExportSlotLimiter} cap is full).
 * Mapped to HTTP 429 by {@link RestExceptionHandler}.
 */
public class TooManyExportsException extends RuntimeException {

  public TooManyExportsException(String message) {
    super(message);
  }
}

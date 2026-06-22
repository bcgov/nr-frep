package ca.bc.gov.nrs.frep.exception;

import java.io.Serial;

/** Generic unexpected server-side failure. Mapped to HTTP 500 by {@link RestExceptionHandler}. */
public class FrepApiRuntimeException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  public FrepApiRuntimeException(String message) {
    super(message);
  }

  public FrepApiRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }
}

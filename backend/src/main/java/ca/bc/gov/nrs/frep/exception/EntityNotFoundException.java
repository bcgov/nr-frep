package ca.bc.gov.nrs.frep.exception;

import java.io.Serial;

/** A requested resource does not exist. Mapped to HTTP 404 by {@link RestExceptionHandler}. */
public class EntityNotFoundException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  public EntityNotFoundException(String message) {
    super(message);
  }
}

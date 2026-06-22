package ca.bc.gov.nrs.frep.exception;

import java.io.Serial;

/** The authenticated user is not permitted to perform this action. Mapped to HTTP 403. */
public class AccessForbiddenException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  public AccessForbiddenException(String message) {
    super(message);
  }
}

package ca.bc.gov.nrs.frep.exception;

import java.io.Serial;

/** The request is malformed or not valid for the resource's current state. Mapped to HTTP 400. */
public class InvalidParameterException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  public InvalidParameterException(String message) {
    super(message);
  }
}

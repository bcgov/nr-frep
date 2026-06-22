package ca.bc.gov.nrs.frep.exception;

import java.io.Serial;

/** The request conflicts with the current state (e.g. optimistic-lock / concurrent edit). HTTP 409. */
public class ConflictFoundException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  public ConflictFoundException(String message) {
    super(message);
  }
}

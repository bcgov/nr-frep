package ca.bc.gov.nrs.frep.exception;

import ca.bc.gov.nrs.frep.exception.errors.ApiError;
import lombok.Getter;

/**
 * Carries a pre-built {@link ApiError} for a rejected request payload; the handler returns the embedded
 * error verbatim (it already holds the status + message). Mirrors the nr-fspts exception of the same name.
 */
@SuppressWarnings("squid:S1948")
public class InvalidPayloadException extends RuntimeException {

  /**
   * The Error.
   */
  @Getter
  private final ApiError error;

  /**
   * Instantiates a new Invalid payload exception.
   *
   * @param error the error
   */
  public InvalidPayloadException(final ApiError error) {
    super(error.getMessage());
    this.error = error;
  }
}

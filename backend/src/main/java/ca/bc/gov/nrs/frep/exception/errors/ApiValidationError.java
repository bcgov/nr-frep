package ca.bc.gov.nrs.frep.exception.errors;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/** A field- or object-level validation failure carried in {@link ApiError#getSubErrors()}. */
@AllArgsConstructor
@Data
@Builder
@SuppressWarnings("squid:S1948")
public class ApiValidationError implements ApiSubError {

  private String object;
  private String field;
  private Object rejectedValue;
  private String message;

  ApiValidationError(String object, String message) {
    this.object = object;
    this.message = message;
  }
}

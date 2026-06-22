package ca.bc.gov.nrs.frep.exception.errors;

import java.io.Serializable;

/** A single sub-error nested under an {@link ApiError} (e.g. one field validation failure). */
public interface ApiSubError extends Serializable {

  String getField();

  String getMessage();

  String getObject();

  Object getRejectedValue();
}

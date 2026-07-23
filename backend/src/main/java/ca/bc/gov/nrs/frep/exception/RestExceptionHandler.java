package ca.bc.gov.nrs.frep.exception;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import ca.bc.gov.nrs.frep.ChrConstants;
import ca.bc.gov.nrs.frep.exception.errors.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Global exception handler. Translates application and framework exceptions into a consistent
 * {@link ApiError} JSON body so internal failures (stack traces, raw Oracle ORA-xxxxx messages) never
 * reach the UI — the frontend reads {@code message}; {@code debugMessage} carries detail for logs/devs.
 * Modelled on nr-fspts {@code RestExceptionHandler}.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

  private ResponseEntity<Object> buildResponseEntity(ApiError apiError) {
    return new ResponseEntity<>(apiError, apiError.getStatus());
  }

  /** Malformed/unreadable request body. */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String error = "Malformed JSON request";
    log.error("{}", error, ex);
    return buildResponseEntity(new ApiError(BAD_REQUEST, error, ex));
  }

  /** Bean-validation (@Valid) failures — surfaces per-field errors under {@code subErrors}. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ApiError apiError = new ApiError(BAD_REQUEST);
    apiError.setMessage("Validation error");
    apiError.addValidationErrors(ex.getBindingResult().getFieldErrors());
    apiError.addValidationError(ex.getBindingResult().getGlobalErrors());
    log.error("{}", apiError.getMessage(), ex);
    return buildResponseEntity(apiError);
  }

  @ExceptionHandler(EntityNotFoundException.class)
  protected ResponseEntity<Object> handleEntityNotFound(EntityNotFoundException ex) {
    log.info("{}", ex.getMessage());
    return buildResponseEntity(new ApiError(NOT_FOUND, ex.getMessage()));
  }

  @ExceptionHandler({InvalidParameterException.class, IllegalArgumentException.class})
  protected ResponseEntity<Object> handleBadRequest(RuntimeException ex) {
    log.warn("{}", ex.getMessage());
    return buildResponseEntity(new ApiError(BAD_REQUEST, ex.getMessage()));
  }

  @ExceptionHandler(ConflictFoundException.class)
  protected ResponseEntity<Object> handleConflict(ConflictFoundException ex) {
    log.warn("{}", ex.getMessage());
    return buildResponseEntity(new ApiError(CONFLICT, ex.getMessage()));
  }

  @ExceptionHandler(AccessForbiddenException.class)
  protected ResponseEntity<Object> handleForbidden(AccessForbiddenException ex) {
    log.warn("{}", ex.getMessage());
    return buildResponseEntity(new ApiError(FORBIDDEN, ex.getMessage()));
  }

  /** Report id not registered / no JRXML template on the classpath. */
  @ExceptionHandler(ReportNotFoundException.class)
  protected ResponseEntity<Object> handleReportNotFound(ReportNotFoundException ex) {
    log.info("{}", ex.getMessage());
    return buildResponseEntity(new ApiError(NOT_FOUND, ex.getMessage()));
  }

  /** Jasper/DB failure while rendering a report — the failing "service" is upstream, so 502. */
  @ExceptionHandler(ReportGenerationException.class)
  protected ResponseEntity<Object> handleReportGeneration(ReportGenerationException ex) {
    log.error("Report generation failed", ex);
    return buildResponseEntity(new ApiError(BAD_GATEWAY, ex.getMessage(), ex));
  }

  /** Upstream FAM identity service failed/unreachable — not caller-correctable, so 502. */
  @ExceptionHandler(FamServiceException.class)
  protected ResponseEntity<Object> handleFamService(FamServiceException ex) {
    log.error("FAM service unavailable: {}", ex.getMessage(), ex);
    return buildResponseEntity(new ApiError(BAD_GATEWAY, ex.getMessage(), ex));
  }

  /** Concurrent-export limit reached (ExportSlotLimiter) — transient backpressure, so 429. */
  @ExceptionHandler(TooManyExportsException.class)
  protected ResponseEntity<Object> handleTooManyExports(TooManyExportsException ex) {
    log.warn("{}", ex.getMessage());
    return buildResponseEntity(new ApiError(TOO_MANY_REQUESTS, ex.getMessage()));
  }

  /** Returns the pre-built {@link ApiError} carried by the exception (already has status + message). */
  @ExceptionHandler(InvalidPayloadException.class)
  protected ResponseEntity<Object> handleInvalidPayload(InvalidPayloadException ex) {
    log.warn("{}", ex.getMessage());
    return buildResponseEntity(ex.getError());
  }

  /** Honour the status + reason carried by a {@link ResponseStatusException} (e.g. search "narrow"). */
  @ExceptionHandler(ResponseStatusException.class)
  protected ResponseEntity<Object> handleResponseStatus(ResponseStatusException ex) {
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
    log.warn("{} {}", status, message);
    return buildResponseEntity(new ApiError(status, message));
  }

  /**
   * Legacy PL/SQL package raised an application error. The proc message is human-oriented, so it is
   * surfaced (with the help-desk suffix) rather than hidden.
   */
  @ExceptionHandler(StoredProcedureException.class)
  protected ResponseEntity<Object> handleStoredProcedure(StoredProcedureException ex) {
    log.error("Stored procedure error: {}", ex.getMessage(), ex);
    String message = ex.getOracleErrorMessage() != null && !ex.getOracleErrorMessage().isBlank()
        ? ex.getOracleErrorMessage() + " " + ChrConstants.RestMessages.SYS_ERROR_REPORT_TO
        : "Unexpected system error. " + ChrConstants.RestMessages.SYS_ERROR_REPORT_TO;
    return buildResponseEntity(new ApiError(INTERNAL_SERVER_ERROR, message, ex));
  }

  /**
   * Any other data-access failure (e.g. ORA-00942 from a native query, a missing grant). The raw
   * Oracle message is logged but NOT returned — the client gets a generic message instead.
   */
  @ExceptionHandler(DataAccessException.class)
  protected ResponseEntity<Object> handleDataAccess(DataAccessException ex) {
    log.error("Database error", ex);
    ApiError apiError = new ApiError(
        INTERNAL_SERVER_ERROR,
        "A database error occurred. " + ChrConstants.RestMessages.SYS_ERROR_REPORT_TO,
        ex);
    return buildResponseEntity(apiError);
  }

  /** Catch-all. Returns a generic message so internal detail never leaks to the UI. */
  @ExceptionHandler(Exception.class)
  protected ResponseEntity<Object> handleUnexpected(Exception ex) {
    log.error("Unexpected error", ex);
    ApiError apiError = new ApiError(
        INTERNAL_SERVER_ERROR,
        "Unexpected system error. " + ChrConstants.RestMessages.SYS_ERROR_REPORT_TO,
        ex);
    return buildResponseEntity(apiError);
  }
}

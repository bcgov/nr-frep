package ca.bc.gov.nrs.frep.exception;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import ca.bc.gov.nrs.frep.ChrConstants;
import ca.bc.gov.nrs.frep.exception.errors.ApiError;
import java.util.Optional;
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
import org.springframework.transaction.TransactionSystemException;
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

  /**
   * The virus scanner rejected an upload — either a signature was detected or (fail-closed) the
   * scanner could not verify the file. The rejection message is user-facing; surface it at 422.
   */
  @ExceptionHandler(VirusDetectedException.class)
  protected ResponseEntity<Object> handleVirusDetected(VirusDetectedException ex) {
    log.warn("Upload rejected by virus scanner ({}): {}", ex.getRejection().code(), ex.getMessage());
    return buildResponseEntity(new ApiError(UNPROCESSABLE_ENTITY, ex.getMessage()));
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
   * Legacy PL/SQL package raised an application error.
   *
   * <p>Most of these are business rules, not system faults: the proc returns an ApplicationResources
   * message key (e.g. {@code frep.evaluatorinfo.delete.evaluator:1,NAR1}) that the legacy Struts app
   * resolved before display. When {@link LegacyProcMessages} recognises every key, the rule is
   * reported as a 409 in plain English — no help-desk suffix, because there is nothing wrong for the
   * help desk to fix and the evaluator can resolve it themselves. Anything unrecognised is a 500
   * with a generic message; the raw text goes to the log and to {@code debugMessage}, never to the
   * screen.
   */
  @ExceptionHandler(StoredProcedureException.class)
  protected ResponseEntity<Object> handleStoredProcedure(StoredProcedureException ex) {
    // Overflow first, for the same reason as the generic handlers — but by a different route. The
    // legacy packages wrap their inserts in `EXCEPTION WHEN OTHERS`, which swallows the Oracle error
    // and returns it as `p_error_message` text, so an ORA-12899 never surfaces as a SQLException and
    // never reaches those handlers. SQLERRM is embedded in that text, so ColumnOverflow still
    // recognises it. Without this, every over-long value written through a proc — a description, a
    // file name, an attachment type — reported as a 500 with raw Oracle text rather than a 400
    // naming the field.
    return overflowResponse(ex).orElseGet(() -> resolveStoredProcedureRule(ex));
  }

  private ResponseEntity<Object> resolveStoredProcedureRule(StoredProcedureException ex) {
    return LegacyProcMessages.resolve(ex.getOracleErrorMessage())
        .map(message -> {
          log.warn("Stored procedure rule: {} -> {}", ex.getMessage(), message);
          return buildResponseEntity(new ApiError(CONFLICT, message, ex));
        })
        .orElseGet(() -> {
          // The raw p_error_message is logged, not shown. It is internal vocabulary — proc names,
          // message keys, Oracle codes — that tells the evaluator nothing they can act on and leaks
          // schema detail into the UI. Support keeps every bit of it: this log line carries the full
          // string and the stack, and ApiError.debugMessage still holds it in the response for
          // anyone reading the network tab. Same policy as the data-access and catch-all handlers.
          log.error("Stored procedure error: {}", ex.getMessage(), ex);
          return buildResponseEntity(new ApiError(
              INTERNAL_SERVER_ERROR,
              "The request could not be completed. " + ChrConstants.RestMessages.SYS_ERROR_REPORT_TO,
              ex));
        });
  }

  /**
   * A transaction failed to commit. The CHR services write through a raw {@code EntityManager}
   * under a class-level {@code @Transactional}, so a constraint the app never checked — an
   * over-long free-text field is the common one — is not detected until the commit-time flush.
   * {@code JpaTransactionManager} wraps that as {@link TransactionSystemException}, which extends
   * {@code TransactionException} and <b>not</b> {@code DataAccessException}, so before this handler
   * existed these failures fell through to the catch-all and reported "Unexpected system error" —
   * the vaguest message the app has, for one of its most explainable causes.
   */
  @ExceptionHandler(TransactionSystemException.class)
  protected ResponseEntity<Object> handleTransactionSystem(TransactionSystemException ex) {
    return rejectResponse(ex).orElseGet(() -> {
      log.error("Transaction could not be committed", ex);
      return buildResponseEntity(new ApiError(
          INTERNAL_SERVER_ERROR,
          "The changes could not be saved. " + ChrConstants.RestMessages.SYS_ERROR_REPORT_TO,
          ex));
    });
  }

  /**
   * Any other data-access failure (e.g. ORA-00942 from a native query, a missing grant). The raw
   * Oracle message is logged but NOT returned — the client gets a generic message instead. The one
   * exception is a column overflow, which is reported as a field-length problem (see
   * {@link #rejectResponse}).
   */
  @ExceptionHandler(DataAccessException.class)
  protected ResponseEntity<Object> handleDataAccess(DataAccessException ex) {
    return rejectResponse(ex).orElseGet(() -> {
      log.error("Database error", ex);
      return buildResponseEntity(new ApiError(
          INTERNAL_SERVER_ERROR,
          "A database error occurred. " + ChrConstants.RestMessages.SYS_ERROR_REPORT_TO,
          ex));
    });
  }

  /** Catch-all. Returns a generic message so internal detail never leaks to the UI. */
  @ExceptionHandler(Exception.class)
  protected ResponseEntity<Object> handleUnexpected(Exception ex) {
    return rejectResponse(ex).orElseGet(() -> {
      log.error("Unexpected error", ex);
      return buildResponseEntity(new ApiError(
          INTERNAL_SERVER_ERROR,
          "Unexpected system error. " + ChrConstants.RestMessages.SYS_ERROR_REPORT_TO,
          ex));
    });
  }

  /**
   * A 400 naming the over-long field, when the failure is an ORA-12899 anywhere in the chain.
   * Applied at every handler that can see one, because the same overflow arrives by four different
   * routes: a commit-time flush (JPA), a translated JDBC failure, an untranslated one, and — via
   * {@link #handleStoredProcedure} — as proc text, where a legacy {@code EXCEPTION WHEN OTHERS}
   * caught the error and returned {@code SQLERRM} in an out-parameter instead of letting it throw.
   * Only the derived field label and the two lengths are returned — never the raw Oracle text.
   */
  private Optional<ResponseEntity<Object>> overflowResponse(Exception ex) {
    return ColumnOverflow.describe(ex).map(message -> {
      log.warn("Column overflow rejected: {}", message, ex);
      return buildResponseEntity(new ApiError(BAD_REQUEST, message, ex));
    });
  }

  /**
   * A 400 naming what the user has to change, when the failure is one the database can explain: an
   * over-long value (ORA-12899) or a duplicate of something that has to be unique (ORA-00001).
   *
   * <p>Checked at all three generic handlers because the same failure arrives by different routes —
   * commit-time flush, a translated JDBC failure, or an untranslated one. Without this a duplicate
   * feature label surfaced as "Unexpected system error", which named neither the field nor the
   * problem.
   */
  private Optional<ResponseEntity<Object>> rejectResponse(Exception ex) {
    Optional<ResponseEntity<Object>> overflow = overflowResponse(ex);
    if (overflow.isPresent()) {
      return overflow;
    }
    return DuplicateRecord.describe(ex).map(message -> {
      log.warn("Duplicate rejected: {}", message, ex);
      return buildResponseEntity(new ApiError(BAD_REQUEST, message, ex));
    });
  }
}

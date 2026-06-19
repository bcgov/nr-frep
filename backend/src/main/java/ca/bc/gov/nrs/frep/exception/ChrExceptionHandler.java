package ca.bc.gov.nrs.frep.exception;

import ca.bc.gov.nrs.frep.ChrConstants;
import ca.bc.gov.nrs.frep.struct.v1.frep.Error;
import ca.bc.gov.nrs.frep.util.ChrDateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ca.bc.gov.nrs.frep.controller")
public class ChrExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ChrExceptionHandler.class);

  @ExceptionHandler(ChrRestException.class)
  public ResponseEntity<Error> handleChrRestException(ChrRestException ex) {
    Integer status = ChrConstants.restExceptionHttpStatusCodes().get(ex.getType());
    if (status == null) {
      status = 500;
    }
    log.error("CHR REST error: {}", ex.getMessage(), ex);
    return ResponseEntity.status(status)
        .body(new Error(ex.getType(), ex.getCode(), "", "", ex.getMessage()));
  }

  @ExceptionHandler(StoredProcedureException.class)
  public ResponseEntity<Error> handleStoredProcedureException(StoredProcedureException ex) {
    log.error("CHR stored procedure error: {}", ex.getMessage(), ex);
    return ResponseEntity.status(500)
        .body(new Error(
            ChrConstants.RestExceptionTypes.UNEXPECTED,
            "",
            "",
            "",
            ex.getMessage() + " " + ChrConstants.RestMessages.SYS_ERROR_REPORT_TO
        ));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Error> handleUnexpectedException(Exception ex) {
    String message = ex.getMessage();
    if (message == null || message.isBlank()) {
      message = "Unexpected system error. " + ChrConstants.RestMessages.SYS_ERROR_REPORT_TO;
    } else {
      message = message + " " + ChrConstants.RestMessages.SYS_ERROR_REPORT_TO;
    }
    message = message.replace("{dateTime}", ChrDateUtils.getSystemDateTime());
    log.error("CHR unexpected error: {}", message, ex);
    return ResponseEntity.status(500)
        .body(new Error(ChrConstants.RestExceptionTypes.UNEXPECTED, "", "", "", message));
  }
}

package ca.bc.gov.nrs.frep.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.frep.exception.errors.ApiError;
import ca.bc.gov.nrs.frep.service.v1.VirusScanner;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class RestExceptionHandlerTest {

  private final RestExceptionHandler handler = new RestExceptionHandler();

  @Test
  void famServiceExceptionMapsToBadGatewayWithMessage() {
    // Upstream FAM failure → 502 (not 500) and the clean message reaches the client.
    ResponseEntity<Object> response =
        handler.handleFamService(new FamServiceException("Evaluator search is unavailable."));

    assertEquals(HttpStatus.BAD_GATEWAY.value(), response.getStatusCode().value());
    ApiError body = (ApiError) response.getBody();
    assertEquals(HttpStatus.BAD_GATEWAY, body.getStatus());
    assertTrue(body.getMessage().contains("Evaluator search is unavailable."));
  }

  @Test
  void virusDetectedExceptionMapsToUnprocessableEntityWithMessage() {
    // A rejected upload → 422 and the user-facing rejection message reaches the client.
    VirusDetectedException ex = new VirusDetectedException(new VirusScanner.Rejection(
        VirusScanner.CODE_VIRUS_DETECTED, "Upload rejected: a virus was detected (Eicar)."));

    ResponseEntity<Object> response = handler.handleVirusDetected(ex);

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), response.getStatusCode().value());
    ApiError body = (ApiError) response.getBody();
    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, body.getStatus());
    assertTrue(body.getMessage().contains("a virus was detected"));
  }
}

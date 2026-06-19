package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.endpoint.v1.HealthApiEndpoint;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal endpoints for verifying access to the backend. Mappings declared on
 * {@link HealthApiEndpoint}.
 */
@RestController
public class HealthApiController implements HealthApiEndpoint {

  @Override
  public ResponseEntity<Map<String, String>> health() {
    return ResponseEntity.ok(Map.of("status", "UP", "application", "frep"));
  }

  @Override
  public String hello() {
    return "Hello World";
  }
}

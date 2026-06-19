package ca.bc.gov.nrs.frep.endpoint.v1;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** HTTP contract for the unauthenticated liveness endpoints. */
@RequestMapping("/api")
public interface HealthApiEndpoint {

  @GetMapping("/health")
  ResponseEntity<Map<String, String>> health();

  @GetMapping(value = "/hello", produces = MediaType.TEXT_PLAIN_VALUE)
  String hello();
}

package ca.bc.gov.nrs.frep.endpoint.v1;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** HTTP contract for the Oracle connectivity smoke endpoint. */
@RequestMapping("/internal/oracle")
public interface OracleSmokeApiEndpoint {

  @GetMapping("/health")
  ResponseEntity<Map<String, Object>> health();
}

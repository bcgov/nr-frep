package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.endpoint.v1.OracleSmokeApiEndpoint;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smoke endpoint verifying Oracle connectivity via a lightweight query. Mappings declared on
 * {@link OracleSmokeApiEndpoint}.
 */
@RestController
public class OracleSmokeApiController implements OracleSmokeApiEndpoint {

  private final JdbcTemplate jdbcTemplate;

  public OracleSmokeApiController(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public ResponseEntity<Map<String, Object>> health() {
    try {
      Integer result = jdbcTemplate.queryForObject("SELECT 1 FROM DUAL", Integer.class);
      return ResponseEntity.ok(Map.of("status", "UP", "database", "ORACLE", "result", result));
    } catch (Exception e) {
      return ResponseEntity.status(503).body(Map.of("status", "DOWN", "error", e.getMessage()));
    }
  }
}

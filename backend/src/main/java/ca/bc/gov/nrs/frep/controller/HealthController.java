package ca.bc.gov.nrs.frep.controller;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal API endpoints for verifying authenticated access to the backend.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

  @GetMapping("/health")
  public ResponseEntity<Map<String, String>> health() {
    return ResponseEntity.ok(Map.of("status", "UP", "application", "frep"));
  }

  @GetMapping(value = "/hello", produces = MediaType.TEXT_PLAIN_VALUE)
  public String hello() {
    return "Hello World";
  }

}

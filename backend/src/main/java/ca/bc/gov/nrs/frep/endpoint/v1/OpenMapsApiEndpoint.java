package ca.bc.gov.nrs.frep.endpoint.v1;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * HTTP contract for opening spatial geometry. Implemented by
 * {@link ca.bc.gov.nrs.frep.controller.v1.OpenMapsApiController}.
 */
@RequestMapping("/api/v1")
public interface OpenMapsApiEndpoint {

  /** Opening polygon as a GeoJSON FeatureCollection (proxied from the DataBC WFS by OPENING_ID). */
  @GetMapping("/openings/{openingId}/polygon")
  ResponseEntity<JsonNode> getOpeningPolygon(@PathVariable String openingId);
}

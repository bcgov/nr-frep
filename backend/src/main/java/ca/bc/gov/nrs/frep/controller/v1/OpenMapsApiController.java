package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.endpoint.v1.OpenMapsApiEndpoint;
import ca.bc.gov.nrs.frep.service.v1.OpenMapsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opening spatial geometry API — proxies the DataBC WFS for an opening's polygon GeoJSON, used by the
 * in-app Leaflet map. Mappings declared on {@link OpenMapsApiEndpoint}.
 */
@RestController
public class OpenMapsApiController implements OpenMapsApiEndpoint {

  private final OpenMapsService openMapsService;

  public OpenMapsApiController(OpenMapsService openMapsService) {
    this.openMapsService = openMapsService;
  }

  @Override
  public ResponseEntity<JsonNode> getOpeningPolygon(String openingId) {
    if (!StringUtils.isNumeric(openingId)) {
      return ResponseEntity.badRequest().build();
    }
    JsonNode result = openMapsService.getOpeningPolygon(openingId);
    return ResponseEntity.ok(result != null ? result : emptyFeatureCollection());
  }

  /** GeoJSON {@code {"type":"FeatureCollection","features":[]}} for the no-data / WFS-error case. */
  private static ObjectNode emptyFeatureCollection() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "FeatureCollection");
    node.set("features", JsonNodeFactory.instance.arrayNode());
    return node;
  }
}

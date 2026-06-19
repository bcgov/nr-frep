package ca.bc.gov.nrs.frep.service.v1;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Fetches opening polygon geometry as GeoJSON from the BC Gov DataBC public WFS, keyed by
 * {@code OPENING_ID}. Mirrors nr-silva's {@code OpenMapsService}: a {@code GetFeature} request on the
 * RESULTS opening spatial layer, returned straight through to the client as GeoJSON for an in-app
 * Leaflet map. The WFS is public, but a browser-direct call would hit CORS — hence this server-side
 * proxy.
 *
 * <p>Profile-agnostic (no Oracle dependency): the geometry comes from DataBC, not the FREP schema.
 */
@Service
public class OpenMapsService {

  private static final Logger LOG = LoggerFactory.getLogger(OpenMapsService.class);

  /** RESULTS opening spatial layer (DataBC), keyed by OPENING_ID. */
  private static final String OPENING_LAYER = "WHSE_FOREST_VEGETATION.RSLT_OPENING_SVW";

  private final RestClient restClient;

  public OpenMapsService(
      @Value("${frep.open-maps.url:https://openmaps.gov.bc.ca/geo/ows}") String baseUrl,
      @Value("${frep.open-maps.connect-timeout:15s}") Duration connectTimeout,
      @Value("${frep.open-maps.read-timeout:30s}") Duration readTimeout) {

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) connectTimeout.toMillis());
    factory.setReadTimeout((int) readTimeout.toMillis());

    // Base URL pinned from trusted config; the opening id only ever flows into queryParam() on the
    // framework's UriBuilder below (and is validated numeric upstream), so it can't redirect the
    // request host (avoids SSRF).
    this.restClient = RestClient.builder()
        .baseUrl(baseUrl.trim())
        .requestFactory(factory)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  /**
   * Opening polygon + {@code OPENING_ID} as a GeoJSON {@code FeatureCollection}. Returns {@code null}
   * if the WFS call fails (the controller substitutes an empty collection).
   */
  public JsonNode getOpeningPolygon(String openingId) {
    try {
      return restClient
          .get()
          .uri(builder -> builder
              .queryParam("service", "WFS")
              .queryParam("version", "2.0.0")
              .queryParam("request", "GetFeature")
              .queryParam("typeName", OPENING_LAYER)
              .queryParam("outputFormat", "application/json")
              .queryParam("SrsName", "EPSG:4326")
              .queryParam("PROPERTYNAME", "OPENING_ID,GEOMETRY")
              .queryParam("CQL_FILTER", "OPENING_ID=" + openingId)
              .build())
          .retrieve()
          .body(JsonNode.class);
    } catch (RestClientException ex) {
      LOG.warn("DataBC WFS opening-polygon fetch failed for opening {}: {}", openingId, ex.getMessage());
      return null;
    }
  }
}

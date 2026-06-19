package ca.bc.gov.nrs.frep.endpoint.v1;

import ca.bc.gov.nrs.frep.dto.frep.AcceptedSiteResponse;
import ca.bc.gov.nrs.frep.dto.frep.MapViewResponse;
import ca.bc.gov.nrs.frep.dto.frep.NotImplementedResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the FREP200 Accepted Sites API (v1). The request mappings live on this interface;
 * {@link ca.bc.gov.nrs.frep.controller.v1.AcceptedSiteApiController} implements it and delegates to
 * the service layer. Mirrors the nr-fspts {@code endpoint/v1} + {@code controller/v1} split.
 *
 * <p>Legacy equivalent: {@code GET /ext/frep/resource/acceptedSites}.
 */
@RequestMapping("/api/v1")
public interface AcceptedSiteApiEndpoint {

  @GetMapping("/accepted-sites")
  ResponseEntity<List<AcceptedSiteResponse>> getAcceptedSites(
      @RequestParam String effectiveYear,
      @RequestParam String orgUnit,
      @RequestParam(required = false) String protocolType);

  @GetMapping("/accepted-sites/print")
  ResponseEntity<NotImplementedResponse> printAcceptedSites();

  @GetMapping("/openings/{openingId}/map-view")
  ResponseEntity<MapViewResponse> getOpeningMapView(@PathVariable String openingId);
}

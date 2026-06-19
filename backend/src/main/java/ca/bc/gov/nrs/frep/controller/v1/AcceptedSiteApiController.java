package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.AcceptedSiteResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.MapViewResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.NotImplementedResponse;
import ca.bc.gov.nrs.frep.endpoint.v1.AcceptedSiteApiEndpoint;
import ca.bc.gov.nrs.frep.service.v1.frep.AcceptedSiteService;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only accepted sites API (Phase 1 vertical slice). Request mappings are declared on
 * {@link AcceptedSiteApiEndpoint}; this controller implements the contract and delegates to the
 * service.
 *
 * <p>Legacy equivalent: {@code GET /ext/frep/resource/acceptedSites}
 */
@RestController
public class AcceptedSiteApiController implements AcceptedSiteApiEndpoint {

  private final AcceptedSiteService acceptedSiteService;

  public AcceptedSiteApiController(AcceptedSiteService acceptedSiteService) {
    this.acceptedSiteService = acceptedSiteService;
  }

  @Override
  public ResponseEntity<List<AcceptedSiteResponse>> getAcceptedSites(
      String effectiveYear,
      String orgUnit,
      String protocolType
  ) {
    if (StringUtils.isBlank(effectiveYear) || StringUtils.isBlank(orgUnit)) {
      return ResponseEntity.badRequest().build();
    }

    return ResponseEntity.ok(
        acceptedSiteService.findAcceptedSites(
            effectiveYear.trim(),
            orgUnit.trim(),
            protocolType
        )
    );
  }

  /**
   * Render a printable accepted-sites summary (legacy "Print" button on
   * {@code frep200AcceptedSites.jsp}).
   *
   * <p>TODO: implement a server-rendered printable/PDF view. Returns HTTP 501 until then.
   */
  @Override
  public ResponseEntity<NotImplementedResponse> printAcceptedSites() {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(NotImplementedResponse.of(
        "print-accepted-sites",
        "Printable accepted-sites view is not yet available."));
  }

  /**
   * Build the GIS map-view URL for an opening's spatial extent (legacy per-row "Map" link on
   * {@code frep100RandomList.jsp} / {@code frep200AcceptedSites.jsp}, which opened an external map
   * viewer scoped to the opening's bounding box via {@code MapViewAction} / {@code MapViewForm}).
   *
   * <p>Resolves the opening's bounding box from {@code frep_map_bounding_values} and appends it to
   * the configured map-viewer base URL ({@code MAP_VIEWER_URL}). The client opens the returned URL
   * in a new tab.
   */
  @Override
  public ResponseEntity<MapViewResponse> getOpeningMapView(String openingId) {
    if (StringUtils.isBlank(openingId)) {
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok(acceptedSiteService.buildOpeningMapView(openingId.trim()));
  }
}

package ca.bc.gov.nrs.frep.controller;

import ca.bc.gov.nrs.frep.dto.frep.AcceptedSiteResponse;
import ca.bc.gov.nrs.frep.dto.frep.NotImplementedResponse;
import ca.bc.gov.nrs.frep.service.frep.AcceptedSiteService;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only accepted sites API (Phase 1 vertical slice).
 *
 * <p>Legacy equivalent: {@code GET /ext/frep/resource/acceptedSites}
 */
@RestController
@RequestMapping("/api/v1")
public class AcceptedSiteController {

  private final AcceptedSiteService acceptedSiteService;

  public AcceptedSiteController(AcceptedSiteService acceptedSiteService) {
    this.acceptedSiteService = acceptedSiteService;
  }

  @GetMapping("/accepted-sites")
  public ResponseEntity<List<AcceptedSiteResponse>> getAcceptedSites(
      @RequestParam String effectiveYear,
      @RequestParam String orgUnit,
      @RequestParam(required = false) String protocolType
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
  @GetMapping("/accepted-sites/print")
  public ResponseEntity<NotImplementedResponse> printAcceptedSites() {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(NotImplementedResponse.of(
        "print-accepted-sites",
        "Printable accepted-sites view is not yet available."));
  }

  /**
   * Build the GIS map-view URL for an opening's spatial extent (legacy per-row "Map"
   * link on {@code frep100RandomList.jsp} / {@code frep200AcceptedSites.jsp}, which
   * opened an external map viewer scoped to the opening's bounding box).
   *
   * <p>TODO: resolve the opening's bounding box and the configured map-viewer base URL,
   * then return the composed URL. Returns HTTP 501 until then.
   */
  @GetMapping("/openings/{openingId}/map-view")
  public ResponseEntity<NotImplementedResponse> getOpeningMapView(
      @PathVariable String openingId
  ) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(NotImplementedResponse.of(
        "map-view",
        "Map / GIS view for opening " + openingId + " is not yet available."));
  }
}

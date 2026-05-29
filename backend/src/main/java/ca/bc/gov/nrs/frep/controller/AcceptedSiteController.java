package ca.bc.gov.nrs.frep.controller;

import ca.bc.gov.nrs.frep.dto.frep.AcceptedSiteResponse;
import ca.bc.gov.nrs.frep.service.frep.AcceptedSiteService;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}

package ca.bc.gov.nrs.frep.controller;

import ca.bc.gov.nrs.frep.dto.frep.SiteDetailResponse;
import ca.bc.gov.nrs.frep.service.frep.SiteDetailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FREP110 Site Details API.
 *
 * <p>Legacy equivalent: {@code frep110SiteDetailAction} backed by
 * {@code FREP_110_SITE_DETAILS.get(...)}.
 */
@RestController
@RequestMapping("/api/v1")
public class SiteDetailController {

  private final SiteDetailService siteDetailService;

  public SiteDetailController(SiteDetailService siteDetailService) {
    this.siteDetailService = siteDetailService;
  }

  @GetMapping("/sites/{frepSelectedSiteId}")
  public ResponseEntity<SiteDetailResponse> getSiteDetail(
      @PathVariable String frepSelectedSiteId
  ) {
    return siteDetailService.findSiteDetail(frepSelectedSiteId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}

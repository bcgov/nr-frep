package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.dto.frep.SiteDetailResponse;
import ca.bc.gov.nrs.frep.dto.frep.SiteResourceSaveRequest;
import ca.bc.gov.nrs.frep.endpoint.v1.SiteDetailApiEndpoint;
import ca.bc.gov.nrs.frep.service.frep.SiteDetailService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * FREP110 Site Details API. Mappings declared on {@link SiteDetailApiEndpoint}.
 *
 * <p>Legacy equivalent: {@code frep110SiteDetailAction} backed by {@code FREP_110_SITE_DETAILS.get}.
 */
@RestController
public class SiteDetailApiController implements SiteDetailApiEndpoint {

  private final SiteDetailService siteDetailService;

  public SiteDetailApiController(SiteDetailService siteDetailService) {
    this.siteDetailService = siteDetailService;
  }

  @Override
  public ResponseEntity<SiteDetailResponse> getSiteDetail(String frepSelectedSiteId) {
    return siteDetailService.findSiteDetail(frepSelectedSiteId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /** Save resource accept/reject/target evaluations (FREP_110_SITE_DETAILS.SAVE; spawns checklists). */
  @Override
  public ResponseEntity<SiteDetailResponse> saveResources(
      String frepSelectedSiteId, List<SiteResourceSaveRequest> resources) {
    return ResponseEntity.ok(siteDetailService.saveResources(frepSelectedSiteId, resources));
  }
}

package ca.bc.gov.nrs.frep.endpoint.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.SiteDetailResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.SiteResourceSaveRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * HTTP contract for the FREP110 Site Details API. Implemented by
 * {@link ca.bc.gov.nrs.frep.controller.v1.SiteDetailApiController}.
 *
 * <p>Legacy equivalent: {@code frep110SiteDetailAction} backed by {@code FREP_110_SITE_DETAILS.get}.
 */
@RequestMapping("/api/v1")
public interface SiteDetailApiEndpoint {

  @GetMapping("/sites/{frepSelectedSiteId}")
  ResponseEntity<SiteDetailResponse> getSiteDetail(@PathVariable String frepSelectedSiteId);

  @PutMapping("/sites/{frepSelectedSiteId}/resources")
  ResponseEntity<SiteDetailResponse> saveResources(
      @PathVariable String frepSelectedSiteId,
      @RequestBody List<SiteResourceSaveRequest> resources);
}

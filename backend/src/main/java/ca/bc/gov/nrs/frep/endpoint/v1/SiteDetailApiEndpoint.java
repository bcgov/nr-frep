package ca.bc.gov.nrs.frep.endpoint.v1;

import ca.bc.gov.nrs.frep.security.FrepAuthorities;
import ca.bc.gov.nrs.frep.struct.v1.frep.CreateTargetedSiteRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.SiteDetailResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.SiteResourceSaveRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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

  /**
   * Site-detail context for a brand-new targeted opening (FREP200 "Add Target Site"), before any
   * selected site exists. Editor-only — it is the first step of creating a targeted site.
   */
  @PreAuthorize(FrepAuthorities.CONTENT_EDIT)
  @GetMapping("/sites/by-opening")
  ResponseEntity<SiteDetailResponse> getSiteDetailByOpening(
      @RequestParam String openingId,
      @RequestParam String effectiveYear);

  @PreAuthorize(FrepAuthorities.CONTENT_EDIT)
  @PutMapping("/sites/{frepSelectedSiteId}/resources")
  ResponseEntity<SiteDetailResponse> saveResources(
      @PathVariable String frepSelectedSiteId,
      @RequestBody List<SiteResourceSaveRequest> resources);

  /** Create a targeted site for an opening (spawns the checklists). FREP200 "Add Target Site". */
  @PreAuthorize(FrepAuthorities.CONTENT_EDIT)
  @PostMapping("/sites/targeted")
  ResponseEntity<SiteDetailResponse> createTargetedSite(@RequestBody CreateTargetedSiteRequest request);
}

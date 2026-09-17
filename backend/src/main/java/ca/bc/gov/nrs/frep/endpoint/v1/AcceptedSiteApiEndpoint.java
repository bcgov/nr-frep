package ca.bc.gov.nrs.frep.endpoint.v1;

import ca.bc.gov.nrs.frep.security.FrepAuthorities;
import ca.bc.gov.nrs.frep.struct.v1.frep.AcceptedSiteResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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

  /**
   * Which sites come back is decided in {@code AcceptedSiteService}, from the caller — CHR rows only
   * where {@code canChr(districtCode)}, Biodiversity rows only where {@code canEdit()}. That filter
   * runs on the mapped rows, so it is a row scope, not an admission check: without this annotation a
   * caller holding no FREP role still ran the native query and received an empty list.
   * {@link FrepAuthorities#SITE_EDIT} is the widest gate FREP has, so no role that can use the
   * screen loses access.
   */
  @PreAuthorize(FrepAuthorities.SITE_EDIT)
  @GetMapping("/accepted-sites")
  ResponseEntity<List<AcceptedSiteResponse>> getAcceptedSites(
      @RequestParam String effectiveYear,
      @RequestParam String orgUnit,
      @RequestParam(required = false) String protocolType);
}

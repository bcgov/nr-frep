package ca.bc.gov.nrs.frep.service;

import ca.bc.gov.nrs.frep.dto.SiteDetailResponse;
import java.util.Optional;

/**
 * Site Details (FREP110) read API.
 *
 * <p>Legacy equivalent: {@code FREP_110_SITE_DETAILS.get(...)}.
 */
public interface SiteDetailService {

  Optional<SiteDetailResponse> findSiteDetail(String frepSelectedSiteId);
}

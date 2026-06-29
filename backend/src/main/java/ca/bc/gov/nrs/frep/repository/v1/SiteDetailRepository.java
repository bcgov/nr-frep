package ca.bc.gov.nrs.frep.repository.v1;

import ca.bc.gov.nrs.frep.repository.v1.bean.SiteDetailData;
import ca.bc.gov.nrs.frep.struct.v1.frep.SiteResourceSaveRequest;
import java.util.List;

/** Contract for FREP110 Site Details ({@code FREP_110_SITE_DETAILS}). */
public interface SiteDetailRepository {
  SiteDetailData findSiteDetail(String frepSelectedSiteId);

  /**
   * Load the site-detail context for an opening that may not yet be a selected site — used when
   * targeting a brand-new opening. Calls {@code FREP_110_SITE_DETAILS.GET} with a null selected-site
   * id; the proc resolves an existing targeted site for {@code openingId}+{@code masterList} if one
   * exists, otherwise returns the opening header with a blank row per protocol type to evaluate.
   */
  SiteDetailData findSiteDetailByOpening(String openingId, String masterList);

  String saveResources(
      String frepSelectedSiteId,
      String openingId,
      String orgUnitNo,
      String effectiveYear,
      List<SiteResourceSaveRequest> resources,
      String userId);

  String resolveChecklistId(String resourceValueId, String resourceType);
}

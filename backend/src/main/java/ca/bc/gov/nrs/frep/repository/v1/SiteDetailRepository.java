package ca.bc.gov.nrs.frep.repository.v1;

import ca.bc.gov.nrs.frep.repository.v1.bean.SiteDetailData;
import ca.bc.gov.nrs.frep.struct.v1.frep.SiteResourceSaveRequest;
import java.util.List;

/** Contract for FREP110 Site Details ({@code FREP_110_SITE_DETAILS}). */
public interface SiteDetailRepository {
  SiteDetailData findSiteDetail(String frepSelectedSiteId);

  String saveResources(
      String frepSelectedSiteId,
      String openingId,
      String orgUnitNo,
      String effectiveYear,
      List<SiteResourceSaveRequest> resources,
      String userId);

  String resolveChecklistId(String resourceValueId, String resourceType);
}

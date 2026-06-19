package ca.bc.gov.nrs.frep.repository.v1.bean;

import java.util.List;

/**
 * Site header and resource rows returned by {@code FREP_110_SITE_DETAILS.GET}.
 */
public record SiteDetailData(
    String frepSelectedSiteId,
    String masterList,
    String orgUnit,
    String orgUnitNo,
    String client,
    String clientName,
    String opening,
    String openingId,
    String actualOpening,
    String licenceNo,
    String actualLicence,
    String cuttingPermitId,
    String cutBlockId,
    String fspLink,
    String harvestYear,
    List<SiteResourceRow> resources
) {}

package ca.bc.gov.nrs.frep.repository.frep;

import java.util.List;

/**
 * Site header and resource rows returned by {@code FREP_110_SITE_DETAILS.GET}.
 */
public record SiteDetailData(
    String frepSelectedSiteId,
    String effectiveYear,
    String orgUnit,
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

package ca.bc.gov.nrs.frep.dto.frep;

import java.util.List;

/**
 * FREP110 Site Details payload — site header plus resource value evaluations.
 *
 * <p>Legacy equivalent: {@code Frep110SiteDetailBean} + the resource array filled by
 * {@code FREP_110_SITE_DETAILS.get(...)}.
 *
 * @param frepSelectedSiteId  PK of {@code FREP_SELECTED_SITE}
 * @param masterList          formatted master list label, e.g. {@code "2024/2025"}
 * @param orgUnit             district display, e.g. {@code "DCK - Chilliwack Forest District"}
 * @param client              licence holder client number
 * @param clientName          licence holder client name
 * @param opening             formatted opening, e.g. {@code "A12345"}
 * @param openingId           numeric opening id (URL-friendly)
 * @param actualOpening       opening id as found in legacy CBOA (for *-flag in legacy UI)
 * @param licenceNo           formatted licence number
 * @param actualLicence       licence number from CBOA
 * @param cuttingPermitId     CP id
 * @param cutBlockId          cut block id
 * @param fspLink             FSP forest stewardship plan reference
 * @param harvestYear         year of harvest completion, e.g. {@code "2024"}
 * @param resources           one entry per resource value evaluated on the site
 */
public record SiteDetailResponse(
    String frepSelectedSiteId,
    String masterList,
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
    List<SiteResourceResponse> resources
) {}

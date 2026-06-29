package ca.bc.gov.nrs.frep.struct.v1.frep;

/**
 * Request to target an opening for evaluation (FREP200 "Add Target Site"). Carries the opening the
 * user selected from the search results plus the district doing the targeting. Validated by
 * {@code FREP_200_ACCEPTED_SITES.ADD_TARGETED_SITE} before a targeted site is created.
 */
public record TargetedSiteRequest(String openingId, String orgUnit) {}

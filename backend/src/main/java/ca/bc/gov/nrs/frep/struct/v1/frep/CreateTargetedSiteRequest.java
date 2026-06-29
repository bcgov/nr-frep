package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * Create a targeted site for an opening (FREP200 "Add Target Site"). After
 * {@code FREP_200_ACCEPTED_SITES.ADD_TARGETED_SITE} validates the opening, the user marks the
 * resources to evaluate and saves: this carries the opening context plus those resource evaluations,
 * which {@code FREP_110_SITE_DETAILS.SAVE} persists — creating the selected site and spawning the
 * checklists.
 */
public record CreateTargetedSiteRequest(
    String openingId,
    String orgUnit,
    String effectiveYear,
    List<SiteResourceSaveRequest> resources
) {}

package ca.bc.gov.nrs.frep.repository.v1.frep;

import java.util.List;

/**
 * One row returned by {@code FREP_100_DIST_RAND_LIST.GET}.
 *
 * <p>Attribute order matches {@code FREP_RANDOM_LIST_OBJECT} in legacy DDL.
 */
public record RandomListRow(
    String frepSelectedSiteId,
    String isReview,
    String orgUnitCode,
    String openingId,
    String openingNumber,
    String licenceId,
    String cuttingPermitId,
    String cutBlockId,
    String exhibitArea,
    String disturbanceStartDate,
    String disturbanceEndDate,
    String managementUnit,
    String grossArea,
    String netArea,
    List<String> existingChecklists
) {}

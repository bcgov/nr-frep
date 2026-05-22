package ca.bc.gov.nrs.frep.dto;

import java.util.List;

/**
 * Payload for the FREP700 Master List admin screen.
 *
 * <p>Legacy equivalent: data fetched by {@code FREP_700_GEN_MASTER.get}.
 *
 * @param effectiveYear            master-list year being viewed
 * @param minHarvestCompleteDate   start of harvest-complete window for eligibility
 * @param maxHarvestCompleteDate   end of harvest-complete window
 * @param minOpeningGrossAreaHa    minimum cut-block area to consider
 * @param maxSitesPerDistrict      cap on sites generated per district
 * @param resourceEvaluatedInd     which protocols are evaluated, e.g. {@code "BIO,RIP,WAT,CHR"}
 * @param generationComments       admin's notes on the latest generation
 * @param generated                {@code true} when the list has already been generated this year
 * @param generationStats          per-district counts (empty until generated)
 */
public record MasterListAdminResponse(
    String effectiveYear,
    String minHarvestCompleteDate,
    String maxHarvestCompleteDate,
    Double minOpeningGrossAreaHa,
    Integer maxSitesPerDistrict,
    String resourceEvaluatedInd,
    String generationComments,
    boolean generated,
    List<MasterListGenerationStat> generationStats
) {}

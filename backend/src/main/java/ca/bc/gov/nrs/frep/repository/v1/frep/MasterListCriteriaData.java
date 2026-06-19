package ca.bc.gov.nrs.frep.repository.v1.frep;

import java.util.List;

/**
 * Criteria and per-district stats returned by {@code FREP_700_GEN_MASTER.get}.
 */
public record MasterListCriteriaData(
    String maxHarvestCompleteDate,
    String minHarvestCompleteDate,
    Double minOpeningGrossAreaHa,
    Integer maxSitesPerDistrict,
    String generationComments,
    /** Legacy {@code resource_evaluation_ind}: {@code ""} = no list, {@code "N"} = generated (no
     *  evaluations yet), {@code "Y"} = evaluations under way (list effectively locked). */
    String resourceEvaluationInd,
    List<MasterListGenerationRow> generationStats
) {}

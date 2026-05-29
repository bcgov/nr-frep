package ca.bc.gov.nrs.frep.repository.frep;

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
    String resourceEvaluatedInd,
    List<MasterListGenerationRow> generationStats
) {}

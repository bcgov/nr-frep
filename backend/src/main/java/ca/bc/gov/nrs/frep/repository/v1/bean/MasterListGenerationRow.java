package ca.bc.gov.nrs.frep.repository.v1.bean;

/**
 * Row returned by {@code FREP_700_GEN_MASTER.get} via
 * {@code THE.FREP_GENERATION_RESULTS_OBJECT}.
 */
public record MasterListGenerationRow(
    String orgUnitNo,
    String orgUnitDisplay,
    int totalSites,
    int totalAvailableSites,
    String resourceValueInd
) {}

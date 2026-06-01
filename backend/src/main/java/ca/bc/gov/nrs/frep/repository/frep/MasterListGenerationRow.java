package ca.bc.gov.nrs.frep.repository.frep;

/**
 * Row returned by {@code FREP_700_GEN_MASTER.get} via
 * {@code THE.FREP_GENERATION_RESULTS_OBJECT}.
 */
public record MasterListGenerationRow(
    String orgUnitDisplay,
    int totalSites,
    int totalAvailableSites,
    String resourceValueInd
) {}

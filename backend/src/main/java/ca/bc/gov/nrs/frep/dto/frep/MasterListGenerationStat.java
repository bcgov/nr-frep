package ca.bc.gov.nrs.frep.dto.frep;

/**
 * Per-district statistics for a master-list generation run.
 *
 * <p>Legacy equivalent: one row of {@code FREP_GENERATION_RESULTS_VARRAY}.
 *
 * @param orgUnitCode    e.g. {@code "DCK"}
 * @param orgUnitName    e.g. {@code "Chilliwack Forest District"}
 * @param eligibleSites  number of cut blocks that met criteria
 * @param selectedSites  number of cut blocks chosen for the random list
 */
public record MasterListGenerationStat(
    String orgUnitCode,
    String orgUnitName,
    int eligibleSites,
    int selectedSites
) {}

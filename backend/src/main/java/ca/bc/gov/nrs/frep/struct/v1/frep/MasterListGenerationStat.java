package ca.bc.gov.nrs.frep.struct.v1.frep;

/**
 * Per-district statistics for a master-list generation run.
 *
 * <p>Legacy equivalent: one row of {@code FREP_GENERATION_RESULTS_VARRAY}.
 *
 * @param orgUnitNo      org unit number (PK used to regenerate a single district)
 * @param orgUnitCode    e.g. {@code "DCK"}
 * @param orgUnitName    e.g. {@code "Chilliwack Forest District"}
 * @param eligibleSites  number of cut blocks that met criteria
 * @param selectedSites  number of cut blocks chosen for the random list
 * @param resourceValueInd legacy {@code resource_value_ind}: {@code "Y"} when this district already
 *                         has evaluated resources (so it can't be regenerated), else {@code "N"}/{@code ""}
 */
public record MasterListGenerationStat(
    String orgUnitNo,
    String orgUnitCode,
    String orgUnitName,
    int eligibleSites,
    int selectedSites,
    String resourceValueInd
) {}

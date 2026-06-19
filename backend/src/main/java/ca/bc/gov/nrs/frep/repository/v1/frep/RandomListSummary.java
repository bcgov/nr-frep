package ca.bc.gov.nrs.frep.repository.v1.frep;

/**
 * Header summary returned by {@code FREP_100_DIST_RAND_LIST.GET} via its OUT params
 * (accepted-site counts per protocol + the district description). Values are the raw
 * strings from the proc; the service parses the counts.
 *
 * @param orgUnitDesc      district description (param 8, {@code p_org_unit_desc})
 * @param biodiversity     biodiversity accepted-site count (param 4)
 * @param riparian         riparian accepted-site count (param 5)
 * @param water            water accepted-site count (param 6)
 * @param culturalHeritage cultural-heritage accepted-site count (param 7)
 */
public record RandomListSummary(
    String orgUnitDesc,
    String biodiversity,
    String riparian,
    String water,
    String culturalHeritage
) {}

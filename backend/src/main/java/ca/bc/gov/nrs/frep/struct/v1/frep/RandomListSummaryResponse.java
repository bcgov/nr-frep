package ca.bc.gov.nrs.frep.struct.v1.frep;

/**
 * Header summary for the FREP100 District Random List — the "# of Sites Accepted"
 * counts per protocol plus the district description, shown above the table.
 *
 * @param orgUnitDescription district description (blank when "all districts")
 * @param biodiversity       accepted biodiversity sites
 * @param culturalHeritage   accepted cultural-heritage sites
 * @param riparian           accepted riparian sites
 * @param water              accepted water sites
 */
public record RandomListSummaryResponse(
    String orgUnitDescription,
    int biodiversity,
    int culturalHeritage,
    int riparian,
    int water
) {}

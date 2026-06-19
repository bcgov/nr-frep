package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * FREP100 District Random List payload: the header summary (accepted-site counts) plus
 * the selected-site rows.
 */
public record RandomListResponse(
    RandomListSummaryResponse summary,
    List<RandomListSiteResponse> sites
) {}

package ca.bc.gov.nrs.frep.repository.frep;

import java.util.List;

/**
 * Combined result of {@code FREP_100_DIST_RAND_LIST.GET}: the header summary (accepted
 * counts per protocol + district description) plus the selected-site rows.
 */
public record RandomListResult(
    RandomListSummary summary,
    List<RandomListRow> rows
) {}

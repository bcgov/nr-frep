package ca.bc.gov.nrs.frep.dto.frep;

/**
 * Master list (FREP evaluation year) reference entry.
 *
 * <p>Legacy equivalent: {@code FREP_CODE_LISTS.GET_MASTERLIST_YEAR_CODE} returning
 * {@code effective_year} and {@code effective_year || '/' || (effective_year + 1)}.
 *
 * @param effectiveYear  the year code, e.g. {@code "2024"}
 * @param label          formatted label, e.g. {@code "2024/2025"}
 * @param current        {@code true} when this is the active master list cycle
 */
public record MasterListYearResponse(
    String effectiveYear,
    String label,
    boolean current
) {}

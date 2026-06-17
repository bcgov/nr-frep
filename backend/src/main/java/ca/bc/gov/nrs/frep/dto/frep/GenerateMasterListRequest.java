package ca.bc.gov.nrs.frep.dto.frep;

/**
 * Payload accepted by {@code POST /api/v1/admin/master-list/generate}.
 *
 * <p>Legacy equivalent: form fields submitted by {@code frep700GenerateMasterListAction}.
 *
 * @param effectiveYear          master-list year to generate, e.g. {@code "2025"}
 * @param minHarvestCompleteDate optional override of the start of the harvest window
 * @param maxHarvestCompleteDate optional override of the end of the harvest window
 * @param minOpeningGrossAreaHa  minimum block size to consider
 * @param maxSitesPerDistrict    cap on sites generated per district
 * @param comments               admin's notes for this run
 */
public record GenerateMasterListRequest(
    String effectiveYear,
    String minHarvestCompleteDate,
    String maxHarvestCompleteDate,
    Double minOpeningGrossAreaHa,
    Integer maxSitesPerDistrict,
    String comments
) {}

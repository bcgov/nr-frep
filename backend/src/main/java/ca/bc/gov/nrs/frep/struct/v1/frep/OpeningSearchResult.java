package ca.bc.gov.nrs.frep.struct.v1.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One opening returned by the FREP200 "Add Target Site" opening search (legacy SIL56 Opening Tenure
 * Search). Sourced from {@code THE.cut_block_open_admin} joined to {@code THE.opening}; the columns
 * mirror the subset of the legacy SIL56 result the user picks from.
 *
 * <p>{@code openingNumber} is the formatted mapsheet-based number (via {@code THE.frep_formatted_mapsheet});
 * {@code openingId} is the surrogate key carried forward to {@code ADD_TARGETED_SITE} and the eventual
 * site-detail creation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpeningSearchResult(
    String openingId,
    String openingNumber,
    String forestFileId,
    String cuttingPermitId,
    String timberMark,
    String cutBlockId,
    String grossArea,
    String openCategoryCode,
    String openingStatusCode,
    String amendmentInd,
    String licenseeOpeningId,
    String adminDistrictNo
) {}

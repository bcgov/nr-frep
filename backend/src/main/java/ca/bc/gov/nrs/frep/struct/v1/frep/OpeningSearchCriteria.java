package ca.bc.gov.nrs.frep.struct.v1.frep;

/**
 * Filters for the "Add Target Site" opening search, ported from the legacy SIL56 Opening Tenure
 * Search ({@code SIL_56_OPEN_TEN_SRCH_V002.get}). All fields are optional except {@code orgUnit};
 * a non-blank {@code openingId} ignores every other filter (legacy behaviour).
 *
 * <p>{@code openingNumber1..4} are the four mapsheet parts (grid+letter, square, quad+sub-quad,
 * opening number). {@code dateType} selects which date range applies — {@code Disturbance},
 * {@code Regen Delay}, {@code Free Growing} or {@code Update} — and reads the matching date pair.
 * {@code sortBy} is {@code L} (licence) or {@code O}/blank (opening). {@code includeAllP87Ind} = {@code Y}
 * unions in P87 openings from other licensees.
 */
public record OpeningSearchCriteria(
    String orgUnit,
    String clientNumber,
    String clientLocnCode,
    String openingNumber1,
    String openingNumber2,
    String openingNumber3,
    String openingNumber4,
    String forestFileId,
    String openingId,
    String licenseeOpeningId,
    String cuttingPermitId,
    String timberMark,
    String cutBlockId,
    String blockStatusSt,
    String openCategoryCode,
    String openingStatusCode,
    String dateType,
    String distStartDate,
    String distEndDate,
    String dueLateDateFrom,
    String dueLateDateTo,
    String fgDueEarlyDate,
    String fgDueLateDate,
    String updateDateFrom,
    String updateDateTo,
    String includeAllP87Ind,
    String sortBy
) {}

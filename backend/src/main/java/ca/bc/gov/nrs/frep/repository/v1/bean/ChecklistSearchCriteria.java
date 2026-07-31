package ca.bc.gov.nrs.frep.repository.v1.bean;

import java.util.List;

/**
 * Search criteria passed to {@code FREP_400_CHECKLIST_SEARCH} via
 * {@code FREP_CHKLST_SEARCH_VW_OBJECT}.
 *
 * <p>The trailing fields are the server-derived protocol/district visibility for the calling user
 * (not client input): CHR result rows are shown only for {@code allowedChrDistrictCodes} (or all
 * when {@code chrSeeAll}); non-CHR (Biodiversity/etc.) rows only when {@code nonChrVisible}.
 */
public record ChecklistSearchCriteria(
    String effectiveYear,
    String orgUnitNo,
    String protocolTypeCode,
    String licenceId,
    String cuttingPermitId,
    String cutBlockId,
    String openingId,
    String clientNumber,
    String checklistStatusCode,
    String checklistId,
    String evaluationDateFrom,
    String evaluationDateTo,
    boolean chrSeeAll,
    List<String> allowedChrDistrictCodes,
    boolean nonChrVisible
) {}

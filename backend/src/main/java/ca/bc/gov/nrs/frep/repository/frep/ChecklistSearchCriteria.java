package ca.bc.gov.nrs.frep.repository.frep;

/**
 * Search criteria passed to {@code FREP_400_CHECKLIST_SEARCH} via
 * {@code FREP_CHKLST_SEARCH_VW_OBJECT}.
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
    String checklistStatusCode
) {}

package ca.bc.gov.nrs.frep.repository.v1.frep;

/**
 * One row returned by {@code FREP_400_CHECKLIST_SEARCH}.
 *
 * <p>Attribute order matches {@code FREP_CHKLST_SEARCH_VW_OBJECT} in legacy DDL.
 */
public record ChecklistSearchRow(
    String checklistId,
    String protocolCode,
    String protocolName,
    String effectiveYear,
    String orgUnitCode,
    String licenceId,
    String cuttingPermitId,
    String cutBlockId,
    String openingId,
    String clientNumber,
    String evaluationDate,
    String evaluatorUserid,
    String checklistStatusCode
) {}

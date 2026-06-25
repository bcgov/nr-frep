package ca.bc.gov.nrs.frep.struct.v1.frep;

/**
 * One row of the FREP400 checklist search results.
 *
 * <p>Legacy equivalent: {@code FREP_CHKLST_SEARCH_VW_OBJECT}.
 *
 * @param checklistId         PK of {@code FREP_*_CHECKLIST}
 * @param protocolCode        {@code BIO} / {@code RIP} / {@code WAT} / {@code CHR}
 * @param protocolName        display name for the protocol
 * @param effectiveYear       master-list year
 * @param orgUnitCode         district code
 * @param licenceId           tenure / forest file id
 * @param cuttingPermitId     cutting permit
 * @param cutBlockId          cut block
 * @param openingId           opening id
 * @param clientNumber        client number (8-digit)
 * @param evaluationDate      date the checklist was evaluated
 * @param evaluatorUserid     IDIR of evaluator
 * @param evaluatorName       evaluator's display name when they have FREP access (via FAM), else the userid
 * @param checklistStatusCode {@code ACT} / {@code SUB} / {@code RDO}
 * @param checklistStatus     human-readable status
 */
public record ChecklistSearchResult(
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
    String evaluatorName,
    String checklistStatusCode,
    String checklistStatus
) {}

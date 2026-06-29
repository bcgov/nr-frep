package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * Checklist Administration tab (legacy FREP301 / {@code checklistCostResource}) — evaluation date,
 * site access / cost data, and the evaluation team. Shared across protocols.
 *
 * <p>The identity fields ({@code selectedSiteId}, {@code resourceValueId}, {@code resourceValueType},
 * {@code statusCode}) and the two revision counts are read back so a save can round-trip them.
 * Team membership is read-only here; adding/removing evaluators is a separate legacy flow.
 *
 * <p>Legacy source: {@code FREP_CHECKLIST_COST_RESOURCES.GET/SAVE}.
 */
public record AdministrationData(
    String checklistId,
    String selectedSiteId,
    String resourceValueId,
    String resourceValueType,
    String statusCode,
    String evaluationDate,
    String siteAccessCode,
    String blockAccessTime,
    String hoursOnBlock,
    String peopleOnBlock,
    String additionalComments,
    String teamLeadNameId,
    String teamLeadName,
    String teamLeadRevisionCount,
    String revisionCount,
    String revisionCountAccess,
    List<EvaluatorRow> teamMembers
) {}

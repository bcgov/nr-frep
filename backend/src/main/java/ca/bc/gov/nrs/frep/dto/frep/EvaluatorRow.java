package ca.bc.gov.nrs.frep.dto.frep;

/**
 * A member of a checklist's evaluation team (FREP301 Administration). The team lead is the member
 * with {@code teamLeadInd = 'Y'}.
 *
 * <p>Legacy source: the team-member REF CURSOR of {@code FREP_CHECKLIST_COST_RESOURCES.GET}.
 */
public record EvaluatorRow(
    String evaluatorUserid,
    String frepResourceValueId,
    String teamLeadInd,
    String evaluatorDescription,
    String revisionCount
) {}

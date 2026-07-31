package ca.bc.gov.nrs.frep.struct.v1.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Typed, editable view of a biodiversity (SLB) checklist Opening screen (FREP screen 210).
 *
 * <p>The editable field names/order mirror the {@code FREP_210_BIO_OPENING.SAVE} parameters. All
 * values are Strings (the legacy DC posts indicators as {@code "Y"}/{@code "N"} and codes as-is).
 * {@code revisionCount} is the optimistic-lock token round-tripped through the save proc.
 *
 * <p>{@code grossArea}/{@code netArea}/{@code harvestDate} are read-only RESULTS reference fields
 * (legacy reads them in {@code FREP_210_BIO_OPENING.GET} from {@code frep_selected_site} and
 * {@code cut_block_open_admin}); the SAVE proc never accepts them, so they are display-only and are
 * not sent back on save.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BiodiversityOpening(
    String checklistId,
    String resourceValueId,
    String statusCode,
    String frepWtpOverride,
    String locationDescription,
    String patchReservesOnBlock,
    String patchReservesSampled,
    String innovativePracticeInd,
    String innovativePracticesComment,
    String invasivePlantIndicator,
    String invasivePlantComment,
    String frepSiteEvaluationCode,
    String evaluatorOpinionComment,
    String evaluationDate,
    String revisionCount,
    String grossArea,
    String netArea,
    String harvestDate,
    // Evaluator = the evaluation-team lead (biodiversity_evaluator_name where team_lead_ind='Y'), the
    // Biodiversity analogue of CHR's "Assessed by". teamLeadNameId is the IDIR userid; teamLeadName is
    // the FAM-resolved display name; teamLeadRevisionCount is the evaluator row's own lock token.
    String teamLeadNameId,
    String teamLeadName,
    String teamLeadRevisionCount
) {

  /** Returns a copy with the id/revision the SAVE proc echoes back. */
  public BiodiversityOpening withIdentity(String newChecklistId, String newRevisionCount) {
    return new BiodiversityOpening(
        newChecklistId, resourceValueId, statusCode, frepWtpOverride, locationDescription,
        patchReservesOnBlock, patchReservesSampled, innovativePracticeInd, innovativePracticesComment,
        invasivePlantIndicator, invasivePlantComment, frepSiteEvaluationCode, evaluatorOpinionComment,
        evaluationDate, newRevisionCount, grossArea, netArea, harvestDate,
        teamLeadNameId, teamLeadName, teamLeadRevisionCount
    );
  }

  /** Returns a copy with the read-only RESULTS reference fields populated. */
  public BiodiversityOpening withResultsRefs(String newGrossArea, String newNetArea,
      String newHarvestDate) {
    return new BiodiversityOpening(
        checklistId, resourceValueId, statusCode, frepWtpOverride, locationDescription,
        patchReservesOnBlock, patchReservesSampled, innovativePracticeInd, innovativePracticesComment,
        invasivePlantIndicator, invasivePlantComment, frepSiteEvaluationCode, evaluatorOpinionComment,
        evaluationDate, revisionCount, newGrossArea, newNetArea, newHarvestDate,
        teamLeadNameId, teamLeadName, teamLeadRevisionCount
    );
  }

  /** Returns a copy with the evaluator (team lead) userid + resolved name + revision populated. */
  public BiodiversityOpening withTeamLead(String userId, String name, String revision) {
    return new BiodiversityOpening(
        checklistId, resourceValueId, statusCode, frepWtpOverride, locationDescription,
        patchReservesOnBlock, patchReservesSampled, innovativePracticeInd, innovativePracticesComment,
        invasivePlantIndicator, invasivePlantComment, frepSiteEvaluationCode, evaluatorOpinionComment,
        evaluationDate, revisionCount, grossArea, netArea, harvestDate, userId, name, revision
    );
  }
}

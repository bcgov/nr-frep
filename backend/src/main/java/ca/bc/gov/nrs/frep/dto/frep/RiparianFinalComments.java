package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Typed, editable view of the riparian final-comments screen (FREP screen 235). Field order mirrors
 * the {@code FREP_235_FINAL_CMTS.save} parameters. {@code revisionCount} is the optimistic-lock
 * token round-tripped through the save proc.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RiparianFinalComments(
    String checklistId,
    String conclusionComment,
    String specificImpactComment,
    String assessmentProblemsComment,
    String mapLegibilityComment,
    String leaveStripAssessmentComment,
    String checklistRecommComment,
    String revisionCount
) {

  public RiparianFinalComments withIdentity(String newChecklistId, String newRevisionCount) {
    return new RiparianFinalComments(
        newChecklistId, conclusionComment, specificImpactComment, assessmentProblemsComment,
        mapLegibilityComment, leaveStripAssessmentComment, checklistRecommComment, newRevisionCount
    );
  }

  public RiparianFinalComments withChecklist(String newChecklistId) {
    return withIdentity(newChecklistId, revisionCount);
  }
}

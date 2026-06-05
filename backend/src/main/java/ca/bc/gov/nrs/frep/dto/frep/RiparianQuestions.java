package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Typed, editable riparian questions screen (FREP 233): question answers (saved via
 * {@code save_responses}) and the no-answer impacts (saved via {@code save_no_answers}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RiparianQuestions(
    String checklistId,
    List<RipQuestionRow> questions,
    List<RipNoAnswerRow> noAnswers
) {

  public RiparianQuestions withChecklist(String newChecklistId) {
    return new RiparianQuestions(newChecklistId, questions, noAnswers);
  }
}

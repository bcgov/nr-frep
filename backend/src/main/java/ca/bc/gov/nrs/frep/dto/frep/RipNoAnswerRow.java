package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One riparian "no answer" impact (mirrors {@code THE.FREP_NO_ANSWERS_OBJECT}, 11 attrs). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RipNoAnswerRow(
    String answerImpactId,
    String checklistId,
    String checklistQuestionId,
    String questionNo,
    String answerImpactType,
    String answerImpactDesc,
    String sortOrder,
    String answerInd,
    String revisionCount,
    String entryUserid,
    String updateUserid
) {}

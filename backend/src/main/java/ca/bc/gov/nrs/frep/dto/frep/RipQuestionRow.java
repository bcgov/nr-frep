package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One riparian question/answer (mirrors {@code THE.FREP_QUESTIONS_OBJECT}, 14 attrs). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RipQuestionRow(
    String checklistId,
    String checklistQuestionId,
    String questionNo,
    String question,
    String chanMorphologyCode,
    String applicableInd,
    String morphologyDesc,
    String questionType,
    String questionDesc,
    String subQuestion,
    String answerCode,
    String revisionCount,
    String entryUserid,
    String updateUserid
) {}

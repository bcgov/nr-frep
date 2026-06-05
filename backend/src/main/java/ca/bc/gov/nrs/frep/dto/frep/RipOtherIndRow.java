package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One riparian "other" indicator (mirrors {@code THE.FREP_OTHER_INDICATOR_OBJECT}, 9 attrs). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RipOtherIndRow(
    String otherIndTypeId,
    String quesSectCode,
    String headerQuestionInd,
    String question,
    String otherIndicatorId,
    String otherAnswerInd,
    String revisionCount,
    String entryUserid,
    String updateUserid
) {}

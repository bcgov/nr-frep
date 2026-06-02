package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One riparian continuous indicator (mirrors {@code THE.FREP_CONTINUOUS_IND_OBJECT}, 8 attrs). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RipContinuousIndRow(
    String continuousIndId,
    String questionNo,
    String continuousIndType,
    String question,
    String total,
    String comments,
    String threshold,
    String revisionCount
) {}

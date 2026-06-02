package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One riparian point indicator (mirrors {@code THE.FREP_POINT_INDICATOR_OBJECT}, 13 attrs). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RipPointIndRow(
    String pointIndicatorId,
    String questionNo,
    String pointIndType,
    String transectNo,
    String measure1,
    String measure2,
    String measure3,
    String measure4,
    String measure5,
    String measure6,
    String threshold,
    String mean,
    String revisionCount
) {}

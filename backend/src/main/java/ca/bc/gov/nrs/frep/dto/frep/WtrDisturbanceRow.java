package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One water disturbance row (mirrors {@code THE.FREP_WTR_DISTURBANCE_OBJECT}, 8 attrs). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WtrDisturbanceRow(
    String disturbanceId,
    String checklistId,
    String disturbanceCode,
    String disturbanceAgeCode,
    String disturbanceNumber,
    String revisionCount,
    String entryUserid,
    String updateUserid
) {}

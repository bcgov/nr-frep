package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One water access-road row (mirrors {@code THE.FREP_WTR_ACCESS_ROAD_OBJECT}, 10 attrs).
 * {@code accessRoadDesc} is a joined code description (display-only, not written back).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WtrAccessRoadRow(
    String accessRoadId,
    String checklistId,
    String accessRoadType,
    String accessRoadDesc,
    String accessRoadStatusCode,
    String approximateRoadLength,
    String approximateRoadAge,
    String revisionCount,
    String entryUserid,
    String updateUserid
) {}

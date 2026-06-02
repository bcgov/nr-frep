package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One water assessment/range indicator row (mirrors {@code THE.FREP_WTR_ASSESSMENT_VW_OBJECT}, 10
 * attrs). The group/desc/count fields are display-only lookups; the save reconciles xref rows by
 * {@code assessmentType} + {@code assessmentInd}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WtrAssessmentRow(
    String waterSampleSiteId,
    String activityGrpCode,
    String activityGrpDesc,
    String activityGrpCount,
    String assessmentType,
    String assessmentDesc,
    String assessmentInd,
    String revisionCount,
    String entryUserid,
    String updateUserid
) {}

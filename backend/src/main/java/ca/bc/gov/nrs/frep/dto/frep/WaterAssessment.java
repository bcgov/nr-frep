package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Typed, editable water assessment screen (FREP 252): observed conditions + solutions. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaterAssessment(
    String waterSampleSiteId,
    List<WtrAssessmentRow> conditions,
    List<WtrAssessmentRow> solutions
) {

  public WaterAssessment withSampleSite(String newSampleSiteId) {
    return new WaterAssessment(newSampleSiteId, conditions, solutions);
  }
}

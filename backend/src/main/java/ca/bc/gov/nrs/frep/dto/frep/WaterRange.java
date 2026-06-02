package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Typed, editable water range screen (FREP 253): range indicators. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaterRange(
    String waterSampleSiteId,
    List<WtrAssessmentRow> ranges
) {

  public WaterRange withSampleSite(String newSampleSiteId) {
    return new WaterRange(newSampleSiteId, ranges);
  }
}

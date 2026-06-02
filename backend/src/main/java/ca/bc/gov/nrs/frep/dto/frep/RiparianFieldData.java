package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Typed, editable riparian field-data screen (FREP 231): a stream-reach-dry indicator plus the
 * point and continuous indicator grids persisted via {@code FREP_231_FIELD_DATA.SAVE}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RiparianFieldData(
    String checklistId,
    String fieldDataStreamReachDry,
    List<RipPointIndRow> points,
    List<RipContinuousIndRow> continuous
) {

  public RiparianFieldData withChecklist(String newChecklistId) {
    return new RiparianFieldData(newChecklistId, fieldDataStreamReachDry, points, continuous);
  }
}

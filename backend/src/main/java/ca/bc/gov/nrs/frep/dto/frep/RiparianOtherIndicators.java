package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Typed, editable riparian other-indicators screen (FREP 232). Persisted via FREP_232_OTHER_INDS.save. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RiparianOtherIndicators(
    String checklistId,
    List<RipOtherIndRow> indicators
) {

  public RiparianOtherIndicators withChecklist(String newChecklistId) {
    return new RiparianOtherIndicators(newChecklistId, indicators);
  }
}

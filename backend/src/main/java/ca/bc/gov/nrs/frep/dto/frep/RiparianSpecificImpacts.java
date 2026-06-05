package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Typed, editable riparian specific-impacts screen (FREP 234). Persisted via FREP_234_SPECIFIC_IMPACTS.SAVE. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RiparianSpecificImpacts(
    String checklistId,
    List<RipOpenSpecImpactRow> openImpacts,
    List<RipOtherSpecImpactRow> otherImpacts
) {

  public RiparianSpecificImpacts withChecklist(String newChecklistId) {
    return new RiparianSpecificImpacts(newChecklistId, openImpacts, otherImpacts);
  }
}

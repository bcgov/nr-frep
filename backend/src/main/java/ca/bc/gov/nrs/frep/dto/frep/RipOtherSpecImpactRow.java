package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One other specific impact (mirrors {@code THE.FREP_OTHER_SPEC_IMPACT_OBJECT}, 4 attrs). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RipOtherSpecImpactRow(
    String otherRiparianSpecImpactId,
    String description,
    String specImpactInd,
    String revisionCount
) {}

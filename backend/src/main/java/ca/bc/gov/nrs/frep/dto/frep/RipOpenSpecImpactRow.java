package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One opening specific impact (mirrors {@code THE.FREP_OPEN_SPEC_IMPACT_OBJECT}, 4 attrs). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RipOpenSpecImpactRow(
    String openingSpecificImpactId,
    String openingSpecificImpactType,
    String specImpactInd,
    String revisionCount
) {}

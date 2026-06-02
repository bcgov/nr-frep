package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single windthrow treatment on a biodiversity stratum, mirroring
 * {@code FREP_WINDTHROW_TREAT_OBJECT(windthrow_treatment_id, stratum_id, windthrow_treatment_code,
 * check_ind)}. The editor only needs the code + checked flag; id/stratumId are managed server-side.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BioWindthrowTreatment(
    String windthrowTreatmentId,
    String code,
    String checkInd
) {}

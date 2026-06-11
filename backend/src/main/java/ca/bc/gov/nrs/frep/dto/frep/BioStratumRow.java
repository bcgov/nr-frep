package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Summary row for the stratum list of a biodiversity checklist. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BioStratumRow(
    String stratumId,
    String stratumNumber,
    String strataTypeCode,
    String summaryDate,
    String plotCount,
    String size,
    String revisionCount
) {}

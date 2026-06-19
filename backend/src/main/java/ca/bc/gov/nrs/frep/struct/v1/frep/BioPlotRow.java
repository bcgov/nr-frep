package ca.bc.gov.nrs.frep.struct.v1.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Lightweight summary of a biodiversity plot (FREP screen 212) for the plot list table. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BioPlotRow(
    String plotId,
    String plotNumber,
    String assessorName,
    String revisionCount
) {}

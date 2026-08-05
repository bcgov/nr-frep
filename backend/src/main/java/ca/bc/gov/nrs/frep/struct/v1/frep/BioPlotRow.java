package ca.bc.gov.nrs.frep.struct.v1.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Lightweight summary of a biodiversity plot (FREP screen 212) for the plot list table. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BioPlotRow(
    String plotId,
    String plotNumber,
    String assessorName,
    String assessorDisplayName,
    String revisionCount
) {

  /**
   * Returns a copy carrying the FAM-resolved assessor name for display. {@code assessorName} stays
   * the bare userid the column actually stores, so nothing downstream compares against a label.
   */
  public BioPlotRow withAssessorDisplayName(String displayName) {
    return new BioPlotRow(plotId, plotNumber, assessorName, displayName, revisionCount);
  }
}

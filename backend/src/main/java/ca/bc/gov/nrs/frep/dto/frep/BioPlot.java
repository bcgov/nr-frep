package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Typed, editable view of a biodiversity plot (FREP screen 212). The scalar field order mirrors the
 * {@code FREP_212_BIOPLOT.save_plot} parameters; {@code standTable}/{@code cwdTable} are the plot's
 * child collections persisted via {@code save_bio_stand_detail}/{@code save_cwd_detail}. All values
 * are Strings. {@code revisionCount} is the optimistic-lock token round-tripped through save_plot.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BioPlot(
    String plotId,
    String stratumId,
    String plotNumber,
    String assessorName,
    String utmSignal,
    String utmZone,
    String utmEasting,
    String utmNorthing,
    String treeIndicator,
    String basalAreaFactor,
    String fixedAreaRadius,
    String fullCountArea,
    String cwdTransectIndicator,
    String firstLegTransect,
    String secondLegTransect,
    String plotComment,
    String revisionCount,
    List<BioStandRow> standTable,
    List<BioCwdRow> cwdTable
) {

  /** Returns a copy with the id/revision the save_plot proc echoes back. */
  public BioPlot withIdentity(String newPlotId, String newStratumId, String newRevisionCount) {
    return new BioPlot(
        newPlotId, newStratumId, plotNumber, assessorName, utmSignal, utmZone, utmEasting,
        utmNorthing, treeIndicator, basalAreaFactor, fixedAreaRadius, fullCountArea,
        cwdTransectIndicator, firstLegTransect, secondLegTransect, plotComment, newRevisionCount,
        standTable, cwdTable
    );
  }

  /** Returns a copy bound to the given stratum (the path is authoritative on save). */
  public BioPlot withStratum(String newStratumId) {
    return new BioPlot(
        plotId, newStratumId, plotNumber, assessorName, utmSignal, utmZone, utmEasting, utmNorthing,
        treeIndicator, basalAreaFactor, fixedAreaRadius, fullCountArea, cwdTransectIndicator,
        firstLegTransect, secondLegTransect, plotComment, revisionCount, standTable, cwdTable
    );
  }
}

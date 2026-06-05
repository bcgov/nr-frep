package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One coarse-woody-debris row of a biodiversity plot (FREP screen 212). Field order mirrors the
 * {@code THE.FREP_CWD_TABLE_OBJECT} attribute order used by
 * {@code FREP_212_BIOPLOT.save_cwd_detail}. The {@code *Desc} fields are display-only and not
 * written back.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BioCwdRow(
    String cwdId,
    String plotId,
    String speciesCode,
    String speciesDesc,
    String logNumber,
    String logDiameter,
    String logLength,
    String decayClassCode,
    String decayClassDesc,
    String comments,
    String revisionCount,
    String entryUserid,
    String updateUserid
) {}

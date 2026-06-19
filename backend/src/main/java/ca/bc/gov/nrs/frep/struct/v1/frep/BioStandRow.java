package ca.bc.gov.nrs.frep.struct.v1.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One stand-table (tree) row of a biodiversity plot (FREP screen 212). Field order mirrors the
 * {@code THE.FREP_STAND_TABLE_OBJECT} attribute order used by
 * {@code FREP_212_BIOPLOT.save_bio_stand_detail}. The {@code *Desc} fields are display-only (joined
 * code descriptions) and are not written back.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BioStandRow(
    String standId,
    String plotId,
    String speciesCode,
    String speciesDesc,
    String treeNumber,
    String dbh,
    String height,
    String comments,
    String decayClassCode,
    String decayClassDesc,
    String revisionCount,
    String entryUserid,
    String updateUserid
) {}

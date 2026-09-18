package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * A device's edited SLR graph, posted back at check-in.
 *
 * <p>Rows created offline carry a {@code tmp:} id (or none): the real ids are sequence-assigned
 * inside the Oracle save procs and cannot exist until this call. The orchestrator assigns them and
 * remaps references — a plot created offline points at its stratum's {@code tmp:} id.
 *
 * <p>Attachments are deliberately absent. They travel as their own multipart calls, flushed before
 * this one, so a 15 MB file never rides inside a graph payload.
 *
 * @param schemaVersion      the shape the device wrote. Rejected if this server cannot read it,
 *                           rather than being silently reinterpreted.
 * @param deviceCheckoutGuid proof this device holds the checkout
 * @param opening            the Opening tab
 * @param notes              the Notes tab — the same {@code BIODIVERSITY_CHECKLIST} row as the
 *                           opening, sharing one {@code revision_count}
 * @param strata             each stratum with its plots
 * @param tombstones         rows deleted offline that exist on the server. Rows created *and*
 *                           deleted offline never appear here — they have no server id to delete.
 */
public record BioSnapshotUpload(
    String schemaVersion,
    String deviceCheckoutGuid,
    BiodiversityOpening opening,
    RiparianNotes notes,
    List<BioStratumUpload> strata,
    List<Tombstone> tombstones
) {

  /** One stratum with its plots. A {@code tmp:} stratum id is remapped for its plots on save. */
  public record BioStratumUpload(BioStratum stratum, List<BioPlot> plots) {}

  /**
   * A row deleted on the device.
   *
   * @param entity         {@code STRATUM} or {@code PLOT}
   * @param id             the server id
   * @param revisionCount  the token the device last saw; the delete procs reject a mismatch
   */
  public record Tombstone(String entity, String id, String revisionCount) {

    public static final String STRATUM = "STRATUM";
    public static final String PLOT = "PLOT";
  }
}

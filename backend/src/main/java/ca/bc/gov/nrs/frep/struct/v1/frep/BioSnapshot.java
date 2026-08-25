package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * The whole Stand Level Retention (SLR) checklist graph in one response, for taking a checklist
 * offline.
 *
 * <p><b>Read-only, and it does not take the checkout.</b> The client reads this while the checklist
 * is still {@code ACT}, then POSTs {@code /offline} to claim it. That ordering (decision 18, adopted
 * from the shipped CHR flow) means an interrupted or failed download costs nothing: no checkout was
 * taken, so the checklist stays editable online and nothing has to be released.
 *
 * <p><b>Attachment metadata only — never bytes.</b> Files are up to 15 MB with no per-checklist cap,
 * so inlining them would make one response unbounded. The client fetches each attachment's content
 * individually, as CHR's take-offline does for photos.
 *
 * @param schemaVersion the shape of this payload. Stamped so a device holding a snapshot written by
 *                      an older build is detected rather than silently reinterpreted — SLR's payload
 *                      is a graph, so a breaking change cannot be absorbed the way CHR's single
 *                      document can.
 * @param checklistId   the checklist
 * @param resourceType  always {@code SLR}; SLB is historical and cannot be taken offline
 * @param statusCode    status at the moment of the read — {@code ACT} for a snapshot that can go on
 *                      to claim a checkout
 * @param opening       the Opening tab, which also carries the administration fields and team lead
 * @param notes         the Notes tab. Not a child entity: it is a column on the checklist row and
 *                      shares its {@code revision_count} with the opening
 * @param strata        each stratum with its plots, in list order
 * @param attachments   metadata for <b>every</b> attachment, not one page
 */
public record BioSnapshot(
    String schemaVersion,
    String checklistId,
    String resourceType,
    String statusCode,
    BiodiversityOpening opening,
    RiparianNotes notes,
    List<BioStratumSnapshot> strata,
    List<AttachmentRow> attachments
) {

  /** The current payload shape. Bump on any breaking change; see {@link #schemaVersion()}. */
  public static final String CURRENT_SCHEMA_VERSION = "1";

  /** One stratum with its plots. Plots carry their own stand/CWD VARRAYs. */
  public record BioStratumSnapshot(BioStratum stratum, List<BioPlot> plots) {}
}

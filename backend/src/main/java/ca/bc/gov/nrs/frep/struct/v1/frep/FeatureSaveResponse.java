package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * What a per-feature write hands back: the features it actually touched, and the checklist's new
 * lock token.
 *
 * <p>Deliberately not the whole checklist. The client consumes exactly two things from a section
 * save — the features array and {@code revisionCount} — and everything else the full document
 * carries is discarded on arrival, so mapping every feature's fields and child collections to
 * deliver one change is waste.
 *
 * <p>More than the addressed feature can appear here: writing an association writes both
 * directions, so the partner's stored state moves too and the client would otherwise be left
 * showing a stale copy of it.
 *
 * @param features      the features this write changed, re-read after the flush
 * @param revisionCount the checklist's token after the write, for the client's next save
 */
public record FeatureSaveResponse(List<Feature> features, String revisionCount) {}

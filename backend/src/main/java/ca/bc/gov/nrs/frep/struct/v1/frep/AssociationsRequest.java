package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * Set the features one feature is associated with.
 *
 * <p>The list is the complete set for the addressed feature, not a delta: whatever is absent is
 * unlinked. Targets are given by <b>id</b> rather than by feature label — a label is a display value
 * the evaluator can see, and resolving persistence references through it is what silently dropped
 * links when a feature was renamed.
 *
 * <p>{@code revisionCount} is the checklist's optimistic-lock token, carried in the body because
 * PUT has one (unlike the delete, which has to put it in the query string).
 *
 * @param revisionCount the checklist token the client last saw
 * @param featureIds    every feature this one should be associated with, empty to clear
 */
public record AssociationsRequest(String revisionCount, List<String> featureIds) {}

package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * Dissolve a composite.
 *
 * <p>The anchor row goes either way — ungrouping dissolves the group rather than emptying it — and
 * every member it held is released to stand on its own.
 *
 * <p>{@code deleteMemberIds} is the dialog's "keep or delete" answer, and it names the members that
 * were never assessed in their own right: a feature added from inside the composite dialog exists
 * only to be part of the group, so freeing it would leave it owing a full assessment. The client
 * decides which those are, because "undescribed" is a rule over seventeen indicator fields
 * (feature type, age, description) that the server holds as xref rows — re-deriving it here would
 * mean two definitions of the same rule with silent deletion as the failure mode.
 *
 * <p>Empty means keep them all.
 *
 * @param revisionCount    the checklist token the client last saw
 * @param deleteMemberIds  members to delete rather than release; each must belong to this composite
 */
public record CompositeUngroupRequest(String revisionCount, List<String> deleteMemberIds) {}

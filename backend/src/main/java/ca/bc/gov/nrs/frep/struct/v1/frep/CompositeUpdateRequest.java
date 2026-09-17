package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * Re-point an existing composite at a new set of members, and update its own class and source.
 *
 * <p>Unlike {@link CompositeCreateRequest} the anchor already exists and is addressed by id, so only
 * the two fields the dialog offers for it travel here. The member list is the complete set, not a
 * delta: a feature that was assessed under this composite and is absent from {@code memberIds} is
 * released back to standing on its own.
 *
 * <p>Editing a group may take a feature that is currently under a <em>different</em> composite —
 * moving one across is the point of that dialog, and the only rule is that a release never reaches
 * beyond this anchor's own members.
 *
 * @param revisionCount         the checklist token the client last saw
 * @param featureDescriptionCode the anchor's feature class
 * @param featureInfoSourceCode  the anchor's information source
 * @param memberIds             every feature that should be assessed under this composite, by id
 * @param newMembers            features created inside the dialog, to be inserted and grouped
 */
public record CompositeUpdateRequest(
    String revisionCount,
    String featureDescriptionCode,
    String featureInfoSourceCode,
    List<String> memberIds,
    List<Feature> newMembers) {}

package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * Create a composite: a new anchor feature, and the features assessed under it.
 *
 * <p>This is the one write in CHR where a reference target has no id yet — the anchor is created by
 * this request, so its members cannot name it beforehand. The anchor's identity <em>is</em> the
 * request, which is why no client-generated correlation id is needed anywhere in the endpoint set.
 *
 * <p>Members arrive two ways, both in the same call because the dialog is one gesture: features
 * that already exist are named by {@code memberIds}, and features typed into the dialog come as
 * {@code newMembers} and are created here. Splitting them into two requests would let a failure
 * leave behind features the evaluator never asked for.
 *
 * @param revisionCount the checklist token the client last saw
 * @param anchor        the composite itself — its label, feature class and information source
 * @param memberIds     existing features to group, by id
 * @param newMembers    features created inside the dialog, to be inserted and grouped
 */
public record CompositeCreateRequest(
    String revisionCount, Feature anchor, List<String> memberIds, List<Feature> newMembers) {}

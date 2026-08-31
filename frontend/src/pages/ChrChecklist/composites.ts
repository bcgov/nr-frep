import type { Feature } from '@/types/chrChecklist';

import { AGE_FIELDS, FEATURE_TYPE_FIELDS } from '@/pages/ChrChecklist/tabStatus';

/**
 * Composite features — a group of features that were managed together and are assessed as one
 * record.
 *
 * The shape is dictated by what the backend stores and validates, so it is worth stating plainly:
 *
 * - An **anchor** is an ordinary feature row with `compositeFeatureInd = 'true'` and no
 *   `compositeFeature`. It carries the assessment, and `ChrSubmitValidationService` validates it
 *   across all five feature tabs.
 * - A **member** points at the anchor by *label* through `compositeFeature`, and keeps
 *   `compositeFeatureInd = 'false'`. The backend resolves that label to the
 *   `COMPOSITE_CHR_FEATURE_ID` self-FK on save, and skips a member's per-tab validation because the
 *   parent covers it.
 *
 * A member must not also carry `compositeFeatureInd = 'true'`: the backend's `validateComposite`
 * treats every `'true'` row as an anchor and demands it have two members of its own, so a member
 * flagged that way reports "must include at least two features" against itself at submit.
 *
 * The anchor's own label stays numeric like any other feature — `FEATURE_LABEL` is `VARCHAR(5)`, so
 * a stored name like "Composite 1" would not fit. "Composite N" is a display name derived from the
 * order the anchors appear in ({@link compositeDisplayName}).
 */

/** The label comparison the backend makes — trimmed and case-insensitive, matching
 *  `ChrSubmitValidationService.matchesCompositeLabel`. "Composite of" is free text, so a stray
 *  space must not silently drop a member. */
export const sameLabel = (a?: string, b?: string): boolean => {
  const left = a?.trim().toLowerCase();
  const right = b?.trim().toLowerCase();
  // `left === right` alone would call two *unlabelled* features a match, since both sides are
  // undefined. A missing label matches nothing, including another missing label.
  return left !== undefined && left === right;
};

const hasValue = (value?: string): boolean => (value ?? '').trim() !== '';

/** A feature that groups others and carries the group's assessment. */
export const isCompositeAnchor = (feature: Feature): boolean =>
  (feature.compositeFeatureInd ?? '').trim().toLowerCase() === 'true' &&
  !hasValue(feature.compositeFeature);

/** A feature assessed through some composite rather than on its own. */
export const isCompositeMember = (feature: Feature): boolean => hasValue(feature.compositeFeature);

/** The features grouped under an anchor, in list order. */
export const membersOf = (features: Feature[], anchor: Feature): Feature[] =>
  features.filter((f) => f !== anchor && sameLabel(f.compositeFeature, anchor.featureLabel));

/**
 * Composites in creation order — by anchor label, which only ever increases.
 *
 * Deliberately not array order: {@link addComposite} has to put a new anchor at the front, so
 * ordering by position would jump an existing composite down the table the moment a second one was
 * created.
 */
const orderedAnchors = (features: Feature[]): Feature[] =>
  features.filter(isCompositeAnchor).sort((a, b) => {
    const left = Number(a.featureLabel);
    const right = Number(b.featureLabel);
    if (Number.isFinite(left) && Number.isFinite(right)) return left - right;
    return (a.featureLabel ?? '').localeCompare(b.featureLabel ?? '');
  });

/** The next free numeric feature label. Non-numeric labels are ignored, as they carry no position. */
export const nextFeatureLabel = (features: Feature[]): string => {
  const numbers = features.map((f) => Number(f.featureLabel)).filter((n) => Number.isFinite(n));
  return String((numbers.length > 0 ? Math.max(...numbers) : 0) + 1);
};

/** What the composite dialog hands back when the user creates or edits a group. */
export type CompositeDraft = {
  featureDescriptionCode?: string;
  featureInfoSourceCode?: string;
  /** Labels of the features to group, existing and newly added alike. */
  memberLabels: string[];
  /** Features added from inside the dialog, already carrying allocated labels. */
  additions: Feature[];
};

const asMember = (feature: Feature, anchorLabel: string): Feature => ({
  ...feature,
  compositeFeature: anchorLabel,
  // Explicitly false, not merely absent — see the note above on why a member must not read as an
  // anchor to validateComposite.
  compositeFeatureInd: 'false',
});

/**
 * Add a composite to the list.
 *
 * The anchor goes at the **front**. That is a persistence requirement, not a display choice:
 * `ChrChecklistPersistenceService` walks the features in order and resolves each member's
 * "composite of" label to a row id with a JPQL lookup, so the anchor has to have been persisted by
 * the time its members are written or the self-FK silently lands as null.
 */
export const addComposite = (features: Feature[], draft: CompositeDraft): Feature[] => {
  const withAdditions = [...features, ...draft.additions];
  const anchor: Feature = {
    featureLabel: nextFeatureLabel(withAdditions),
    compositeFeatureInd: 'true',
    featureDescriptionCode: draft.featureDescriptionCode,
    featureInfoSourceCode: draft.featureInfoSourceCode,
  };
  const anchorLabel = anchor.featureLabel as string;
  return [
    anchor,
    ...withAdditions.map((f) =>
      draft.memberLabels.some((label) => sameLabel(f.featureLabel, label))
        ? asMember(f, anchorLabel)
        : f,
    ),
  ];
};

/** Re-point an existing composite at a new set of members, and update its own class / source. */
export const updateComposite = (
  features: Feature[],
  anchor: Feature,
  draft: CompositeDraft,
): Feature[] => {
  const anchorLabel = anchor.featureLabel;
  if (!anchorLabel) return features;
  const withAdditions = [...features, ...draft.additions];
  return withAdditions.map((f) => {
    // Matched by label, not by reference: the dialog holds the anchor it was opened with, and a
    // save in between replaces every object in the list.
    if (sameLabel(f.featureLabel, anchorLabel)) {
      return {
        ...f,
        featureDescriptionCode: draft.featureDescriptionCode,
        featureInfoSourceCode: draft.featureInfoSourceCode,
      };
    }
    const wanted = draft.memberLabels.some((label) => sameLabel(f.featureLabel, label));
    if (wanted) return asMember(f, anchorLabel);
    // Dropped from this composite — released, but never stolen from a different one.
    if (sameLabel(f.compositeFeature, anchorLabel)) {
      return { ...f, compositeFeature: undefined };
    }
    return f;
  });
};

/**
 * Dissolve a composite: the anchor row goes, and its members become individual features again.
 *
 * Clearing `compositeFeature` here matters beyond the display — a member left pointing at a deleted
 * anchor is what `ORA-02292` (child record found) is raised on.
 */
export const ungroupComposite = (features: Feature[], anchor: Feature): Feature[] =>
  features
    .filter((f) => f !== anchor)
    .map((f) =>
      sameLabel(f.compositeFeature, anchor.featureLabel)
        ? { ...f, compositeFeature: undefined }
        : f,
    );

/**
 * One rendered table row: a composite and the features assessed under it, or a lone feature.
 *
 * `name` is the composite's own feature label. A composite is a feature like any other and keeps
 * its real number — the caption row beneath it, not an invented name, is what marks it as a group.
 */
export type FeatureRow =
  | { kind: 'composite'; anchor: Feature; name: string; members: Feature[] }
  | { kind: 'feature'; feature: Feature };

/**
 * The feature list as the table shows it: each composite followed by the features grouped under it,
 * with ungrouped features in their own rows. Members are rendered under their anchor wherever they
 * sit in the underlying array, so storage order (anchor first) and reading order stay independent.
 */
export const featureRows = (features: Feature[]): FeatureRow[] => [
  ...orderedAnchors(features).map((anchor) => ({
    kind: 'composite' as const,
    anchor,
    name: anchor.featureLabel ?? '',
    members: features.filter(
      (f) => f !== anchor && sameLabel(f.compositeFeature, anchor.featureLabel),
    ),
  })),
  ...features
    .filter((f) => !isCompositeAnchor(f) && !isCompositeMember(f))
    .map((feature) => ({ kind: 'feature' as const, feature })),
];

/**
 * Whether a feature has nothing recorded against it beyond its identity.
 *
 * This is what a feature added from inside the composite dialog looks like: it exists to be part of
 * the group, and the group's assessment covers it. Ungrouping strands such a feature — it would
 * then owe a full assessment of its own — which is why the ungroup dialog asks what to do with it.
 *
 * Judged on the three things submit demands of every standalone feature: at least one feature type,
 * at least one age, and a description. Those are real user selections, which is what makes them
 * usable here.
 *
 * The tempting test — "no field outside identity is set" — does not work against a saved feature.
 * `ChrStringUtils.booleanToIndictorInverseLogic(null)` stores `'N'` for an unanswered
 * `unabletoLocate` / `noManagement` / `sitePermitIssued`, and `indicatorToBooleanStrInverseLogic`
 * reads `'N'` back as `'true'` — so every feature returns from the server carrying three answers
 * nobody gave, and no saved feature ever looks untouched.
 */
export const hasNoDetailsOfItsOwn = (feature: Feature): boolean => {
  // Narrowed rather than stringified: the Feature index signature is `unknown`, so a template
  // literal would turn an array or object field into "[object Object]" and compare that.
  const ticked = (fields: readonly string[]): boolean =>
    fields.some((key) => {
      const value = feature[key];
      return typeof value === 'string' && value.trim().toLowerCase() === 'true';
    });
  return (
    !ticked(FEATURE_TYPE_FIELDS) &&
    !ticked(AGE_FIELDS) &&
    (feature.featureDescription ?? '').trim() === ''
  );
};

/** Members of a composite that were never assessed in their own right, in list order. */
export const undescribedMembers = (features: Feature[], anchor: Feature): Feature[] =>
  membersOf(features, anchor).filter(hasNoDetailsOfItsOwn);

/** "Feature 5", "Features 5 and 6", "Features 5, 6 and 7". */
export const listFeatureLabels = (features: Feature[]): string => {
  const labels = features.map((f) => f.featureLabel ?? '?');
  if (labels.length <= 1) return `Feature ${labels[0] ?? '?'}`;
  return `Features ${labels.slice(0, -1).join(', ')} and ${labels.at(-1) ?? '?'}`;
};

/** Ungroup, discarding the members that were never assessed rather than stranding them. */
export const ungroupDiscardingUndescribed = (features: Feature[], anchor: Feature): Feature[] => {
  // Matched by label, not by reference: ungroupComposite rebuilds every member it frees, so the
  // objects coming back are not the ones identified here.
  const doomed = undescribedMembers(features, anchor).map((f) => f.featureLabel);
  return ungroupComposite(features, anchor).filter(
    (f) => !doomed.some((label) => sameLabel(f.featureLabel, label)),
  );
};

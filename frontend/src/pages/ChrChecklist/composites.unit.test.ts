import { describe, expect, it } from 'vitest';

import type { Feature } from '@/types/chrChecklist';

import {
  addComposite,
  featureRows,
  hasNoDetailsOfItsOwn,
  isCompositeAnchor,
  isCompositeMember,
  membersOf,
  nextFeatureLabel,
  listFeatureLabels,
  undescribedMembers,
  ungroupComposite,
  ungroupDiscardingUndescribed,
  updateComposite,
} from '@/pages/ChrChecklist/composites';

const feature = (label: string, extra: Partial<Feature> = {}): Feature => ({
  featureLabel: label,
  compositeFeatureInd: 'false',
  ...extra,
});

const base = () => [feature('1'), feature('2'), feature('3'), feature('4')];

describe('addComposite', () => {
  it('anchors the group and points the chosen features at it', () => {
    const next = addComposite(base(), {
      featureDescriptionCode: 'ARCH',
      featureInfoSourceCode: 'AIA',
      memberLabels: ['1', '3'],
      additions: [],
    });

    const anchor = next[0];
    expect(anchor.compositeFeatureInd).toBe('true');
    expect(anchor.compositeFeature).toBeUndefined();
    expect(anchor.featureDescriptionCode).toBe('ARCH');
    expect(next.filter((f) => f.compositeFeature === anchor.featureLabel)).toHaveLength(2);
  });

  it('leaves members flagged false so they do not read as composites themselves', () => {
    // ChrSubmitValidationService.validateComposite treats every 'true' row as an anchor and demands
    // two members of its own — a member flagged 'true' fails submit against itself.
    const next = addComposite(base(), { memberLabels: ['1', '3'], additions: [] });
    const members = next.filter((f) => f.compositeFeature);
    expect(members).toHaveLength(2);
    members.forEach((m) => expect(m.compositeFeatureInd).toBe('false'));
  });

  it('puts the anchor before its members', () => {
    // The backend resolves each member's "composite of" label to a row id as it walks the list, so
    // an anchor saved after its members leaves the self-FK null.
    const next = addComposite(base(), { memberLabels: ['1', '3'], additions: [] });
    const anchorAt = next.findIndex(isCompositeAnchor);
    const firstMemberAt = next.findIndex((f) => Boolean(f.compositeFeature));
    expect(anchorAt).toBeLessThan(firstMemberAt);
  });

  it('gives the anchor a label that fits FEATURE_LABEL, not a display name', () => {
    const next = addComposite(base(), { memberLabels: ['1', '3'], additions: [] });
    // FEATURE_LABEL is VARCHAR(5); "Composite 1" would raise ORA-12899.
    expect(next[0].featureLabel).toBe('5');
    expect((next[0].featureLabel as string).length).toBeLessThanOrEqual(5);
  });

  it('numbers features added inside the dialog before the anchor takes its label', () => {
    const next = addComposite(base(), {
      memberLabels: ['1', '5'],
      additions: [feature('5')],
    });
    expect(next[0].featureLabel).toBe('6');
    expect(next.filter((f) => f.compositeFeature === '6').map((f) => f.featureLabel)).toEqual([
      '1',
      '5',
    ]);
  });

  it('matches member labels the way the backend does — trimmed and case-insensitive', () => {
    const next = addComposite([feature('1'), feature('2')], {
      memberLabels: [' 1 ', '2'],
      additions: [],
    });
    expect(next.filter((f) => f.compositeFeature)).toHaveLength(2);
  });
});

describe('updateComposite', () => {
  const grouped = () =>
    addComposite(base(), {
      featureDescriptionCode: 'ARCH',
      memberLabels: ['1', '3'],
      additions: [],
    });

  it('releases a feature dropped from the group', () => {
    const next = updateComposite(grouped(), grouped()[0], { memberLabels: ['1'], additions: [] });
    expect(next.find((f) => f.featureLabel === '3')?.compositeFeature).toBeUndefined();
  });

  it('never releases a feature belonging to a different composite', () => {
    const first = addComposite(base(), { memberLabels: ['1', '2'], additions: [] });
    const second = addComposite(first, { memberLabels: ['3', '4'], additions: [] });
    const secondAnchor = second[0];

    const next = updateComposite(second, secondAnchor, { memberLabels: ['3'], additions: [] });
    // '1' and '2' are the other composite's; dropping '4' must not touch them.
    expect(next.find((f) => f.featureLabel === '1')?.compositeFeature).toBeDefined();
    expect(next.find((f) => f.featureLabel === '4')?.compositeFeature).toBeUndefined();
  });

  it('updates the composite’s own class and source', () => {
    const list = grouped();
    const next = updateComposite(list, list[0], {
      featureDescriptionCode: 'CMT',
      memberLabels: ['1', '3'],
      additions: [],
    });
    expect(next[0].featureDescriptionCode).toBe('CMT');
  });
});

describe('ungroupComposite', () => {
  it('removes the anchor and frees its members', () => {
    const list = addComposite(base(), { memberLabels: ['1', '3'], additions: [] });
    const next = ungroupComposite(list, list[0]);

    expect(next).toHaveLength(4);
    expect(next.some(isCompositeAnchor)).toBe(false);
    // A member left pointing at a deleted anchor is what raises ORA-02292.
    expect(next.every((f) => !f.compositeFeature)).toBe(true);
  });
});

describe('featureRows', () => {
  it('renders each composite followed by its members, then the ungrouped features', () => {
    const list = addComposite(base(), { memberLabels: ['1', '3'], additions: [] });
    const rows = featureRows(list);

    expect(rows).toHaveLength(3);
    // The composite keeps its own feature number rather than being given an invented name.
    expect(rows[0]).toMatchObject({ kind: 'composite', name: '5' });
    expect(rows[0].kind === 'composite' && rows[0].members.map((m) => m.featureLabel)).toEqual([
      '1',
      '3',
    ]);
    expect(
      rows.slice(1).map((r) => (r.kind === 'feature' ? r.feature.featureLabel : null)),
    ).toEqual(['2', '4']);
  });

  it('never lists a member twice', () => {
    const list = addComposite(base(), { memberLabels: ['1', '3'], additions: [] });
    const rows = featureRows(list);
    expect(rows.some((r) => r.kind === 'feature' && r.feature.compositeFeature)).toBe(false);
  });

  it('keeps a composite where it was when a later one is created', () => {
    // The new anchor is prepended (persistence needs it saved before its members), so ordering by
    // array position would jump the existing group down the table.
    const first = addComposite(base(), { memberLabels: ['1', '2'], additions: [] });
    const second = addComposite(first, { memberLabels: ['3', '4'], additions: [] });
    const names = featureRows(second)
      .filter((r) => r.kind === 'composite')
      .map((r) => (r.kind === 'composite' ? r.name : ''));

    expect(names).toEqual(['5', '6']);
  });
});

describe('classification helpers', () => {
  it('does not count a feature that is both flagged and pointed at another as an anchor', () => {
    const odd = feature('9', { compositeFeatureInd: 'true', compositeFeature: '1' });
    expect(isCompositeAnchor(odd)).toBe(false);
    expect(isCompositeMember(odd)).toBe(true);
  });

  it('finds an anchor’s members', () => {
    const list = addComposite(base(), { memberLabels: ['1', '3'], additions: [] });
    expect(membersOf(list, list[0]).map((f) => f.featureLabel)).toEqual(['1', '3']);
  });

  it('ignores non-numeric labels when allocating the next one', () => {
    expect(nextFeatureLabel([feature('1'), feature('x'), feature('7')])).toBe('8');
  });
});

describe('ungrouping a composite with undescribed members', () => {
  const described = (label: string): Feature =>
    feature(label, { featureDescriptionCode: 'ARCH', featureDescription: 'A real find' });
  /** Class and source are identity, not assessment — this feature has never been described. */
  const bare = (label: string): Feature => feature(label, { featureDescriptionCode: 'ARCH' });

  const grouped = () =>
    addComposite([described('1'), described('2'), bare('3')], {
      memberLabels: ['1', '2', '3'],
      additions: [],
    });

  it('spots the members that were never assessed in their own right', () => {
    const list = grouped();
    expect(undescribedMembers(list, list[0]).map((f) => f.featureLabel)).toEqual(['3']);
  });

  it('counts a ticked feature type, age or description as a detail', () => {
    expect(hasNoDetailsOfItsOwn(feature('9'))).toBe(true);
    expect(hasNoDetailsOfItsOwn(feature('9', { burialSite: 'false' }))).toBe(true);
    expect(hasNoDetailsOfItsOwn(feature('9', { burialSite: 'true' }))).toBe(false);
    expect(hasNoDetailsOfItsOwn(feature('9', { pre1846: 'true' }))).toBe(false);
    expect(hasNoDetailsOfItsOwn(feature('9', { featureDescription: 'A rock shelter' }))).toBe(
      false,
    );
  });

  it('still sees a saved-but-undescribed feature as undescribed', () => {
    // The backend stores an unanswered unabletoLocate / noManagement / sitePermitIssued as 'N' and
    // reads 'N' back as 'true' (inverse logic), so every saved feature returns carrying three
    // answers nobody gave. Judging "untouched" by "no field is set" would never fire again.
    const roundTripped = feature('9', {
      unabletoLocate: 'true',
      noManagement: 'true',
      sitePermitIssued: 'true',
    });
    expect(hasNoDetailsOfItsOwn(roundTripped)).toBe(true);
  });

  it('keeps every feature when asked to keep them', () => {
    const list = grouped();
    const next = ungroupComposite(list, list[0]);
    expect(next.map((f) => f.featureLabel)).toEqual(['1', '2', '3']);
  });

  it('discards only the undescribed members when asked to delete them', () => {
    const list = grouped();
    const next = ungroupDiscardingUndescribed(list, list[0]);
    expect(next.map((f) => f.featureLabel)).toEqual(['1', '2']);
    expect(next.every((f) => !f.compositeFeature)).toBe(true);
  });

  it('never discards a feature outside the composite', () => {
    const list = [...grouped(), bare('8')];
    const next = ungroupDiscardingUndescribed(list, list[0]);
    expect(next.map((f) => f.featureLabel)).toContain('8');
  });

  it('names the stranded features in a readable list', () => {
    expect(listFeatureLabels([feature('5')])).toBe('Feature 5');
    expect(listFeatureLabels([feature('5'), feature('6')])).toBe('Features 5 and 6');
    expect(listFeatureLabels([feature('5'), feature('6'), feature('7')])).toBe(
      'Features 5, 6 and 7',
    );
  });
});

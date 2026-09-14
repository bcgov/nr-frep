import { describe, expect, it } from 'vitest';

import { addComposite } from './composites';
import {
  attachmentsOutstanding,
  blockSummaryOutstanding,
  chrTabStatuses,
  featuresOutstanding,
  openingOutstanding,
} from './tabStatus';

import type { CheckList, Feature } from '@/types/chrChecklist';

/** A feature with every submit rule satisfied — the baseline the cases below break one rule off. */
const completeFeature = (over: Partial<Feature> = {}): Feature => ({
  featureLabel: '1',
  compositeFeatureInd: 'false',
  featureDescriptionCode: 'CMT',
  featureInfoSourceCode: 'AIA',
  burialSite: 'true',
  pre1846: 'true',
  featureRating: 'HIGH',
  ...over,
});

/** A checklist that would pass submit, so a single change is the only thing under test. */
const completeChecklist = (over: Partial<CheckList> = {}): CheckList => ({
  checklistID: '1001',
  evaluationDate: '2026-06-10',
  assessedBy: 'IDIR\\TESTER',
  generalLocation: '16 km on Finnegan FSR',
  yearOfHarvest: '2024',
  rating: 'HIGH',
  features: [completeFeature()],
  contacts: [],
  pictures: [],
  ...over,
});

describe('openingOutstanding', () => {
  it('names every required Opening field on an empty checklist, in tab order', () => {
    // Each names the field as the form labels it, and the fieldset holding it. Harvest completion
    // year has none: the Opening tab does not edit it — it arrives with the record.
    expect(openingOutstanding({})).toEqual([
      'Evaluation date, in the Evaluation section',
      'Evaluator — use “Assign it to me”, in the Evaluation section',
      'General location, in the Evaluation section',
      'Harvest completion year',
    ]);
  });

  it('is empty once the tab is filled in', () => {
    expect(openingOutstanding(completeChecklist())).toEqual([]);
  });
});

describe('blockSummaryOutstanding', () => {
  it('requires a rating', () => {
    expect(blockSummaryOutstanding({})).toEqual(['Rating, in the Block rating section']);
  });

  it('requires a description only for a question answered Yes', () => {
    const items = blockSummaryOutstanding(
      completeChecklist({
        q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock: 'true',
      }),
    );
    expect(items).toEqual([
      'Q8 — description of the limiting operational factors, in the Operational review section',
    ]);
  });
});

describe('featuresOutstanding', () => {
  it('requires at least one feature', () => {
    expect(featuresOutstanding(completeChecklist({ features: [] }))).toEqual([
      'At least one feature is required before submit.',
    ]);
  });

  it('passes a complete feature', () => {
    expect(featuresOutstanding(completeChecklist())).toEqual([]);
  });

  it('names the feature it is talking about', () => {
    const items = featuresOutstanding(
      completeChecklist({ features: [completeFeature({ featureRating: undefined })] }),
    );
    // Named as the editor labels the field, and pointing at the accordion section holding it.
    expect(items).toEqual(['Feature 1 — Feature rating, in the Summary section']);
  });

  it('falls back to the position when a feature has no label', () => {
    const items = featuresOutstanding(
      completeChecklist({ features: [completeFeature({ featureLabel: '' })] }),
    );
    expect(items).toContain('Feature 1 — Feature label, in the Description section');
  });

  it('requires a feature type and an age', () => {
    const items = featuresOutstanding(
      completeChecklist({
        features: [completeFeature({ burialSite: 'false', pre1846: 'false' })],
      }),
    );
    expect(items).toEqual([
      'Feature 1 — Tick at least one type of feature, in the Description section',
      'Feature 1 — Select an age, in the Age section',
    ]);
  });

  it('asks a composite for at least two members', () => {
    const items = featuresOutstanding(
      completeChecklist({
        features: [completeFeature({ compositeFeatureInd: 'true' })],
      }),
    );
    expect(items[0]).toContain('A composite needs at least two features');
  });

  it('accepts a composite once two features point at it, matching the label loosely', () => {
    const items = featuresOutstanding(
      completeChecklist({
        features: [
          completeFeature({ featureLabel: 'A', compositeFeatureInd: 'true' }),
          completeFeature({ featureLabel: '2', compositeFeature: ' a ' }),
          completeFeature({ featureLabel: '3', compositeFeature: 'A' }),
        ],
      }),
    );
    expect(items).toEqual([]);
  });

  it('checks a composite member only for its label', () => {
    // Empty apart from the membership: everything else is validated through the parent composite.
    const items = featuresOutstanding(
      completeChecklist({
        features: [
          completeFeature({ featureLabel: 'A', compositeFeatureInd: 'true' }),
          { featureLabel: '2', compositeFeature: 'A' },
          { featureLabel: '3', compositeFeature: 'A' },
        ],
      }),
    );
    expect(items).toEqual([]);
  });

  it('flags a damage question answered Yes with no cause selected', () => {
    const items = featuresOutstanding(
      completeChecklist({
        features: [completeFeature({ q1Isthereevidenceofdamagetothesiteorfeature: 'true' })],
      }),
    );
    // Damage has its own accordion section, even though the rule lives in the effectiveness chain.
    expect(items).toEqual([
      'Feature 1 — Q1 is Yes — tick at least one Q2 cause, in the Damage section',
    ]);
  });

  it('flags "No management applied" contradicting a selected strategy', () => {
    const items = featuresOutstanding(
      completeChecklist({
        features: [completeFeature({ noManagement: 'true', leftStanding: 'true' })],
      }),
    );
    expect(items[0]).toContain('“No management applied”');
  });

  it('flags "No management applied" contradicting an other-activity description', () => {
    // otherActivities holds the description, never "true" — presence is what "selected" means for
    // it, so this contradiction has to be caught by text, not by the indicator test.
    const items = featuresOutstanding(
      completeChecklist({
        features: [completeFeature({ noManagement: 'true', otherActivities: 'Fenced' })],
      }),
    );
    expect(items[0]).toContain('“No management applied”');
  });

  it('leaves "No management applied" alone when the other-activity box is blank', () => {
    const items = featuresOutstanding(
      completeChecklist({
        features: [completeFeature({ noManagement: 'true', otherActivities: '   ' })],
      }),
    );
    expect(items).toEqual([]);
  });

  it('requires a Q2 cause once Q3 is answered Yes', () => {
    // Q3 is a coded answer, not an indicator — testing it as a boolean left this rule unreachable.
    const items = featuresOutstanding(
      completeChecklist({
        features: [
          completeFeature({
            q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse: 'Y',
          }),
        ],
      }),
    );
    expect(items[0]).toContain('Q3 is Yes');
  });

  it.each(['N', 'D', ''])('asks for no Q2 cause when Q3 is %s', (answer) => {
    const items = featuresOutstanding(
      completeChecklist({
        features: [
          completeFeature({
            q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse: answer,
          }),
        ],
      }),
    );
    expect(items).toEqual([]);
  });

  it('requires a permit number once the AIA permit box is checked', () => {
    const items = featuresOutstanding(
      completeChecklist({ features: [completeFeature({ sitePermitIssued: 'true' })] }),
    );
    expect(items[0]).toContain('Permit number');
  });

  it('requires every "Other" planned strategy to have a source and a unique description', () => {
    const items = featuresOutstanding(
      completeChecklist({
        features: [
          completeFeature({
            otherPlannedManagementStrategy: [
              { otherStrategy: 'Fenced', fnInd: 'true' },
              { otherStrategy: 'Fenced', fnInd: 'true' },
              { otherStrategy: 'Signed' },
            ],
          }),
        ],
      }),
    );
    expect(items).toEqual([
      'Feature 1 — Other strategy “Fenced” is entered more than once — each must be unique, ' +
        'in the Planning section',
      'Feature 1 — Other strategy “Signed” — tick FN, AIA/SAP or Site plan, in the Planning section',
    ]);
  });
});

describe('attachmentsOutstanding', () => {
  it('names a photo with no description', () => {
    const items = attachmentsOutstanding(
      completeChecklist({ pictures: [{ fileName: 'stump.jpg' }] }),
    );
    expect(items).toEqual(['stump.jpg — every photo requires a description.']);
  });

  it('is quiet when every held photo is described', () => {
    expect(
      attachmentsOutstanding(completeChecklist({ pictures: [{ description: 'A stump' }] })),
    ).toEqual([]);
  });
});

describe('chrTabStatuses', () => {
  it('reports what a brand-new checklist owes, without waiting to be started', () => {
    const { statuses, counts, items } = chrTabStatuses({});
    expect(statuses.opening).toBe('errors');
    expect(statuses.blockSummary).toBe('errors');
    expect(statuses.features).toBe('errors');
    expect(counts.opening).toBe(4);
    // Opening items are ungrouped — the tab is one form, so there is no record to head them with.
    expect(items.opening.every((item) => item.group === undefined)).toBe(true);
  });

  it('attributes each feature rule to the feature it belongs to', () => {
    const { items, outstanding } = chrTabStatuses(
      completeChecklist({ features: [{ featureLabel: '1', compositeFeatureInd: 'false' }] }),
    );
    // The heading is carried as its own field so the panel can group on it...
    expect(items.features.every((item) => item.group === 'Feature 1')).toBe(true);
    // ...while the flat form the submit pre-flight reads is unchanged.
    expect(outstanding.features[0]).toMatch(/^Feature 1 — /);
  });

  it('turns a started but incomplete tab red, with the number outstanding', () => {
    const { statuses, counts } = chrTabStatuses(completeChecklist({ generalLocation: undefined }));
    expect(statuses.opening).toBe('errors');
    expect(counts.opening).toBe(1);
  });

  it('reads a submit-ready checklist as complete throughout', () => {
    const { statuses } = chrTabStatuses(completeChecklist());
    expect(statuses).toEqual({
      opening: 'complete',
      blockSummary: 'complete',
      features: 'complete',
      // Rule-less tabs report `none`, not `complete`: both are silent in the strip, but only one
      // of them means "every rule passed" — see chrTabStatuses.
      contacts: 'none',
      notes: 'none',
      attachments: 'none',
    });
  });

  it('gives Contacts and Notes no indicator at all — they carry no rules of either kind', () => {
    const { statuses, outstanding } = chrTabStatuses({});
    expect(statuses.contacts).toBe('none');
    expect(statuses.notes).toBe('none');
    expect(outstanding.contacts).toEqual([]);
    expect(outstanding.notes).toEqual([]);
  });

  it('counts a feature\u2019s over-long free text, which blocks the save but never reaches the proc', () => {
    const feature = {
      featureLabel: '1',
      compositeFeatureInd: 'false',
      // Must exceed FEATURE_TEXT_LIMITS.featureComment, which the column widening took to 2000.
      // At 600 this asserted a limit that no longer exists and could never report "Too long".
      featureComment: 'x'.repeat(2100),
    };
    const { outstanding } = chrTabStatuses(completeChecklist({ features: [feature] }));
    expect(outstanding.features.some((item) => item.includes('Too long'))).toBe(true);
  });
});

describe('composites in the outstanding list', () => {
  it('names a composite by the number the feature table shows', () => {
    const features = addComposite(
      [
        { featureLabel: '1', compositeFeatureInd: 'false' },
        { featureLabel: '2', compositeFeatureInd: 'false' },
      ],
      { memberLabels: ['1', '2'], additions: [] },
    );
    const items = featuresOutstanding({ features } as CheckList);

    // The anchor took label 3, and that is what the table shows — no invented name to chase.
    expect(items.some((i) => i.startsWith('Feature 3 — '))).toBe(true);
  });

  it('does not ask a member for the assessment its composite carries', () => {
    const features = addComposite(
      [
        {
          featureLabel: '1',
          compositeFeatureInd: 'false',
          featureDescriptionCode: 'CMT',
          featureInfoSourceCode: 'AIA',
        },
        {
          featureLabel: '2',
          compositeFeatureInd: 'false',
          featureDescriptionCode: 'CT',
          featureInfoSourceCode: 'SP',
        },
      ],
      { memberLabels: ['1', '2'], additions: [] },
    );
    const items = featuresOutstanding({ features } as CheckList);

    expect(items.some((i) => i.startsWith('Feature 1 — '))).toBe(false);
    expect(items.some((i) => i.startsWith('Feature 2 — '))).toBe(false);
  });

  it('does not report a well-formed composite as short of members', () => {
    const features = addComposite(
      [
        { featureLabel: '1', compositeFeatureInd: 'false' },
        { featureLabel: '2', compositeFeatureInd: 'false' },
      ],
      { memberLabels: ['1', '2'], additions: [] },
    );
    expect(
      featuresOutstanding({ features } as CheckList).some((i) =>
        i.includes('at least two features'),
      ),
    ).toBe(false);
  });
});

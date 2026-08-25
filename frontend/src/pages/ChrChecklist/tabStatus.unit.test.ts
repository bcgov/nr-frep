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
    expect(openingOutstanding({})).toEqual([
      'Evaluation date',
      'Evaluator — use “Assign it to me”',
      'General location',
      'Harvest completion year',
    ]);
  });

  it('is empty once the tab is filled in', () => {
    expect(openingOutstanding(completeChecklist())).toEqual([]);
  });
});

describe('blockSummaryOutstanding', () => {
  it('requires a rating', () => {
    expect(blockSummaryOutstanding({})).toEqual(['Rating']);
  });

  it('requires a description only for a question answered Yes', () => {
    const items = blockSummaryOutstanding(
      completeChecklist({
        q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock: 'true',
      }),
    );
    expect(items).toEqual(['Q8 — description of the limiting operational factors']);
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
    expect(items).toEqual(['Feature 1 — Provide a Rating in Feature Summary.']);
  });

  it('falls back to the position when a feature has no label', () => {
    const items = featuresOutstanding(
      completeChecklist({ features: [completeFeature({ featureLabel: '' })] }),
    );
    expect(items).toContain('Feature 1 — Each feature must have a feature label.');
  });

  it('requires a feature type and an age', () => {
    const items = featuresOutstanding(
      completeChecklist({
        features: [completeFeature({ burialSite: 'false', pre1846: 'false' })],
      }),
    );
    expect(items).toEqual([
      'Feature 1 — Select at least one feature description.',
      'Feature 1 — Select at least one item for the Age of this feature.',
    ]);
  });

  it('asks a composite for at least two members', () => {
    const items = featuresOutstanding(
      completeChecklist({
        features: [completeFeature({ compositeFeatureInd: 'true' })],
      }),
    );
    expect(items[0]).toContain('A composite feature must include at least two features');
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
    expect(items).toEqual([
      'Feature 1 — Q1 is answered Yes — select at least one Q2 damage cause.',
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

  it('requires a permit number once the AIA permit box is checked', () => {
    const items = featuresOutstanding(
      completeChecklist({ features: [completeFeature({ sitePermitIssued: 'true' })] }),
    );
    expect(items[0]).toContain('permit number');
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
      'Feature 1 — “Other” management strategy “Fenced” is defined more than once — each description must be unique.',
      'Feature 1 — “Other” management strategy “Signed” must have a source (FN, AIA or SP).',
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
  it('reads a brand-new checklist as not started rather than as a fault', () => {
    const { statuses, counts } = chrTabStatuses({});
    expect(statuses.opening).toBe('empty');
    expect(statuses.blockSummary).toBe('empty');
    expect(statuses.features).toBe('empty');
    // The count is still there for the page to reveal once Submit has been pressed.
    expect(counts.opening).toBe(4);
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
      contacts: 'complete',
      features: 'complete',
      notes: 'complete',
      attachments: 'complete',
    });
  });

  it('treats Contacts and Notes as never outstanding — they have no submit rules', () => {
    const { statuses, outstanding } = chrTabStatuses({});
    expect(statuses.contacts).toBe('complete');
    expect(statuses.notes).toBe('complete');
    expect(outstanding.contacts).toEqual([]);
    expect(outstanding.notes).toEqual([]);
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

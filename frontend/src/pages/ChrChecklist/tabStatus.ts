import type { TabStatus } from '@/pages/ProtocolChecklist/tabStatus';
import type { CheckList, Feature, Picture } from '@/types/chrChecklist';

import {
  BLOCK_REQUIRED_LABELS,
  OPENING_REQUIRED_LABELS,
  blockSummaryRequiredErrors,
  blockSummaryTouched,
  openingRequiredErrors,
  openingTouched,
} from '@/pages/ChrChecklist/checklistValidation';
import { BORDEN_RE } from '@/pages/ChrChecklist/featureValidation';

/**
 * Per-tab completion state for the CHR checklist tab strip.
 *
 * The rules below mirror {@code ChrSubmitValidationService.validateBeforeSubmit} — the service that
 * actually blocks submit — so a tab only shows `complete` when it would not contribute a submit
 * error. They are deliberately a *read-only mirror*: the service stays authoritative, and a drift
 * between the two shows up as a dot that disagrees with the submit panel, never as a blocked save.
 *
 * Unlike the SLR equivalent this needs no requests at all: the whole CHR checklist — features
 * included — is already in client state, so every rule can be answered from what the page is
 * holding. That also means the dots keep working on an offline copy.
 *
 * See chr-submit-validation-rules.local.md for the same rules in prose.
 */

export type ChrTabKey =
  | 'opening'
  | 'blockSummary'
  | 'contacts'
  | 'features'
  | 'notes'
  | 'attachments';

export type ChrTabSnapshot = {
  statuses: Record<ChrTabKey, TabStatus>;
  counts: Record<ChrTabKey, number>;
  outstanding: Record<ChrTabKey, string[]>;
};

const has = (value?: string): boolean => value != null && `${value}`.trim() !== '';

const isYes = (value?: string): boolean => `${value ?? ''}`.trim().toLowerCase() === 'true';

/**
 * Read one field off a feature by name.
 *
 * By name rather than by property because the rules below work over groups of a dozen-plus
 * indicators at a time ("at least one feature type", "any FN strategy"), and the backend groups them
 * the same way. The names are the JSON ones — which are not always the Java field's spelling, e.g.
 * `culturaltraildesignated` and `cedarBarkStriparea`.
 */
const field = (f: Feature, key: string): string | undefined => {
  const value = (f as Record<string, unknown>)[key];
  return typeof value === 'string' ? value : undefined;
};

const anyYes = (f: Feature, keys: readonly string[]): boolean =>
  keys.some((key) => isYes(field(f, key)));

// Feature-description "type of feature(s)" group — at least one must be selected.
export const FEATURE_TYPE_FIELDS = [
  'culturaltraildesignated',
  'culturaltrailundesignated',
  'burialSite',
  'nest',
  'ceremonialSite',
  'cremationSite',
  'ofCMTs',
  'caveorotherKarst',
  'den',
  'traditionalUseSite',
  'cedarBarkStriparea',
  'rockOutcrop',
  'spiritualSite',
  'ofMonumentalCedars',
  'culturalDepression',
  'lithics',
  'other',
] as const;

export const AGE_FIELDS = ['pre1846', 'post1846', 'ageUnknown', 'historicalUse'] as const;

const Q2_CAUSE_FIELDS = [
  'harvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause',
  'safetyQ2Wheredamagehasoccurredwhatisthemostlikelycause',
  'silvicultureQ2Wheredamagehasoccurredwhatisthemostlikelycause',
  'recreationQ2Wheredamagehasoccurredwhatisthemostlikelycause',
  'fireQ2Wheredamagehasoccurredwhatisthemostlikelycause',
  'industrialUseQ2Wheredamagehasoccurredwhatisthemostlikelycause',
  'roadQ2Wheredamagehasoccurredwhatisthemostlikelycause',
  'livestockQ2Wheredamagehasoccurredwhatisthemostlikelycause',
  'windthrowQ2Wheredamagehasoccurredwhatisthemostlikelycause',
  'otherQ2Wheredamagehasoccurredwhatisthemostlikelycause',
] as const;

// Management strategies the "No management applied" box contradicts.
const USED_STRATEGY_FIELDS = [
  'partiallytemporaryreserve',
  'fullyconservedintemporaryreserve',
  'partiallyconservedinpermanentreserve',
  'fullyconservedinpermanentreserve',
  'modifiedblockboundary',
  'retainabuffer',
  'compledCrownorstandmodification',
  'datedthefeature',
  'retainedinharvestareanobuffer',
  'leftStanding',
  'stubbed',
  'alteredsilviculture',
  'otherActivities',
] as const;

// Windthrow techniques the "None" box contradicts.
const WINDTHROW_OTHER_FIELDS = [
  'windthrowTechniqueRetentionBuffer',
  'windthrowTechniquePruning',
  'windthrowTechniqueFeathering',
  'windthrowTechniqueTopping',
  'otherTechnique',
] as const;

// Planning strategies, by the source that recommended them. AIA has no group rule of its own — it is
// gated on the permit number instead.
const FN_STRATEGY_FIELDS = [
  'modifyBlockBoundaryFN',
  'retainBufferFN',
  'retaininHarvestAreaFN',
  'crownorstandmodificationFN',
  'conserveinRotationalReserveFN',
  'permanentReserveFN',
  'datetheFeatureFN',
  'stubCMTsabovescarFN',
  'stubnonCMTsFN',
  'leaveStandingFN',
  'machineFreeZoneFN',
  'harvestUnderSapFN',
  'winterHarvestFrozenGroundFN',
  'avoidSilvAvoidPlantingFN',
  'avoidSilvAvoidSitePrepFN',
] as const;

const SP_STRATEGY_FIELDS = FN_STRATEGY_FIELDS.map((key) =>
  key.replace(/FN$/, 'SP'),
) as readonly string[];

/**
 * How a feature is named in the banner: its label if it has one, else its position.
 *
 * A composite needs no special case — it is a feature with a real label, and the table shows that
 * same number, so "Feature 4" points at the row the reader can see.
 */
const featureName = (feature: Feature, index: number): string =>
  `Feature ${feature.featureLabel?.trim() || index + 1}`;

/**
 * Composite membership matches on the feature label, case-insensitively and trimmed — the same
 * comparison the backend makes, so a stray space typed into "Composite of" does not silently drop a
 * member here either.
 */
const matchesCompositeLabel = (featureLabel?: string, compositeReference?: string): boolean =>
  featureLabel != null &&
  compositeReference != null &&
  featureLabel.trim().toLowerCase() === compositeReference.trim().toLowerCase();

/** Whether any "Other" planned strategy on the feature is attributed to the given source. */
const anyOtherStrategySource = (feature: Feature, key: 'fnInd' | 'aiaInd' | 'spInd'): boolean =>
  (feature.otherPlannedManagementStrategy ?? []).some((strategy) => isYes(strategy[key]));

/** "Other" planned management strategy rules: description, source, and uniqueness within a feature. */
const otherStrategyItems = (feature: Feature): string[] => {
  const list = feature.otherPlannedManagementStrategy ?? [];
  const items: string[] = [];
  let reportedBlank = false;
  const reportedDuplicate: string[] = [];

  list.forEach((strategy) => {
    if (!has(strategy.otherStrategy)) {
      if (!reportedBlank) {
        items.push('Provide a description for any management strategies defined as “Other”.');
        reportedBlank = true;
      }
      return;
    }
    if (!(isYes(strategy.fnInd) || isYes(strategy.aiaInd) || isYes(strategy.spInd))) {
      items.push(
        `“Other” management strategy “${strategy.otherStrategy}” must have a source (FN, AIA or SP).`,
      );
      return;
    }
    const value = strategy.otherStrategy;
    const count = list.filter((other) => other.otherStrategy === value).length;
    if (count > 1 && !reportedDuplicate.includes(value as string)) {
      reportedDuplicate.push(value as string);
      items.push(
        `“Other” management strategy “${value}” is defined more than once — each description must be unique.`,
      );
    }
  });

  return items;
};

/** If a strategy is selected, its detail field must be filled in. */
const detailFor = (
  items: string[],
  selected: string | undefined,
  detail: string | undefined,
  message: string,
) => {
  if (isYes(selected) && !has(detail)) items.push(message);
};

/** If the answer is Yes, the description must be filled in. */
const describeIf = detailFor;

/** An entered value must parse as a whole number. Blank is a gap, handled by the rule above it. */
const wholeNumber = (items: string[], value: string | undefined, message: string) => {
  if (has(value) && !/^-?\d+$/.test(`${value}`.trim())) items.push(`${message} (${value})`);
};

/** Feature Description tab rules. */
const descriptionItems = (f: Feature): string[] => {
  const items: string[] = [];
  describeIf(
    items,
    f.ofCMTs,
    f.ofCMTsNumber,
    'Enter the number of CMT(s) included in the feature.',
  );
  describeIf(
    items,
    f.ofMonumentalCedars,
    f.standofMonumentalCedar,
    'Enter the number of Monumental Cedar(s) included in the feature.',
  );
  describeIf(items, f.other, f.otherdescription, 'Provide a description of the feature.');
  wholeNumber(items, f.ofCMTsNumber, 'Enter a valid number of CMT(s) included in the feature.');
  wholeNumber(
    items,
    f.standofMonumentalCedar,
    'Enter a valid number of Monumental Cedar(s) included in the feature.',
  );
  if (!anyYes(f, FEATURE_TYPE_FIELDS)) {
    items.push('Select at least one feature description.');
  }
  if (isYes(f.chrRegisteredSite) && has(f.borden) && !BORDEN_RE.test(f.borden as string)) {
    items.push('Provide a Borden # in the format AaBb-0, AaBb-00, AaBb-000 or AaBb-0000.');
  }
  return items;
};

/** Feature Location and Age tab rules. */
const locationAgeItems = (f: Feature): string[] => {
  const items: string[] = [];
  describeIf(
    items,
    f.locationOther,
    f.locationOtherDescription,
    'Provide a description of the location if Other.',
  );
  describeIf(items, f.inReserve, f.locationReservetype, 'Provide the In Reserve “Type”.');
  if (!anyYes(f, AGE_FIELDS)) items.push('Select at least one item for the Age of this feature.');
  return items;
};

/** Feature Planning tab rules — the same three follow-ups for each of FN, AIA and SP. */
const planningItems = (f: Feature): string[] => {
  const items: string[] = [];

  (['FN', 'AIA', 'SP'] as const).forEach((source) => {
    detailFor(
      items,
      field(f, `retainBuffer${source}`),
      field(f, `bufferLength${source}`),
      `Provide the buffer size in metres for the ${source} strategy.`,
    );
    detailFor(
      items,
      field(f, `conserveinRotationalReserve${source}`),
      field(f, `conserveRotationalReserveType${source}`),
      `Provide the Rotational Reserve “Type” for the ${source} strategy.`,
    );
    detailFor(
      items,
      field(f, `permanentReserve${source}`),
      field(f, `temporaryRetentionType${source}`),
      `Provide the Temporary Reserve Area “Type” for the ${source} strategy.`,
    );
    wholeNumber(
      items,
      field(f, `bufferLength${source}`),
      `Provide a valid ${source} buffer size in metres.`,
    );
  });

  items.push(...otherStrategyItems(f));

  if (
    isYes(f.managementStrategyFN) &&
    !(anyYes(f, FN_STRATEGY_FIELDS) || anyOtherStrategySource(f, 'fnInd'))
  ) {
    items.push(
      '“FN management recommendations provided” is checked, but no FN strategy is entered. Add one, or uncheck the box.',
    );
  }
  if (isYes(f.sitePermitIssued) && !has(f.permit)) {
    items.push(
      '“AIA / site-alteration permit issued” is checked, but no permit number has been entered. Enter it, or uncheck the box.',
    );
  }
  if (
    isYes(f.managementStrategySP) &&
    !(anyYes(f, SP_STRATEGY_FIELDS) || anyOtherStrategySource(f, 'spInd'))
  ) {
    items.push(
      '“Site plan strategies noted” is checked, but no site-plan strategy is entered. Add one, or uncheck the box.',
    );
  }

  return items;
};

/** Feature Effectiveness tab rules. */
const effectivenessItems = (f: Feature): string[] => {
  const items: string[] = [];
  describeIf(items, f.retainabuffer, f.bufferWidthMeter, 'Provide the buffer size in metres.');
  describeIf(
    items,
    f.partiallytemporaryreserve,
    f.partiallytemporaryreservetype,
    'Provide the Temporary Reserve “Type” for the partially conserved area.',
  );
  describeIf(
    items,
    f.fullyconservedintemporaryreserve,
    f.fullytemporaryreserve,
    'Provide the Temporary Reserve “Type” for the fully conserved area.',
  );
  describeIf(
    items,
    f.partiallyconservedinpermanentreserve,
    f.partiallyconservedinpermanentreserveType,
    'Provide the Permanent Reserve “Type” for the partially conserved area.',
  );
  describeIf(
    items,
    f.fullyconservedinpermanentreserve,
    f.fullyconservedinpermanentreserveType,
    'Provide the Permanent Reserve “Type” for the fully conserved area.',
  );
  describeIf(
    items,
    f.otherQ2Wheredamagehasoccurredwhatisthemostlikelycause,
    f.ifotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause,
    'Provide a description for the “Other” damage cause.',
  );
  describeIf(
    items,
    f.otherTechnique,
    f.ifotherpleasedescribe,
    'Provide a description for the “Other” windthrow management technique.',
  );
  describeIf(
    items,
    f.isthereevidenceofdamage,
    f.trailLength,
    'Provide an estimated percentage of the trail length affected.',
  );
  wholeNumber(items, f.bufferWidthMeter, 'Provide a valid buffer size in metres.');
  wholeNumber(
    items,
    f.trailLength,
    'Provide a valid estimated percentage of the trail length affected.',
  );

  if (isYes(f.noManagement) && anyYes(f, USED_STRATEGY_FIELDS)) {
    items.push(
      'Management strategies are selected while “No management applied” is checked. Clear the strategies, or uncheck the box.',
    );
  }
  if (isYes(f.q1Isthereevidenceofdamagetothesiteorfeature) && !anyYes(f, Q2_CAUSE_FIELDS)) {
    items.push('Q1 is answered Yes — select at least one Q2 damage cause.');
  }
  if (
    isYes(f.q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse) &&
    !anyYes(f, Q2_CAUSE_FIELDS)
  ) {
    items.push('Q3 is answered Yes — select at least one Q2 damage cause.');
  }
  if (
    isYes(f.windthrowManagement) &&
    isYes(f.windthrowTechniqueNone) &&
    anyYes(f, WINDTHROW_OTHER_FIELDS)
  ) {
    items.push(
      'Windthrow techniques are selected while “None” is checked. Clear the techniques, or uncheck “None”.',
    );
  }

  return items;
};

/** Feature Summary tab rules. */
const summaryItems = (f: Feature): string[] => {
  const items: string[] = [];
  if (!has(f.featureRating)) items.push('Provide a Rating in Feature Summary.');
  describeIf(
    items,
    f.q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature,
    f.q4Description,
    'Provide a description for Q4 in Feature Summary.',
  );
  describeIf(
    items,
    f.q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective,
    f.q5Description,
    'Provide a description for Q5 in Feature Summary.',
  );
  describeIf(
    items,
    f.q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature,
    f.q6Description,
    'Provide a description for Q6 in Feature Summary.',
  );
  return items;
};

/**
 * Everything one feature still owes, unprefixed.
 *
 * A feature that is a *member* of a composite is checked only for its label: everything else about
 * it is validated through the parent composite, so the same information is not demanded twice.
 */
const featureItems = (checkList: CheckList, feature: Feature): string[] => {
  const items: string[] = [];

  if (isYes(feature.compositeFeatureInd)) {
    const members = (checkList.features ?? []).filter((other) =>
      matchesCompositeLabel(feature.featureLabel, other.compositeFeature),
    ).length;
    if (members < 2) {
      items.push(
        'A composite feature must include at least two features. Open another feature, check “Composite feature”, and set its “Composite of (feature label)” to this feature’s label.',
      );
    }
  }

  if (!has(feature.featureLabel)) items.push('Each feature must have a feature label.');

  // Only an explicit "not a composite" owes these — the backend compares the indicator to the string
  // "false", so a feature that has never had the box touched is not asked for them either.
  if (feature.compositeFeatureInd === 'false') {
    if (!has(feature.featureDescriptionCode)) items.push('Provide a Description Code.');
    if (!has(feature.featureInfoSourceCode)) items.push('Provide an Information Source Code.');
  }

  if (has(feature.compositeFeature)) return items;

  items.push(
    ...descriptionItems(feature),
    ...locationAgeItems(feature),
    ...planningItems(feature),
    ...effectivenessItems(feature),
    ...summaryItems(feature),
  );
  return items;
};

/**
 * Required Opening fields still blank, named as the banner and the submit pre-flight list them.
 *
 * Listed in tab order, so the list reads top-to-bottom the way the user works down the form.
 */
export const openingOutstanding = (checkList?: CheckList | null): string[] => {
  const missing = openingRequiredErrors(checkList ?? {}, { includeReadOnly: true });
  return Object.keys(OPENING_REQUIRED_LABELS)
    .filter((key) => key in missing)
    .map((key) => OPENING_REQUIRED_LABELS[key]);
};

/** Required Block summary fields still blank, in tab order. */
export const blockSummaryOutstanding = (checkList?: CheckList | null): string[] => {
  const missing = blockSummaryRequiredErrors(checkList ?? {});
  return Object.keys(BLOCK_REQUIRED_LABELS)
    .filter((key) => key in missing)
    .map((key) => BLOCK_REQUIRED_LABELS[key]);
};

/**
 * Everything still outstanding on the Features tab, one line per rule, each prefixed with the
 * feature it belongs to.
 *
 * These are the submit rules, which are a superset of the field-level ones the feature editor marks
 * inline (see featureValidation.ts) — so a gap the editor flags with an asterisk is the same gap
 * counted here, listed once.
 */
export const featuresOutstanding = (checkList?: CheckList | null): string[] => {
  const features = checkList?.features ?? [];
  if (features.length === 0) return ['At least one feature is required before submit.'];
  return features.flatMap((feature, index) => {
    const name = featureName(feature, index);
    return featureItems(checkList as CheckList, feature).map((item) => `${name} — ${item}`);
  });
};

/**
 * Photo descriptions that are still missing.
 *
 * Only the photos the page is holding can be checked: online the Attachments tab reads one page at a
 * time, while submit validates every photo on the record. A description is required at upload (by
 * both the form and the API), so this can only catch a legacy row — the check is here so that when
 * one does turn up it is named rather than silently blocking submit, not as a promise that the rest
 * of the pages are clean.
 */
export const attachmentsOutstanding = (checkList?: CheckList | null): string[] =>
  (checkList?.pictures ?? [])
    .filter((picture: Picture) => !has(picture.description))
    .map((picture: Picture, index: number) => {
      const name = picture.fileName || `Photo ${index + 1}`;
      return `${name} — every photo requires a description.`;
    });

/**
 * Completion state for every CHR tab.
 *
 * Contacts and Notes have no submit rules at all, so they always read as complete — the indicator
 * answers "is anything outstanding?", and a tab that can never be outstanding is never incomplete.
 *
 * Opening info, Block summary and Features hold their count back until the tab has been started. A
 * brand-new checklist owes every required field, and greeting it with a red count reads as a fault
 * rather than as work not yet begun — the empty outline says the same thing without the alarm. Once
 * the evaluator has entered something (or has pressed Submit — see the page), the count is
 * information they asked for.
 */
export const chrTabStatuses = (checkList?: CheckList | null): ChrTabSnapshot => {
  const opening = openingOutstanding(checkList);
  const blockSummary = blockSummaryOutstanding(checkList);
  const features = featuresOutstanding(checkList);
  const attachments = attachmentsOutstanding(checkList);

  const started = (touched: boolean, items: string[]): TabStatus => {
    if (!touched) return 'empty';
    return items.length === 0 ? 'complete' : 'errors';
  };

  return {
    statuses: {
      opening: started(openingTouched(checkList), opening),
      blockSummary: started(blockSummaryTouched(checkList), blockSummary),
      contacts: 'complete',
      features: started((checkList?.features ?? []).length > 0, features),
      notes: 'complete',
      attachments: attachments.length === 0 ? 'complete' : 'errors',
    },
    counts: {
      opening: opening.length,
      blockSummary: blockSummary.length,
      contacts: 0,
      features: features.length,
      notes: 0,
      attachments: attachments.length,
    },
    outstanding: {
      opening,
      blockSummary,
      contacts: [],
      features,
      notes: [],
      attachments,
    },
  };
};

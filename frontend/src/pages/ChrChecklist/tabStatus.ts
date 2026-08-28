import type { OutstandingItem, TabStatus } from '@/pages/ProtocolChecklist/tabStatus';
import type { CheckList, Feature, Picture } from '@/types/chrChecklist';

import {
  BLOCK_REQUIRED_LABELS,
  BLOCK_REQUIRED_SECTIONS,
  OPENING_REQUIRED_LABELS,
  OPENING_REQUIRED_SECTIONS,
  blockSummaryFormatErrors,
  blockSummaryRequiredErrors,
  openingFormatErrors,
  openingRequiredErrors,
} from '@/pages/ChrChecklist/checklistValidation';
import { BORDEN_RE, featureBlockingErrors } from '@/pages/ChrChecklist/featureValidation';
import { FEATURE_TEXT_LIMITS } from '@/pages/ChrChecklist/textLimits';
import { flattenOutstanding, inFormSection } from '@/pages/ProtocolChecklist/tabStatus';

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
 * The count covers rules of both kinds. For features the submit rules are a superset of the
 * editor's own *required* checks, so those need no separate pass; what they do not cover is the
 * editor's *blocking* checks — a value already typed that the stored row could not survive. Those
 * are folded in by {@link featureItems}.
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
  /** Grouped for the per-tab panel — features carry their feature as the heading. */
  items: Record<ChrTabKey, OutstandingItem[]>;
  /** The same items flattened, for the submit pre-flight. */
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
/**
 * One outstanding rule on a feature, and the accordion section of the feature editor the reader
 * will find it in.
 *
 * The section travels with the rule rather than being inferred later, because the two are only
 * knowable together: the editor groups by subject, not by which validation produced the gap, and a
 * single rule group here can span three sections (see {@link effectivenessItems}).
 */
type FeatureRule = { text: string; section: string };

/**
 * How a rule reads in the panel: "Feature rating, in the Summary section".
 *
 * Naming the section is the whole point of the phrasing. A feature is edited through a nine-section
 * accordion with most sections collapsed, so "A rating is required" left the reader to open each one
 * in turn to find out where. The leading half names what is wanted — a field label where the gap is
 * a blank field, a short instruction where the rule is a contradiction rather than an omission.
 */
const inSection = ({ text, section }: FeatureRule): string => `${text}, in the ${section} section`;

/** "Other" planned management strategy rules: description, source, and uniqueness within a feature. */
const otherStrategyItems = (feature: Feature): FeatureRule[] => {
  const list = feature.otherPlannedManagementStrategy ?? [];
  const items: FeatureRule[] = [];
  let reportedBlank = false;
  const reportedDuplicate: string[] = [];

  list.forEach((strategy) => {
    if (!has(strategy.otherStrategy)) {
      if (!reportedBlank) {
        items.push({ text: 'Other strategy — describe it', section: 'Planning' });
        reportedBlank = true;
      }
      return;
    }
    if (!(isYes(strategy.fnInd) || isYes(strategy.aiaInd) || isYes(strategy.spInd))) {
      items.push({
        text: `Other strategy “${strategy.otherStrategy}” — tick FN, AIA/SAP or Site plan`,
        section: 'Planning',
      });
      return;
    }
    const value = strategy.otherStrategy;
    const count = list.filter((other) => other.otherStrategy === value).length;
    if (count > 1 && !reportedDuplicate.includes(value as string)) {
      reportedDuplicate.push(value as string);
      items.push({
        text: `Other strategy “${value}” is entered more than once — each must be unique`,
        section: 'Planning',
      });
    }
  });

  return items;
};

/** If a strategy is selected, its detail field must be filled in. */
const detailFor = (
  items: FeatureRule[],
  selected: string | undefined,
  detail: string | undefined,
  text: string,
  section: string,
) => {
  if (isYes(selected) && !has(detail)) items.push({ text, section });
};

/** If the answer is Yes, the description must be filled in. */
const describeIf = detailFor;

/** An entered value must parse as a whole number. Blank is a gap, handled by the rule above it. */
const wholeNumber = (
  items: FeatureRule[],
  value: string | undefined,
  label: string,
  section: string,
) => {
  if (has(value) && !/^-?\d+$/.test(`${value}`.trim())) {
    items.push({ text: `${label} must be a whole number (currently “${value}”)`, section });
  }
};

/** Feature Description section rules. */
const descriptionItems = (f: Feature): FeatureRule[] => {
  const items: FeatureRule[] = [];
  describeIf(items, f.ofCMTs, f.ofCMTsNumber, 'Number of CMTs', 'Description');
  describeIf(
    items,
    f.ofMonumentalCedars,
    f.standofMonumentalCedar,
    'Stand of monumental cedar',
    'Description',
  );
  describeIf(items, f.other, f.otherdescription, 'Other description', 'Description');
  wholeNumber(items, f.ofCMTsNumber, 'Number of CMTs', 'Description');
  wholeNumber(items, f.standofMonumentalCedar, 'Stand of monumental cedar', 'Description');
  if (!anyYes(f, FEATURE_TYPE_FIELDS)) {
    items.push({ text: 'Tick at least one type of feature', section: 'Description' });
  }
  if (isYes(f.chrRegisteredSite) && has(f.borden) && !BORDEN_RE.test(f.borden as string)) {
    items.push({
      text: 'Borden number must read AaBb-0, AaBb-00, AaBb-000 or AaBb-0000',
      section: 'Description',
    });
  }
  return items;
};

/** Feature Location and Age section rules. */
const locationAgeItems = (f: Feature): FeatureRule[] => {
  const items: FeatureRule[] = [];
  describeIf(
    items,
    f.locationOther,
    f.locationOtherDescription,
    'Other location description',
    'Location',
  );
  describeIf(items, f.inReserve, f.locationReservetype, 'Reserve type', 'Location');
  if (!anyYes(f, AGE_FIELDS)) items.push({ text: 'Select an age', section: 'Age' });
  return items;
};

/** Feature Planning section rules — the same three follow-ups for each of FN, AIA and SP. */
const planningItems = (f: Feature): FeatureRule[] => {
  const items: FeatureRule[] = [];

  (['FN', 'AIA', 'SP'] as const).forEach((source) => {
    detailFor(
      items,
      field(f, `retainBuffer${source}`),
      field(f, `bufferLength${source}`),
      `${source} buffer size in metres`,
      'Planning',
    );
    detailFor(
      items,
      field(f, `conserveinRotationalReserve${source}`),
      field(f, `conserveRotationalReserveType${source}`),
      `${source} rotational reserve type`,
      'Planning',
    );
    detailFor(
      items,
      field(f, `permanentReserve${source}`),
      field(f, `temporaryRetentionType${source}`),
      `${source} temporary reserve area type`,
      'Planning',
    );
    wholeNumber(items, field(f, `bufferLength${source}`), `${source} buffer size`, 'Planning');
  });

  items.push(...otherStrategyItems(f));

  if (
    isYes(f.managementStrategyFN) &&
    !(anyYes(f, FN_STRATEGY_FIELDS) || anyOtherStrategySource(f, 'fnInd'))
  ) {
    items.push({
      text: 'Add an FN strategy, or untick “FN management recommendations provided”',
      section: 'Planning',
    });
  }
  if (isYes(f.sitePermitIssued) && !has(f.permit)) {
    items.push({
      text: 'Permit number, or untick “AIA / site-alteration permit issued”',
      section: 'Planning',
    });
  }
  if (
    isYes(f.managementStrategySP) &&
    !(anyYes(f, SP_STRATEGY_FIELDS) || anyOtherStrategySource(f, 'spInd'))
  ) {
    items.push({
      text: 'Add a site-plan strategy, or untick “Site plan strategies noted”',
      section: 'Planning',
    });
  }

  return items;
};

/**
 * Feature Effectiveness rules — and the Damage and Windthrow rules that sit beside them.
 *
 * Three sections rather than one: the legacy screen kept damage and windthrow inside management
 * effectiveness, and the rules are still written as one chain, but the editor gives each its own
 * accordion section and that is where the reader has to go.
 */
const effectivenessItems = (f: Feature): FeatureRule[] => {
  const items: FeatureRule[] = [];
  describeIf(items, f.retainabuffer, f.bufferWidthMeter, 'Buffer size (m)', 'Effectiveness');
  describeIf(
    items,
    f.partiallytemporaryreserve,
    f.partiallytemporaryreservetype,
    'Partial temporary reserve type',
    'Effectiveness',
  );
  describeIf(
    items,
    f.fullyconservedintemporaryreserve,
    f.fullytemporaryreserve,
    'Full temporary reserve type',
    'Effectiveness',
  );
  describeIf(
    items,
    f.partiallyconservedinpermanentreserve,
    f.partiallyconservedinpermanentreserveType,
    'Partial permanent reserve type',
    'Effectiveness',
  );
  describeIf(
    items,
    f.fullyconservedinpermanentreserve,
    f.fullyconservedinpermanentreserveType,
    'Full permanent reserve type',
    'Effectiveness',
  );
  describeIf(
    items,
    f.otherQ2Wheredamagehasoccurredwhatisthemostlikelycause,
    f.ifotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause,
    'Other cause description',
    'Damage',
  );
  describeIf(
    items,
    f.otherTechnique,
    f.ifotherpleasedescribe,
    'Other technique description',
    'Windthrow',
  );
  describeIf(
    items,
    f.isthereevidenceofdamage,
    f.trailLength,
    'Estimated trail damage (%)',
    'Windthrow',
  );
  wholeNumber(items, f.bufferWidthMeter, 'Buffer size (m)', 'Effectiveness');
  wholeNumber(items, f.trailLength, 'Estimated trail damage (%)', 'Windthrow');

  if (isYes(f.noManagement) && anyYes(f, USED_STRATEGY_FIELDS)) {
    items.push({
      text: 'Clear the strategies, or untick “No management applied”',
      section: 'Effectiveness',
    });
  }
  if (isYes(f.q1Isthereevidenceofdamagetothesiteorfeature) && !anyYes(f, Q2_CAUSE_FIELDS)) {
    items.push({ text: 'Q1 is Yes — tick at least one Q2 cause', section: 'Damage' });
  }
  if (
    isYes(f.q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse) &&
    !anyYes(f, Q2_CAUSE_FIELDS)
  ) {
    items.push({ text: 'Q3 is Yes — tick at least one Q2 cause', section: 'Damage' });
  }
  if (
    isYes(f.windthrowManagement) &&
    isYes(f.windthrowTechniqueNone) &&
    anyYes(f, WINDTHROW_OTHER_FIELDS)
  ) {
    items.push({ text: 'Clear the techniques, or untick “None”', section: 'Windthrow' });
  }

  return items;
};

/** Feature Summary section rules. */
const summaryItems = (f: Feature): FeatureRule[] => {
  const items: FeatureRule[] = [];
  if (!has(f.featureRating)) items.push({ text: 'Feature rating', section: 'Summary' });
  describeIf(
    items,
    f.q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature,
    f.q4Description,
    'Q4 description',
    'Summary',
  );
  describeIf(
    items,
    f.q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective,
    f.q5Description,
    'Q5 description',
    'Summary',
  );
  describeIf(
    items,
    f.q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature,
    f.q6Description,
    'Q6 description',
    'Summary',
  );
  return items;
};

/**
 * Which section each length-limited free-text field is edited in, and how the editor labels it.
 *
 * Keyed the same way as {@link FEATURE_TEXT_LIMITS}, which is what the lookup below walks — a limit
 * added there without an entry here still reports, under its field key, rather than disappearing
 * from the list because nobody remembered to name it.
 */
const TEXT_LIMIT_FIELDS: Record<string, FeatureRule> = {
  featureDescription: { text: 'Feature description', section: 'Description' },
  descriptionofdamage: { text: 'Description of damage', section: 'Damage' },
  q4Description: { text: 'Q4 description', section: 'Summary' },
  q5Description: { text: 'Q5 description', section: 'Summary' },
  q6Description: { text: 'Q6 description', section: 'Summary' },
  featureRatingRationale: { text: 'Feature rating rationale', section: 'Summary' },
  featureComment: { text: 'Comments', section: 'Comments' },
};

const featureItems = (checkList: CheckList, feature: Feature): string[] => {
  // Free text past its byte-semantic column limit blocks the save and is invisible to the proc,
  // which never receives the row. Only the limits are taken, selected by field key rather than by
  // reading the message: every other blocking rule here (Borden format, the gated whole-number
  // counts) is already stated by the submit rules below, and taking the whole map would list each
  // of those twice in different words.
  const blocking = featureBlockingErrors(feature);
  const rules: FeatureRule[] = Object.keys(FEATURE_TEXT_LIMITS)
    .filter((key) => blocking[key])
    .map((key) => {
      const named = TEXT_LIMIT_FIELDS[key];
      return {
        text: `${named?.text ?? key} — ${blocking[key]}`,
        section: named?.section ?? 'Comments',
      };
    });

  if (isYes(feature.compositeFeatureInd)) {
    const members = (checkList.features ?? []).filter((other) =>
      matchesCompositeLabel(feature.featureLabel, other.compositeFeature),
    ).length;
    if (members < 2) {
      rules.push({
        text:
          'A composite needs at least two features — open another feature, tick “Composite feature”, ' +
          'and set its “Composite of (feature label)” to this one’s label',
        section: 'Description',
      });
    }
  }

  if (!has(feature.featureLabel)) {
    rules.push({ text: 'Feature label', section: 'Description' });
  }

  // Only an explicit "not a composite" owes these — the backend compares the indicator to the string
  // "false", so a feature that has never had the box touched is not asked for them either.
  //
  // Named as the feature editor labels them. The backend calls these columns DESCRIPTION_CODE and
  // INFO_SOURCE_CODE and this once repeated those, but no field on the form carries either name.
  if (feature.compositeFeatureInd === 'false') {
    if (!has(feature.featureDescriptionCode)) {
      rules.push({ text: 'Feature class', section: 'Description' });
    }
    if (!has(feature.featureInfoSourceCode)) {
      rules.push({ text: 'Information source', section: 'Description' });
    }
  }

  if (!has(feature.compositeFeature)) {
    rules.push(
      ...descriptionItems(feature),
      ...locationAgeItems(feature),
      ...planningItems(feature),
      ...effectivenessItems(feature),
      ...summaryItems(feature),
    );
  }

  return rules.map(inSection);
};

/**
 * Required Opening fields still blank, named as the banner and the submit pre-flight list them.
 *
 * Listed in tab order, so the list reads top-to-bottom the way the user works down the form.
 */
export const openingOutstandingItems = (checkList?: CheckList | null): OutstandingItem[] => {
  const missing = openingRequiredErrors(checkList ?? {}, { includeReadOnly: true });
  const items = Object.keys(OPENING_REQUIRED_LABELS)
    .filter((key) => key in missing)
    .map((key) => ({
      text: inFormSection(OPENING_REQUIRED_LABELS[key], OPENING_REQUIRED_SECTIONS[key]),
    }));
  // Over-limit free text blocks the save and never reaches the service, so nothing else reports it.
  Object.entries(openingFormatErrors(checkList ?? {})).forEach(([key, message]) =>
    items.push({
      text: `${inFormSection(OPENING_REQUIRED_LABELS[key] ?? key, OPENING_REQUIRED_SECTIONS[key])} — ${message}`,
    }),
  );
  return items;
};

export const openingOutstanding = (checkList?: CheckList | null): string[] =>
  flattenOutstanding(openingOutstandingItems(checkList));

/** Required Block summary fields still blank, in tab order. */
export const blockSummaryOutstandingItems = (checkList?: CheckList | null): OutstandingItem[] => {
  const missing = blockSummaryRequiredErrors(checkList ?? {});
  const items = Object.keys(BLOCK_REQUIRED_LABELS)
    .filter((key) => key in missing)
    .map((key) => ({
      text: inFormSection(BLOCK_REQUIRED_LABELS[key], BLOCK_REQUIRED_SECTIONS[key]),
    }));
  Object.entries(blockSummaryFormatErrors(checkList ?? {})).forEach(([key, message]) =>
    items.push({
      text: `${inFormSection(BLOCK_REQUIRED_LABELS[key] ?? key, BLOCK_REQUIRED_SECTIONS[key])} — ${message}`,
    }),
  );
  return items;
};

export const blockSummaryOutstanding = (checkList?: CheckList | null): string[] =>
  flattenOutstanding(blockSummaryOutstandingItems(checkList));

/**
 * Everything still outstanding on the Features tab, one line per rule, each prefixed with the
 * feature it belongs to.
 *
 * These are the submit rules, which are a superset of the field-level ones the feature editor marks
 * inline (see featureValidation.ts) — so a gap the editor flags with an asterisk is the same gap
 * counted here, listed once.
 */
export const featuresOutstandingItems = (checkList?: CheckList | null): OutstandingItem[] => {
  const features = checkList?.features ?? [];
  if (features.length === 0) return [{ text: 'At least one feature is required before submit.' }];
  return features.flatMap((feature, index) => {
    const group = featureName(feature, index);
    return featureItems(checkList as CheckList, feature).map((text) => ({ group, text }));
  });
};

export const featuresOutstanding = (checkList?: CheckList | null): string[] =>
  flattenOutstanding(featuresOutstandingItems(checkList));

/**
 * Photo descriptions that are still missing.
 *
 * Only the photos the page is holding can be checked: online the Attachments tab reads one page at a
 * time, while submit validates every photo on the record. A description is required at upload (by
 * both the form and the API), so this can only catch a legacy row — the check is here so that when
 * one does turn up it is named rather than silently blocking submit, not as a promise that the rest
 * of the pages are clean.
 */
export const attachmentsOutstandingItems = (checkList?: CheckList | null): OutstandingItem[] =>
  (checkList?.pictures ?? [])
    .filter((picture: Picture) => !has(picture.description))
    .map((picture: Picture, index: number) => ({
      group: picture.fileName || `Photo ${index + 1}`,
      text: 'every photo requires a description.',
    }));

export const attachmentsOutstanding = (checkList?: CheckList | null): string[] =>
  flattenOutstanding(attachmentsOutstandingItems(checkList));

/**
 * Completion state for every CHR tab.
 *
 * Contacts and Notes carry no rules of either kind — nothing on them is required to save, and
 * nothing on them blocks submit — so they report `none` and draw no indicator at all. Attachments
 * reports `none` too, by request: its one rule is a best-effort check over the photos this page
 * happens to be holding (see {@link attachmentsOutstandingItems}), which is too partial an answer to
 * put a number on. It keeps its items, so they still show in the tab's own panel and in the
 * page-level tally, and the submit pre-flight can still name the tab.
 *
 * Every count is reported from the first render, on a brand-new checklist as readily as on one
 * part-way through — see the note on `TabStatus` in the SLR module for why that changed.
 */
export const chrTabStatuses = (checkList?: CheckList | null): ChrTabSnapshot => {
  const opening = openingOutstandingItems(checkList);
  const blockSummary = blockSummaryOutstandingItems(checkList);
  const features = featuresOutstandingItems(checkList);
  const attachments = attachmentsOutstandingItems(checkList);

  const state = (items: OutstandingItem[]): TabStatus =>
    items.length === 0 ? 'complete' : 'errors';

  const items: Record<ChrTabKey, OutstandingItem[]> = {
    opening,
    blockSummary,
    contacts: [],
    features,
    notes: [],
    attachments,
  };

  return {
    statuses: {
      opening: state(opening),
      blockSummary: state(blockSummary),
      contacts: 'none',
      features: state(features),
      notes: 'none',
      attachments: 'none',
    },
    counts: {
      opening: opening.length,
      blockSummary: blockSummary.length,
      contacts: 0,
      features: features.length,
      notes: 0,
      attachments: attachments.length,
    },
    items,
    outstanding: {
      opening: flattenOutstanding(opening),
      blockSummary: flattenOutstanding(blockSummary),
      contacts: [],
      features: flattenOutstanding(features),
      notes: [],
      attachments: flattenOutstanding(attachments),
    },
  };
};

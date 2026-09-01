import { Add, TrashCan, WarningFilled } from '@carbon/icons-react';
import { Button, MultiSelect, RadioButton, RadioButtonGroup } from '@carbon/react';
import { useMemo, useState, type FC, type ReactNode } from 'react';

import {
  CodeSelect,
  IndicatorCheckbox,
  TextAreaField,
  TextField,
} from '@/pages/ChrChecklist/fields';
import RequiredLegend from '@/pages/ProtocolChecklist/RequiredLegend';
import { requiredLabel } from '@/utils/requiredLabel';

import type { Feature, Indicator, OtherPlannedManagementStrategy } from '@/types/chrChecklist';

import { useSettledFields } from '@/hooks/useSettledFields';
import {
  duplicateLabelError,
  featureErrors,
  featureTypingErrors,
} from '@/pages/ChrChecklist/featureValidation';
import { FEATURE_SINGLE_LINE_MAX, FEATURE_TEXT_LIMITS } from '@/pages/ChrChecklist/textLimits';
import {
  useFeatureClassCodes,
  useInformationSourceCodes,
  useRatingCodes,
  useReserveTypeCodes,
} from '@/pages/ChrChecklist/useChrCodeLists';
import useCodeList from '@/pages/ChrChecklist/useCodeList';
import API from '@/services/APIs';
import { errorsForSettledFields } from '@/utils/validation';

type PatchFn = (patch: Partial<Feature>) => void;

// Which accordion section each lightweight-validation field lives in, so a section header can show
// an error badge and stay expanded while any of its fields is invalid (otherwise a blocked Save
// leaves the error hidden inside a collapsed section). Sections not listed never carry these errors.
const SECTION_ERROR_FIELDS: Record<string, string[]> = {
  'Description': [
    'borden',
    'ofCMTsNumber',
    'standofMonumentalCedar',
    'otherdescription',
    'widthofFeature',
    'lengthofFeature',
    'areaofFeature',
  ],
  'Location': ['locationOtherDescription', 'locationReservetype'],
  'Effectiveness': ['bufferWidthMeter'],
  'Windthrow': ['estwindthrow'],
  'Trail features': ['trailLength'],
  'Summary': ['q4Description', 'q5Description', 'q6Description', 'featureRating'],
};

/** A management-strategy row rendered as FN / AIA / SP checkboxes. */
const PLANNING_STRATEGIES: Array<{
  label: string;
  fn: string;
  aia: string;
  sp: string;
}> = [
  {
    label: 'Modify block boundary',
    fn: 'modifyBlockBoundaryFN',
    aia: 'modifyBlockBoundaryAIA',
    sp: 'modifyBlockBoundarySP',
  },
  { label: 'Retain buffer', fn: 'retainBufferFN', aia: 'retainBufferAIA', sp: 'retainBufferSP' },
  {
    label: 'Retain in harvest area (no buffer)',
    fn: 'retaininHarvestAreaFN',
    aia: 'retaininHarvestAreaAIA',
    sp: 'retaininHarvestAreaSP',
  },
  {
    label: 'Crown/stand modification',
    fn: 'crownorstandmodificationFN',
    aia: 'crownorstandmodificationAIA',
    sp: 'crownorstandmodificationSP',
  },
  {
    label: 'Conserve in rotational reserve',
    fn: 'conserveinRotationalReserveFN',
    aia: 'conserveinRotationalReserveAIA',
    sp: 'conserveinRotationalReserveSP',
  },
  {
    label: 'Permanent / temporary reserve',
    fn: 'permanentReserveFN',
    aia: 'permanentReserveAIA',
    sp: 'permanentReserveSP',
  },
  {
    label: 'Date the feature',
    fn: 'datetheFeatureFN',
    aia: 'datetheFeatureAIA',
    sp: 'datetheFeatureSP',
  },
  {
    label: 'Stub CMTs above scar',
    fn: 'stubCMTsabovescarFN',
    aia: 'stubCMTsabovescarAIA',
    sp: 'stubCMTsabovescarSP',
  },
  { label: 'Stub non-CMTs', fn: 'stubnonCMTsFN', aia: 'stubnonCMTsAIA', sp: 'stubnonCMTsSP' },
  {
    label: 'Leave standing',
    fn: 'leaveStandingFN',
    aia: 'leaveStandingAIA',
    sp: 'leaveStandingSP',
  },
  {
    label: 'Avoid silviculture — planting',
    fn: 'avoidSilvAvoidPlantingFN',
    aia: 'avoidSilvAvoidPlantingAIA',
    sp: 'avoidSilvAvoidPlantingSP',
  },
  {
    label: 'Avoid silviculture — site prep',
    fn: 'avoidSilvAvoidSitePrepFN',
    aia: 'avoidSilvAvoidSitePrepAIA',
    sp: 'avoidSilvAvoidSitePrepSP',
  },
  {
    label: 'Machine-free zone',
    fn: 'machineFreeZoneFN',
    aia: 'machineFreeZoneAIA',
    sp: 'machineFreeZoneSP',
  },
  {
    label: 'Harvest under SAP',
    fn: 'harvestUnderSapFN',
    aia: 'harvestUnderSapAIA',
    sp: 'harvestUnderSapSP',
  },
  {
    label: 'Winter harvest / frozen ground',
    fn: 'winterHarvestFrozenGroundFN',
    aia: 'winterHarvestFrozenGroundAIA',
    sp: 'winterHarvestFrozenGroundSP',
  },
];

// Per-strategy conditional sub-fields (buffer length / reserve type) keyed by the row checkbox
// that reveals them, with the FN/AIA/SP variant field names. Rendered below the planning grid.
const BUFFER_LENGTH = {
  fn: { when: 'retainBufferFN', field: 'bufferLengthFN' },
  aia: { when: 'retainBufferAIA', field: 'bufferLengthAIA' },
  sp: { when: 'retainBufferSP', field: 'bufferLengthSP' },
} as const;
const ROTATIONAL_RESERVE = {
  fn: { when: 'conserveinRotationalReserveFN', field: 'conserveRotationalReserveTypeFN' },
  aia: { when: 'conserveinRotationalReserveAIA', field: 'conserveRotationalReserveTypeAIA' },
  sp: { when: 'conserveinRotationalReserveSP', field: 'conserveRotationalReserveTypeSP' },
} as const;
const TEMPORARY_RESERVE = {
  fn: { when: 'permanentReserveFN', field: 'temporaryRetentionTypeFN' },
  aia: { when: 'permanentReserveAIA', field: 'temporaryRetentionTypeAIA' },
  sp: { when: 'permanentReserveSP', field: 'temporaryRetentionTypeSP' },
} as const;

/** The four age indicators, as one question. Order follows the legacy screen. */
const AGE_OPTIONS: Array<{ label: string; field: string }> = [
  { label: 'Pre-1846', field: 'pre1846' },
  { label: 'Post-1846', field: 'post1846' },
  { label: 'Age unknown', field: 'ageUnknown' },
  { label: 'Historical use', field: 'historicalUse' },
];

const FEATURE_TYPES: Array<{ label: string; field: string }> = [
  { label: 'Cultural trail — designated', field: 'culturaltraildesignated' },
  { label: 'Cultural trail — undesignated', field: 'culturaltrailundesignated' },
  { label: 'Burial site', field: 'burialSite' },
  { label: 'Nest', field: 'nest' },
  { label: 'Ceremonial site', field: 'ceremonialSite' },
  { label: 'Cremation site', field: 'cremationSite' },
  { label: 'Cave or other karst', field: 'caveorotherKarst' },
  { label: 'Den', field: 'den' },
  { label: 'Traditional use site', field: 'traditionalUseSite' },
  { label: 'Cedar bark strip area', field: 'cedarBarkStriparea' },
  { label: 'Rock outcrop', field: 'rockOutcrop' },
  { label: 'Spiritual site', field: 'spiritualSite' },
  { label: 'Cultural depression', field: 'culturalDepression' },
  { label: 'Lithics', field: 'lithics' },
];

const DAMAGE_AGENTS: Array<{ label: string; field: string }> = [
  { label: 'Harvesting', field: 'harvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause' },
  { label: 'Safety', field: 'safetyQ2Wheredamagehasoccurredwhatisthemostlikelycause' },
  { label: 'Silviculture', field: 'silvicultureQ2Wheredamagehasoccurredwhatisthemostlikelycause' },
  { label: 'Recreation', field: 'recreationQ2Wheredamagehasoccurredwhatisthemostlikelycause' },
  { label: 'Fire', field: 'fireQ2Wheredamagehasoccurredwhatisthemostlikelycause' },
  {
    label: 'Industrial use',
    field: 'industrialUseQ2Wheredamagehasoccurredwhatisthemostlikelycause',
  },
  { label: 'Road', field: 'roadQ2Wheredamagehasoccurredwhatisthemostlikelycause' },
  { label: 'Livestock', field: 'livestockQ2Wheredamagehasoccurredwhatisthemostlikelycause' },
  { label: 'Windthrow', field: 'windthrowQ2Wheredamagehasoccurredwhatisthemostlikelycause' },
];

const OTHER_DAMAGE_AGENT = 'otherQ2Wheredamagehasoccurredwhatisthemostlikelycause';
const OTHER_DAMAGE_AGENT_DESCRIPTION =
  'ifotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause';

/* Q2 as one multi-select rather than ten checkboxes. Every cause is still its own indicator column;
   the picker is only how they are chosen. Ten boxes made Damage the longest section on the tab for
   a question most features answer with one cause, or none. */
const DAMAGE_CAUSE_ITEMS: Array<{ id: string; label: string }> = [
  ...DAMAGE_AGENTS,
  { label: 'Other', field: OTHER_DAMAGE_AGENT },
].map((d) => ({ id: d.field, label: d.label }));

const WINDTHROW_TECHNIQUES: Array<{ label: string; field: string }> = [
  { label: 'None', field: 'windthrowTechniqueNone' },
  { label: 'Retention buffer', field: 'windthrowTechniqueRetentionBuffer' },
  { label: 'Pruning', field: 'windthrowTechniquePruning' },
  { label: 'Feathering', field: 'windthrowTechniqueFeathering' },
  { label: 'Topping', field: 'windthrowTechniqueTopping' },
];

// Age is a single-select group (one age per feature); mirrors the backend AGE_FIELDS set.
const AGE_FIELDS = ['pre1846', 'post1846', 'ageUnknown', 'historicalUse'] as const;

// Monotonic key generator for the id-less, editable "other strategy" rows. A stable React key
// (never reused, never the array index) keeps row inputs from remounting/losing focus on edit.
let otherStrategyKeySeq = 0;
const nextOtherStrategyKey = () => {
  otherStrategyKeySeq += 1;
  return `other-strat-${otherStrategyKeySeq}`;
};

const FeatureEditor: FC<{
  feature: Feature;
  onPatch: PatchFn;
  readOnly: boolean;
  // Errors are computed live but only shown once a save has been attempted — FeatureList owns the
  // flag because it owns the Save button. Also gates the section error badges and the auto-open
  // effect below, so an untouched feature opens fully collapsed and quiet.
  showErrors?: boolean;
  /** How this feature is named elsewhere — "Feature 5" — shown as the form's heading. */
  title?: string;
  /** Labels held by the checklist's *other* features, for the uniqueness check on this one. */
  takenLabels?: readonly string[];
}> = ({ feature, onPatch, readOnly, showErrors = false, title, takenLabels = [] }) => {
  const ind = (field: string): Indicator | undefined => feature[field] as Indicator | undefined;
  const str = (field: string): string | undefined => feature[field] as string | undefined;
  const on = (field: string): boolean => ind(field) === 'true';

  /**
   * Q3's answers, from FREP_CHECKLIST_ANSWER_CODE rather than a list in this file.
   *
   * "NA" is excluded, as the SLR opening tab does: Q3 asks a Yes/No question with a "Don't know",
   * and legacy's radio offered exactly those three. The proc orders by description descending,
   * which lands them Yes / No / Don't Know — the order the mockup asks for, and legacy's own.
   */
  const q3Options = useCodeList('checklist-answers:NA', () =>
    API.configuration.getChecklistAnswers('NA'),
  );

  // The remaining dropdowns, from their own code tables. Order and label shape live in
  // useChrCodeLists; the codes themselves are the table's.
  const featureClassCodes = useFeatureClassCodes();
  const informationSourceCodes = useInformationSourceCodes();
  const reserveTypeCodes = useReserveTypeCodes();
  const ratingCodes = useRatingCodes();

  // An explicit "not a composite". The submit rule compares the indicator to the string "false", so
  // a feature whose composite box has never been touched owes neither code — and must not be
  // marked as if it did.
  const notComposite = feature.compositeFeatureInd === 'false';
  const chk = (field: string, label: string) => (
    <IndicatorCheckbox
      // Keyed because several groups render this straight from a `.map` (feature types, damage
      // agents, windthrow techniques) — React warned on each of those lists.
      key={field}
      id={`feat-${field}`}
      labelText={label}
      value={ind(field)}
      disabled={readOnly}
      onToggle={(v) => onPatch({ [field]: v })}
    />
  );

  // "Other activities" has no indicator column of its own — the strategy is stored as an OTH row
  // carrying the description, so text present is the tick. The box is held here so it can stay
  // ticked while the description is still being typed.
  const [otherActivitiesOn, setOtherActivitiesOn] = useState<boolean>(() =>
    Boolean(str('otherActivities')),
  );

  // Age is single-select: a feature has one age, stored as four indicator columns. Asked as radios,
  // so choosing a different age just moves the selection — the four checkboxes it replaced disabled
  // each other once one was ticked, and switching meant unticking the old one first.
  const selectedAge = AGE_FIELDS.find((f) => on(f));
  const selectAge = (field: string) =>
    onPatch(
      Object.fromEntries(AGE_FIELDS.map((f) => [f, f === field ? 'true' : 'false'])) as Partial<
        Record<(typeof AGE_FIELDS)[number], string>
      >,
    );

  // Size-of-area unit toggle (legacy `metersOrBuffer`): UI-only, derived from the data — an area
  // value means hectares, a width or length means metres. Switching units clears the other set of
  // fields (legacy `reinitUntis`).
  //
  // Unset on a feature that records neither, so the fields stay closed until the question has been
  // answered: defaulting to metres put an empty Width and Length on every new feature, which reads
  // as two fields owed rather than a choice not yet made.
  const [areaUnit, setAreaUnit] = useState<'metres' | 'hectares' | ''>(() => {
    if (str('areaofFeature')) return 'hectares';
    return str('widthofFeature') || str('lengthofFeature') ? 'metres' : '';
  });
  const switchUnit = (unit: 'metres' | 'hectares') => {
    setAreaUnit(unit);
    onPatch(
      unit === 'hectares' ? { widthofFeature: '', lengthofFeature: '' } : { areaofFeature: '' },
    );
  };

  // Live inline validation (lightweight high-value subset; the full rule set runs server-side at
  // submit). Empty when read-only. Save is blocked in FeatureList while any error remains. Memoised
  // on the feature so the auto-open effect below only re-runs when the data actually changes.
  //
  // Before the first save attempt only the typing-safe subset is shown: a value that no further
  // typing can rescue — a letter in a number, a decimal place too many, a figure above the maximum —
  // is wrong the moment it is on screen, and waiting for Save to say so sends the user back to a
  // field they had finished with. Everything else (blank required fields, and the rules a
  // half-finished value trips on its way to being right) waits for Save.
  //
  // Between the two sits the field the user has filled in and moved on from: once it has been left,
  // the rules a half-finished value would have tripped can be judged, so they are shown then rather
  // than held to Save. Only for a field that holds a value — see errorsForSettledFields.
  const { settled, markSettled } = useSettledFields();
  const fieldErrors: Record<string, string> = useMemo(
    () => ({
      ...(readOnly
        ? {}
        : showErrors
          ? featureErrors(feature)
          : {
              ...featureTypingErrors(feature),
              ...errorsForSettledFields(
                featureErrors(feature),
                settled,
                (key) => feature[key] as string | undefined,
              ),
            }),
      // Shown as soon as it is true, without waiting for a save attempt: a duplicate label is a
      // clash with another record rather than a field left unfinished, and the user is typing the
      // very value that clashes. Holding it back until Save meant retyping a label they had already
      // moved on from.
      ...(readOnly ? {} : duplicateLabelError(feature, takenLabels)),
    }),
    [readOnly, showErrors, feature, takenLabels, settled],
  );
  const err = (key: string): string | undefined => fieldErrors[key];

  // The sections are laid out one after another, ruled apart, rather than folded into an accordion.
  // Nothing to open or close, so the open-state bookkeeping is gone with it — including the effect
  // that force-opened a section holding an error, and the stickiness that stopped it snapping shut
  // again the moment the field was filled. A section that holds an error still says so, in its
  // heading.
  const sectionErrorCount = (section: string): number =>
    (SECTION_ERROR_FIELDS[section] ?? []).filter((key) => Boolean(fieldErrors[key])).length;
  const sectionTitle = (section: string): ReactNode => {
    const count = sectionErrorCount(section);
    if (count === 0) return section;
    return (
      <span className="feature-accordion__title">
        <span>{section}</span>
        <WarningFilled size={16} className="feature-accordion__error-icon" />
      </span>
    );
  };

  // Set from the feature table, never from here — the Effectiveness tab still asks a
  // composite-only question.
  const isComposite = on('compositeFeatureInd');

  // --- Planning: column visibility + the "Other management strategies" dynamic list ---
  // Which planning columns are in play. Each is a source of recommendations, and the table only
  // carries the ones this feature actually has: an AIA column stood on every feature regardless,
  // asking for strategies from an assessment that may never have happened.
  const showFN = on('managementStrategyFN');
  const showSP = on('managementStrategySP');
  const showAIA = on('sitePermitIssued');
  const recommendationsEnabled = showFN || showSP || showAIA;

  /** The source columns in play, in the order the planning table lists them. */
  const SOURCE_COLUMNS: Array<{
    key: string;
    label: string;
    ind: 'fnInd' | 'aiaInd' | 'spInd';
    shown: boolean;
  }> = [
    { key: 'fn', label: 'FN', ind: 'fnInd', shown: showFN },
    { key: 'aia', label: 'AIA/SAP', ind: 'aiaInd', shown: showAIA },
    { key: 'sp', label: 'Site plan', ind: 'spInd', shown: showSP },
  ];

  const strategies = feature.otherPlannedManagementStrategy ?? [];
  // Stable React keys for the id-less strategy rows, kept positionally in sync with the list.
  // (FeatureEditor remounts per feature, so this initializes per feature.)
  const [strategyKeys, setStrategyKeys] = useState<string[]>(() =>
    strategies.map(() => nextOtherStrategyKey()),
  );
  const patchStrategy = (index: number, patch: Partial<OtherPlannedManagementStrategy>) =>
    onPatch({
      otherPlannedManagementStrategy: strategies.map((s, i) =>
        i === index ? { ...s, ...patch } : s,
      ),
    });
  const addStrategy = () => {
    setStrategyKeys((keys) => [...keys, nextOtherStrategyKey()]);
    onPatch({
      otherPlannedManagementStrategy: [
        ...strategies,
        { otherStrategy: '', fnInd: 'false', aiaInd: 'false', spInd: 'false' },
      ],
    });
  };
  const removeStrategy = (index: number) => {
    setStrategyKeys((keys) => keys.filter((_, i) => i !== index));
    onPatch({ otherPlannedManagementStrategy: strategies.filter((_, i) => i !== index) });
  };

  // Buffer-length / reserve-type sub-field for one of the FN/AIA/SP columns — shown only when the
  // column is visible and the matching strategy row is checked.
  const subField = (
    variant: 'fn' | 'aia' | 'sp',
    def: { when: string; field: string },
    label: string,
    kind: 'buffer' | 'reserve',
  ) => {
    const columnVisible = { fn: showFN, aia: showAIA, sp: showSP }[variant];
    if (!columnVisible || !on(def.when)) return null;
    return kind === 'buffer' ? (
      <TextField
        key={def.field}
        id={`feat-${def.field}`}
        labelText={label}
        value={str(def.field)}
        disabled={readOnly}
        onChange={(v) => onPatch({ [def.field]: v })}
      />
    ) : (
      <CodeSelect
        key={def.field}
        id={`feat-${def.field}`}
        labelText={label}
        value={str(def.field)}
        options={reserveTypeCodes}
        disabled={readOnly}
        onChange={(v) => onPatch({ [def.field]: v })}
      />
    );
  };

  const variantLabel = { fn: 'FN', aia: 'AIA', sp: 'SP' } as const;
  /**
   * Untick a planning source and everything recorded under it goes with it.
   *
   * The column disappears when its source is unticked, so anything left behind would be invisible
   * and still counted — a strategy the feature no longer claims, a buffer length with no column to
   * show it, a permit number for a permit that is not issued. Clearing on the way out keeps what is
   * stored to what the form can show.
   *
   * Ticking the box again starts the column empty rather than restoring the old answers. That is
   * the honest reading: the source has been re-asserted, but nothing has said the same strategies
   * still apply.
   */
  const clearSource = (variant: 'fn' | 'aia' | 'sp'): Partial<Feature> => {
    const cleared: Record<string, unknown> = {};
    PLANNING_STRATEGIES.forEach((row) => {
      cleared[row[variant]] = 'false';
    });
    [BUFFER_LENGTH, ROTATIONAL_RESERVE, TEMPORARY_RESERVE].forEach((map) => {
      cleared[map[variant].field] = '';
    });
    if (variant === 'aia') cleared.permit = '';
    // The free-text strategies are shared across the three columns, so the rows stay and only this
    // column's tick is dropped.
    cleared.otherPlannedManagementStrategy = strategies.map((row) => ({
      ...row,
      [`${variant}Ind`]: 'false',
    }));
    return cleared as Partial<Feature>;
  };

  /** A planning-source checkbox: ticking reveals its column, unticking clears it. */
  const sourceChk = (field: string, label: string, variant: 'fn' | 'aia' | 'sp') => (
    <IndicatorCheckbox
      id={`feat-${field}`}
      labelText={label}
      value={ind(field)}
      disabled={readOnly}
      onToggle={(v) =>
        onPatch(v === 'true' ? { [field]: v } : { [field]: v, ...clearSource(variant) })
      }
    />
  );

  /**
   * Deal cells into fixed columns, round-robin.
   *
   * Fixed in markup on purpose: a grid row is as tall as its tallest cell, so one ticked box
   * opening a field left a hole beside every checkbox sharing its row; and CSS multi-column
   * balances its content, so opening a field re-flowed items and boxes jumped as they were ticked.
   * Dealing here pins every box to a column, and each column stacks on its own.
   *
   * Round-robin rather than sliced into thirds, so the reading order runs across the row as the
   * list is written — which is the order these have always been presented in.
   */
  const dealColumns = (cells: ReactNode[], count = 3): ReactNode[][] =>
    Array.from({ length: count }, (_, column) =>
      cells.filter((_cell, index) => index % count === column),
    );

  /**
   * The feature-type checkboxes, dealt into three fixed columns.
   *
   * Round-robin rather than sliced into thirds, so the reading order runs across the row as the
   * list is written — which is the order the tab has always presented these in. The three that ask
   * a follow-up come last and carry it inside their own cell.
   */
  const typeCells: ReactNode[] = [
    ...FEATURE_TYPES.map((t) => (
      <div className="chr-checklist__type-cell" key={t.field}>
        {chk(t.field, t.label)}
      </div>
    )),
    <div className="chr-checklist__type-cell" key="ofCMTs">
      {chk('ofCMTs', 'CMTs')}
      {on('ofCMTs') && (
        <TextField
          id="feat-cmt-num"
          labelText={requiredLabel('Number of CMTs', true)}
          value={str('ofCMTsNumber')}
          disabled={readOnly}
          invalid={Boolean(err('ofCMTsNumber'))}
          invalidText={err('ofCMTsNumber')}
          onChange={(v) => onPatch({ ofCMTsNumber: v })}
        />
      )}
    </div>,
    <div className="chr-checklist__type-cell" key="ofMonumentalCedars">
      {chk('ofMonumentalCedars', 'Monumental cedars')}
      {on('ofMonumentalCedars') && (
        <TextField
          id="feat-mon-num"
          labelText={requiredLabel('Stand of monumental cedar', true)}
          value={str('standofMonumentalCedar')}
          disabled={readOnly}
          invalid={Boolean(err('standofMonumentalCedar'))}
          invalidText={err('standofMonumentalCedar')}
          onChange={(v) => onPatch({ standofMonumentalCedar: v })}
        />
      )}
    </div>,
    <div className="chr-checklist__type-cell" key="other">
      {chk('other', 'Other')}
      {on('other') && (
        <TextField
          id="feat-other-desc"
          labelText={requiredLabel('Description', true)}
          value={str('otherdescription')}
          disabled={readOnly}
          maxLength={FEATURE_SINGLE_LINE_MAX.otherdescription}
          invalid={Boolean(err('otherdescription'))}
          invalidText={err('otherdescription')}
          onChange={(v) => onPatch({ otherdescription: v })}
        />
      )}
    </div>,
  ];
  const typeColumns = dealColumns(typeCells);

  /** The location checkboxes, in the same fixed columns — two of them open a follow-up. */
  const locationCells: ReactNode[] = [
    ...(
      [
        ['inharvestedarea', 'In harvested area'],
        ['adjacenttoblock', 'Adjacent to block'],
        ['adjacenttowater', 'Adjacent to water'],
        ['entirecutblock', 'Entire cut block'],
      ] as const
    ).map(([field, label]) => (
      <div className="chr-checklist__type-cell" key={field}>
        {chk(field, label)}
      </div>
    )),
    <div className="chr-checklist__type-cell" key="locationOther">
      {chk('locationOther', 'Other location')}
      {on('locationOther') && (
        <TextField
          id="feat-loc-other"
          labelText={requiredLabel('Description', true)}
          value={str('locationOtherDescription')}
          disabled={readOnly}
          maxLength={FEATURE_SINGLE_LINE_MAX.locationOtherDescription}
          invalid={Boolean(err('locationOtherDescription'))}
          invalidText={err('locationOtherDescription')}
          onChange={(v) => onPatch({ locationOtherDescription: v })}
        />
      )}
    </div>,
    <div className="chr-checklist__type-cell" key="inReserve">
      {chk('inReserve', 'In reserve')}
      {on('inReserve') && (
        <CodeSelect
          id="feat-reserve"
          labelText={requiredLabel('Reserve type', true)}
          value={str('locationReservetype')}
          options={reserveTypeCodes}
          disabled={readOnly}
          invalid={Boolean(err('locationReservetype'))}
          invalidText={err('locationReservetype')}
          onChange={(v) => onPatch({ locationReservetype: v })}
        />
      )}
    </div>,
  ];
  const locationColumns = dealColumns(locationCells);

  /** A plain effectiveness strategy — no follow-up of its own. */
  const effCell = (field: string, label: string): ReactNode => (
    <div className="chr-checklist__type-cell" key={field}>
      {chk(field, label)}
    </div>
  );

  /** A strategy whose tick asks for a reserve type. */
  const reserveCell = (
    field: string,
    label: string,
    id: string,
    typeField: string,
    typeLabel: string,
  ): ReactNode => (
    <div className="chr-checklist__type-cell" key={field}>
      {chk(field, label)}
      {on(field) && (
        <CodeSelect
          id={id}
          labelText={requiredLabel(typeLabel, true)}
          value={str(typeField)}
          options={reserveTypeCodes}
          disabled={readOnly}
          invalid={Boolean(err(typeField))}
          invalidText={err(typeField)}
          onChange={(v) => onPatch({ [typeField]: v })}
        />
      )}
    </div>
  );

  /**
   * The effectiveness strategies, in the order the columns read.
   *
   * "Other activities" is a checkbox over a free-text column: there is no separate indicator for it
   * — the strategy is stored as an OTH row carrying the description, so text present *is* the tick.
   * Unticking clears the text, which is what removes the row.
   */
  const effectivenessCells: ReactNode[] = [
    <div className="chr-checklist__type-cell" key="locate">
      {isComposite
        ? chk('forCompositeFeaturesInd', 'Same strategy for all features (composite)')
        : chk('unabletoLocate', 'Unable to locate feature')}
    </div>,
    effCell('noManagement', 'No management applied'),
    reserveCell(
      'partiallytemporaryreserve',
      'Partially conserved in temporary reserve',
      'feat-eff-parttemp',
      'partiallytemporaryreservetype',
      'Partial temporary reserve type',
    ),
    reserveCell(
      'fullyconservedintemporaryreserve',
      'Fully conserved in temporary reserve',
      'feat-eff-fulltemp',
      'fullytemporaryreserve',
      'Full temporary reserve type',
    ),
    reserveCell(
      'partiallyconservedinpermanentreserve',
      'Partially conserved in permanent reserve',
      'feat-eff-partperm',
      'partiallyconservedinpermanentreserveType',
      'Partial permanent reserve type',
    ),
    reserveCell(
      'fullyconservedinpermanentreserve',
      'Fully conserved in permanent reserve',
      'feat-eff-fullperm',
      'fullyconservedinpermanentreserveType',
      'Full permanent reserve type',
    ),
    effCell('modifiedblockboundary', 'Modified block boundary'),
    <div className="chr-checklist__type-cell" key="retainabuffer">
      {chk('retainabuffer', 'Retained a buffer')}
      {on('retainabuffer') && (
        <TextField
          id="feat-eff-buffer"
          labelText={requiredLabel('Buffer width (m)', true)}
          value={str('bufferWidthMeter')}
          disabled={readOnly}
          invalid={Boolean(err('bufferWidthMeter'))}
          invalidText={err('bufferWidthMeter')}
          onChange={(v) => onPatch({ bufferWidthMeter: v })}
        />
      )}
    </div>,
    effCell('compledCrownorstandmodification', 'Completed crown/stand modification'),
    effCell('datedthefeature', 'Dated the feature'),
    effCell('retainedinharvestareanobuffer', 'Retained in harvest area (no buffer)'),
    effCell('leftStanding', 'Left standing'),
    effCell('stubbed', 'Stubbed (CMT)'),
    effCell('stubbedNon', 'Stubbed (non-CMT)'),
    effCell('avoidSilvAvoidPlanting', 'Avoided planting'),
    effCell('avoidSilvAvoidSitePrep', 'Avoided site prep'),
    effCell('machineFreeZone', 'Machine-free zone'),
    effCell('harvestUnderSap', 'Harvest under SAP'),
    effCell('winterHarvestFrozenGround', 'Winter harvest / frozen ground'),
    <div className="chr-checklist__type-cell" key="otherActivities">
      <IndicatorCheckbox
        id="feat-eff-other-on"
        labelText="Other activities"
        value={otherActivitiesOn ? 'true' : 'false'}
        disabled={readOnly}
        onToggle={(v) => {
          setOtherActivitiesOn(v === 'true');
          if (v !== 'true') onPatch({ otherActivities: '' });
        }}
      />
      {otherActivitiesOn && (
        <TextField
          id="feat-eff-other"
          labelText={requiredLabel('Description', true)}
          value={str('otherActivities')}
          maxLength={FEATURE_SINGLE_LINE_MAX.otherActivities}
          disabled={readOnly}
          onChange={(v) => onPatch({ otherActivities: v })}
        />
      )}
    </div>,
  ];
  const effectivenessColumns = dealColumns(effectivenessCells);

  const planningSubFields = (['fn', 'aia', 'sp'] as const).flatMap((v) => [
    subField(v, BUFFER_LENGTH[v], `Buffer length — ${variantLabel[v]} (m)`, 'buffer'),
    subField(v, ROTATIONAL_RESERVE[v], `Rotational reserve type — ${variantLabel[v]}`, 'reserve'),
    subField(v, TEMPORARY_RESERVE[v], `Reserve type — ${variantLabel[v]}`, 'reserve'),
  ]);
  const hasPlanningSubFields = planningSubFields.some(Boolean);

  return (
    <div className="feature-sections">
      {/* The feature's own name, so the form says which of a list of features is open — the tab
          strip above it names the tab, not the record. */}
      {title && <h2 className="feature-sections__title">{title}</h2>}
      {/* Only while editable: a submitted feature is read-only, and the key belongs with fields that
          can still be filled in. */}
      {!readOnly && <RequiredLegend />}
      <section className="feature-section">
        <h3 className="feature-section__title">{sectionTitle('Description')}</h3>
        {/* No legend of its own: the section heading above already says Description, and a fieldset
            labelled "Feature description" directly beneath it named the same group twice. Kept as a
            plain group — the heading is what labels these fields now. */}
        <div className="rip-form__group">
          {/* All three on one row, each at the width its own content needs — a five-character label
              beside two selects whose option text runs to fifty. See `__description-row`. */}
          <div className="chr-checklist__description-row">
            <div className="chr-checklist__short-field">
              <TextField
                id="feat-label"
                labelText={requiredLabel('Feature label', true)}
                value={str('featureLabel')}
                maxLength={FEATURE_SINGLE_LINE_MAX.featureLabel}
                disabled={readOnly}
                invalid={Boolean(err('featureLabel'))}
                invalidText={err('featureLabel')}
                onChange={(v) => onPatch({ featureLabel: v })}
              />
            </div>
            <CodeSelect
              id="feat-class"
              // Owed only by an explicit non-composite, exactly as the submit rule asks for it: a
              // composite is described through its members, and a feature whose composite box has
              // never been touched is not asked either.
              labelText={requiredLabel('Feature class', notComposite)}
              value={str('featureDescriptionCode')}
              options={featureClassCodes}
              includeBlank
              disabled={readOnly}
              onChange={(v) => onPatch({ featureDescriptionCode: v })}
            />
            <CodeSelect
              id="feat-source"
              labelText={requiredLabel('Information source', notComposite)}
              value={str('featureInfoSourceCode')}
              options={informationSourceCodes}
              includeBlank
              disabled={readOnly}
              onChange={(v) => onPatch({ featureInfoSourceCode: v })}
            />
            {/* Its own full-width row directly under label/class/source. The grid auto-flows, so
                left at the end of the list this landed in whatever cell was free — wedged beside the
                composite hint and the registered-site checkbox. */}
          </div>
          <div className="rip-form__grid rip-form__grid--wide">
            <div className="chr-form__full-row">
              <TextAreaField
                id="feat-desc"
                labelText="Feature description"
                value={str('featureDescription')}
                disabled={readOnly}
                limit={FEATURE_TEXT_LIMITS.featureDescription}
                invalid={Boolean(err('featureDescription'))}
                invalidText={err('featureDescription')}
                onChange={(v) => onPatch({ featureDescription: v })}
              />
            </div>
            {/* Asked as a question with an explicit No, not a lone tick: an unticked box says
                nothing about whether the site is registered or the question was simply skipped.
                The Borden number sits under the answer that reveals it, in the same cell — beside
                it, the field read as a separate question rather than a follow-up to this one. */}
            <div className="chr-checklist__question">
              <RadioButtonGroup
                legendText="Is this a registered archaeological site?"
                name="feat-registered-site"
                valueSelected={
                  ind('chrRegisteredSite') === 'true'
                    ? 'yes'
                    : ind('chrRegisteredSite') === 'false'
                      ? 'no'
                      : ''
                }
                disabled={readOnly}
                onChange={(v) => onPatch({ chrRegisteredSite: v === 'yes' ? 'true' : 'false' })}
              >
                <RadioButton labelText="Yes" value="yes" id="feat-registered-yes" />
                <RadioButton labelText="No" value="no" id="feat-registered-no" />
              </RadioButtonGroup>
              {on('chrRegisteredSite') && (
                <TextField
                  id="feat-borden"
                  labelText="Borden number"
                  value={str('borden')}
                  disabled={readOnly}
                  maxLength={9}
                  helperText="Format: AaBb-0000"
                  invalid={Boolean(err('borden'))}
                  invalidText={err('borden')}
                  onBlur={() => markSettled('borden')}
                  onChange={(v) => onPatch({ borden: v })}
                />
              )}
            </div>
          </div>
        </div>

        <fieldset className="rip-form__group">
          <legend>Size of area influenced</legend>
          <RadioButtonGroup
            legendText="Select how the area was measured"
            name="feat-area-unit"
            valueSelected={areaUnit}
            disabled={readOnly}
            onChange={(v) => switchUnit(v as 'metres' | 'hectares')}
          >
            {/* The units name what they ask for: metres wants two sides, hectares one total. */}
            <RadioButton labelText="Metres (width × length)" value="metres" id="feat-area-metres" />
            <RadioButton
              labelText="Hectares (total area)"
              value="hectares"
              id="feat-area-hectares"
            />
          </RadioButtonGroup>
          {areaUnit !== '' && (
            <div className="rip-form__grid">
              {areaUnit === 'metres' ? (
                <>
                  <TextField
                    id="feat-width"
                    labelText="Width (m)"
                    value={str('widthofFeature')}
                    disabled={readOnly}
                    invalid={Boolean(err('widthofFeature'))}
                    invalidText={err('widthofFeature')}
                    onChange={(v) => onPatch({ widthofFeature: v })}
                  />
                  <TextField
                    id="feat-length"
                    labelText="Length (m)"
                    value={str('lengthofFeature')}
                    disabled={readOnly}
                    invalid={Boolean(err('lengthofFeature'))}
                    invalidText={err('lengthofFeature')}
                    onChange={(v) => onPatch({ lengthofFeature: v })}
                  />
                </>
              ) : (
                <TextField
                  id="feat-area"
                  labelText="Area (ha)"
                  value={str('areaofFeature')}
                  disabled={readOnly}
                  invalid={Boolean(err('areaofFeature'))}
                  invalidText={err('areaofFeature')}
                  onChange={(v) => onPatch({ areaofFeature: v })}
                />
              )}
            </div>
          )}
        </fieldset>

        <fieldset className="rip-form__group">
          <legend>Type of feature(s)</legend>
          {/* Fixed columns, dealt in markup.
              CSS multi-column balances its content, so opening a field re-flowed items between
              columns and boxes jumped as they were ticked. Dealing the list here pins every box to
              a column; each column then stacks independently, so an opened field pushes only what
              is under it and nothing else moves. */}
          <div className="chr-checklist__type-columns">
            {typeColumns.map((column, index) => (
              <div className="chr-checklist__type-column" key={`type-column-${index}`}>
                {column}
              </div>
            ))}
          </div>
        </fieldset>
      </section>

      <section className="feature-section">
        <h3 className="feature-section__title">{sectionTitle('Location')}</h3>
        {/* Location */}
        <div className="chr-checklist__type-columns">
          {locationColumns.map((column, index) => (
            <div className="chr-checklist__type-column" key={`location-column-${index}`}>
              {column}
            </div>
          ))}
        </div>
      </section>

      <section className="feature-section">
        <h3 className="feature-section__title">Age</h3>
        {/* Age is one answer, so it is asked as one: radios, not four checkboxes that disable each
            other once one is ticked. Switching age used to mean unticking the old one first. */}
        <RadioButtonGroup
          legendText={requiredLabel('Select age for this feature', true)}
          name="feat-age"
          valueSelected={selectedAge ?? ''}
          disabled={readOnly}
          onChange={(v) => selectAge(v as string)}
        >
          {AGE_OPTIONS.map((o) => (
            <RadioButton key={o.field} labelText={o.label} value={o.field} id={`feat-${o.field}`} />
          ))}
        </RadioButtonGroup>
      </section>

      <section className="feature-section">
        <h3 className="feature-section__title">Planning</h3>
        {/* The three sources of planning direction. Stacked rather than laid across a row: they are
            a checklist to work down, and the permit number belongs under the box that asks for it. */}
        <p className="rip-form__hint">Applies to this feature</p>
        <div className="chr-checklist__planning-sources">
          {sourceChk('managementStrategyFN', 'FN management recommendations provided', 'fn')}
          {sourceChk('managementStrategySP', 'Site plan strategies noted', 'sp')}
          {sourceChk('sitePermitIssued', 'AIA / site-alteration permit issued', 'aia')}
          {showAIA && (
            <div className="chr-checklist__planning-permit">
              <TextField
                id="feat-permit"
                labelText={requiredLabel('Permit number', true)}
                value={str('permit')}
                maxLength={FEATURE_SINGLE_LINE_MAX.permit}
                disabled={readOnly}
                onChange={(v) => onPatch({ permit: v })}
              />
            </div>
          )}
        </div>
        {/* Nothing below until a source is named. The strategy table asks "who recommended this?",
            which has no answer before one of the three boxes is ticked — and a grid of empty
            checkboxes with no column to put them in was the first thing the tab showed. */}
        {recommendationsEnabled && (
          <table className="chr-checklist__planning">
            <thead>
              <tr>
                <th>Strategy</th>
                {showFN && <th>FN</th>}
                {showAIA && <th>AIA/SAP</th>}
                {showSP && <th>Site plan</th>}
              </tr>
            </thead>
            <tbody>
              {PLANNING_STRATEGIES.map((s) => (
                <tr key={s.label}>
                  <td>{s.label}</td>
                  {showFN && <td>{chk(s.fn, '')}</td>}
                  {showAIA && <td>{chk(s.aia, '')}</td>}
                  {showSP && <td>{chk(s.sp, '')}</td>}
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {hasPlanningSubFields && <div className="rip-form__grid">{planningSubFields}</div>}
        {recommendationsEnabled && (
          <fieldset className="rip-form__group">
            <legend>Additional management strategies</legend>
            <div className="bio-strata">
              {/* Empty, the line says what belongs here; filled, it states the rule the rows have
                  to satisfy. A lone "Add" button gave neither. */}
              <p className="rip-form__hint">
                {strategies.length === 0
                  ? 'None added. Add any management strategy applied to this feature that is not listed above.'
                  : 'Select at least one source for each strategy.'}
              </p>
              {strategies.length > 0 && (
                <table className="chr-checklist__planning chr-checklist__additional">
                  <thead>
                    <tr>
                      <th>{requiredLabel('Name', true)}</th>
                      <th>{requiredLabel('Source', true)}</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {strategies.map((s, i) => (
                      <tr key={strategyKeys[i]}>
                        <td>
                          <TextField
                            id={`other-strat-${i}`}
                            labelText="Name"
                            value={s.otherStrategy}
                            maxLength={FEATURE_SINGLE_LINE_MAX.otherStrategy}
                            disabled={readOnly}
                            onChange={(v) => patchStrategy(i, { otherStrategy: v })}
                          />
                        </td>
                        {/* One cell, one checkbox per source in play — the sources are an attribute
                            of the strategy, not three separate questions, and a column each left
                            two of them empty on most features. */}
                        <td>
                          {/* The flex row lives in a child, not the cell: a `display: flex` td stops
                              stretching to the row's height, so centring inside it had only the
                              checkboxes' own 20px to work with and they sat above the input. */}
                          <div className="chr-checklist__additional-sources">
                            {SOURCE_COLUMNS.filter((c) => c.shown).map((c) => (
                              <IndicatorCheckbox
                                key={c.key}
                                id={`other-strat-${c.key}-${i}`}
                                labelText={c.label}
                                value={s[c.ind]}
                                disabled={readOnly}
                                onToggle={(v) => patchStrategy(i, { [c.ind]: v })}
                              />
                            ))}
                          </div>
                        </td>
                        <td className="table-actions">
                          {!readOnly && (
                            <Button
                              kind="danger--ghost"
                              size="sm"
                              renderIcon={TrashCan}
                              onClick={() => removeStrategy(i)}
                            >
                              Delete
                            </Button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              {!readOnly && (
                <div className="chr-checklist__planning-add">
                  <Button
                    kind="tertiary"
                    size="lg"
                    className="bio-strata__add"
                    renderIcon={Add}
                    onClick={addStrategy}
                  >
                    Add management strategy
                  </Button>
                </div>
              )}
            </div>
          </fieldset>
        )}
      </section>

      <section className="feature-section">
        <h3 className="feature-section__title">{sectionTitle('Effectiveness')}</h3>
        {/* Effectiveness — the same fixed columns as the type and location lists, so a strategy that
            asks for a reserve type or a width keeps it in its own cell. */}
        <div className="chr-checklist__type-columns">
          {effectivenessColumns.map((column, index) => (
            <div className="chr-checklist__type-column" key={`effectiveness-column-${index}`}>
              {column}
            </div>
          ))}
        </div>
      </section>

      <section className="feature-section">
        <h3 className="feature-section__title">Damage</h3>
        {/* Damage — one column, in the order the questions are asked: the tick that opens the
            section, the description it calls for, then Q2 and Q3 beneath it. Not two columns: the
            description is a paragraph, and pairing it with Q1 left it sharing a row with a single
            checkbox while Q2 and Q3 ran down beside empty space. */}
        <div className="chr-checklist__field-stack chr-checklist__damage-stack">
          {chk(
            'q1Isthereevidenceofdamagetothesiteorfeature',
            'Q1 — Evidence of damage to the site/feature',
          )}
          {on('q1Isthereevidenceofdamagetothesiteorfeature') && (
            <>
              <TextAreaField
                id="feat-damage-desc"
                labelText="Description of damage"
                value={str('descriptionofdamage')}
                disabled={readOnly}
                limit={FEATURE_TEXT_LIMITS.descriptionofdamage}
                invalid={Boolean(err('descriptionofdamage'))}
                invalidText={err('descriptionofdamage')}
                onChange={(v) => onPatch({ descriptionofdamage: v })}
              />
              <div className="chr-checklist__damage-cause">
                <MultiSelect
                  id="feat-q2-cause"
                  titleText="Q2 — Most likely cause of damage"
                  label="Choose one or more options"
                  items={DAMAGE_CAUSE_ITEMS}
                  itemToString={(item) => item?.label ?? ''}
                  selectedItems={DAMAGE_CAUSE_ITEMS.filter((item) => on(item.id))}
                  disabled={readOnly}
                  onChange={({ selectedItems }) => {
                    const chosen = new Set((selectedItems ?? []).map((item) => item.id));
                    const patch = Object.fromEntries(
                      DAMAGE_CAUSE_ITEMS.map((item) => [
                        item.id,
                        chosen.has(item.id) ? 'true' : 'false',
                      ]),
                    ) as Partial<Record<string, string>>;
                    // Drop the description with the cause it belongs to, the way unticking any
                    // other "Other" box does — otherwise it survives out of sight of the picker.
                    if (!chosen.has(OTHER_DAMAGE_AGENT)) {
                      patch[OTHER_DAMAGE_AGENT_DESCRIPTION] = '';
                    }
                    onPatch(patch);
                  }}
                />
                {on(OTHER_DAMAGE_AGENT) && (
                  <TextField
                    id="feat-damage-other"
                    labelText="Other cause description"
                    value={str(OTHER_DAMAGE_AGENT_DESCRIPTION)}
                    maxLength={FEATURE_SINGLE_LINE_MAX[OTHER_DAMAGE_AGENT_DESCRIPTION]}
                    disabled={readOnly}
                    onChange={(v) => onPatch({ [OTHER_DAMAGE_AGENT_DESCRIPTION]: v })}
                  />
                )}
              </div>
              <RadioButtonGroup
                legendText="Q3 — Irreversibly damaged or unsuitable for continued use?"
                name="feat-q3"
                valueSelected={
                  str('q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse') ??
                  ''
                }
                disabled={readOnly}
                onChange={(v) =>
                  onPatch({
                    q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse:
                      String(v),
                  })
                }
              >
                {q3Options.map((option) => (
                  <RadioButton
                    key={option.code}
                    labelText={option.description}
                    value={option.code}
                    id={`feat-q3-${option.code}`}
                  />
                ))}
              </RadioButtonGroup>
            </>
          )}
        </div>
      </section>

      <section className="feature-section">
        <h3 className="feature-section__title">{sectionTitle('Windthrow')}</h3>
        {/* Both windthrow and trail features are one question that opens a short form. Everything
            the tick reveals is indented under it, so the follow-ups read as belonging to the answer
            rather than as further questions of their own. */}
        {chk('windthrowManagement', 'Windthrow management applicable')}
        {on('windthrowManagement') && (
          <div className="chr-checklist__reveal">
            {chk('windthrow', 'Area windfirm')}
            {!on('windthrow') && (
              <TextField
                id="feat-est-windthrow"
                labelText="Estimated windthrow (%)"
                value={str('estwindthrow')}
                disabled={readOnly}
                invalid={Boolean(err('estwindthrow'))}
                invalidText={err('estwindthrow')}
                onChange={(v) => onPatch({ estwindthrow: v })}
              />
            )}
            <fieldset className="rip-form__group">
              <legend>Treatment</legend>
              {WINDTHROW_TECHNIQUES.map((w) => chk(w.field, w.label))}
              {chk('otherTechnique', 'Other technique')}
              {on('otherTechnique') && (
                <TextField
                  id="feat-windthrow-other"
                  labelText={requiredLabel('Description', true)}
                  value={str('ifotherpleasedescribe')}
                  maxLength={FEATURE_SINGLE_LINE_MAX.ifotherpleasedescribe}
                  disabled={readOnly}
                  onChange={(v) => onPatch({ ifotherpleasedescribe: v })}
                />
              )}
            </fieldset>
          </div>
        )}
      </section>

      <section className="feature-section">
        <h3 className="feature-section__title">{sectionTitle('Trail features')}</h3>
        {chk('trailfeatures', 'Trail features applicable')}
        {on('trailfeatures') && (
          <div className="chr-checklist__reveal">
            {chk('canthetrailstillbelocated', 'Trail still locatable')}
            {chk('hasthetrailbeenmadelesspassble', 'Trail made less passable')}
            {chk('isthereevidenceofdamage', 'Evidence of damage to trail area')}
            {on('isthereevidenceofdamage') && (
              <TextField
                id="feat-trail-len"
                labelText={requiredLabel('Estimated trail damage (%)', true)}
                value={str('trailLength')}
                disabled={readOnly}
                invalid={Boolean(err('trailLength'))}
                invalidText={err('trailLength')}
                onChange={(v) => onPatch({ trailLength: v })}
              />
            )}
          </div>
        )}
      </section>

      <section className="feature-section">
        <h3 className="feature-section__title">{sectionTitle('Summary')}</h3>
        {/* Summary — one column. Each question keeps its description directly beneath it, so the
            answer reads as belonging to the box that asked for it rather than as the next field. */}
        <div className="chr-checklist__summary">
          <div className="chr-checklist__summary-question">
            {chk(
              'q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature',
              'Q4 — Operational factors limited CHR management options?',
            )}
            {on('q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature') && (
              <TextAreaField
                id="feat-q4-desc"
                labelText={requiredLabel('Q4 description', true)}
                value={str('q4Description')}
                disabled={readOnly}
                limit={FEATURE_TEXT_LIMITS.q4Description}
                invalid={Boolean(err('q4Description'))}
                invalidText={err('q4Description')}
                onChange={(v) => onPatch({ q4Description: v })}
              />
            )}
          </div>
          <div className="chr-checklist__summary-question">
            {chk(
              'q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective',
              'Q5 — Strategies/practices particularly effective?',
            )}
            {on(
              'q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective',
            ) && (
              <TextAreaField
                id="feat-q5-desc"
                labelText={requiredLabel('Q5 description', true)}
                value={str('q5Description')}
                disabled={readOnly}
                limit={FEATURE_TEXT_LIMITS.q5Description}
                invalid={Boolean(err('q5Description'))}
                invalidText={err('q5Description')}
                onChange={(v) => onPatch({ q5Description: v })}
              />
            )}
          </div>
          <div className="chr-checklist__summary-question">
            {chk(
              'q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature',
              'Q6 — Strategies that could have reduced impact?',
            )}
            {on(
              'q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature',
            ) && (
              <TextAreaField
                id="feat-q6-desc"
                labelText={requiredLabel('Q6 description', true)}
                value={str('q6Description')}
                disabled={readOnly}
                limit={FEATURE_TEXT_LIMITS.q6Description}
                invalid={Boolean(err('q6Description'))}
                invalidText={err('q6Description')}
                onChange={(v) => onPatch({ q6Description: v })}
              />
            )}
          </div>
          <div className="chr-checklist__summary-question">
            <div className="chr-checklist__rating">
              <CodeSelect
                id="feat-rating"
                labelText={requiredLabel('Feature rating', true)}
                value={str('featureRating')}
                options={ratingCodes}
                includeBlank
                disabled={readOnly}
                invalid={Boolean(err('featureRating'))}
                invalidText={err('featureRating')}
                onChange={(v) => onPatch({ featureRating: v })}
              />
            </div>
            <TextAreaField
              id="feat-rating-rationale"
              labelText="Feature rating rationale"
              value={str('featureRatingRationale')}
              disabled={readOnly}
              limit={FEATURE_TEXT_LIMITS.featureRatingRationale}
              invalid={Boolean(err('featureRatingRationale'))}
              invalidText={err('featureRatingRationale')}
              onChange={(v) => onPatch({ featureRatingRationale: v })}
            />
          </div>
        </div>
      </section>

      <section className="feature-section">
        <h3 className="feature-section__title">Comments</h3>
        {/* Comments */}
        <div className="rip-form__grid">
          <TextAreaField
            id="feat-comment"
            labelText="Comments"
            value={str('featureComment')}
            rows={10}
            disabled={readOnly}
            limit={FEATURE_TEXT_LIMITS.featureComment}
            invalid={Boolean(err('featureComment'))}
            invalidText={err('featureComment')}
            onChange={(v) => onPatch({ featureComment: v })}
          />
        </div>
      </section>
    </div>
  );
};

export default FeatureEditor;

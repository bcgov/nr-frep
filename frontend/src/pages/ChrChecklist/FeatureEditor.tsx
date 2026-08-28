import { Add, TrashCan, WarningFilled } from '@carbon/icons-react';
import {
  Accordion,
  AccordionItem,
  Button,
  RadioButton,
  RadioButtonGroup,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useEffect, useMemo, useState, type FC, type ReactNode } from 'react';

import {
  CodeSelect,
  IndicatorCheckbox,
  TextAreaField,
  TextField,
} from '@/pages/ChrChecklist/fields';
import { requiredLabel } from '@/utils/requiredLabel';

import type { CodeOption } from '@/pages/ChrChecklist/codeLists';
import type { Feature, Indicator, OtherPlannedManagementStrategy } from '@/types/chrChecklist';

import {
  FEATURE_CLASS_CODES,
  INFORMATION_SOURCE_CODES,
  RATING_CODES,
  RESERVE_TYPE_CODES,
} from '@/pages/ChrChecklist/codeLists';
import { featureErrors } from '@/pages/ChrChecklist/featureValidation';
import { FEATURE_SINGLE_LINE_MAX, FEATURE_TEXT_LIMITS } from '@/pages/ChrChecklist/textLimits';

type PatchFn = (patch: Partial<Feature>) => void;

// Which accordion section each lightweight-validation field lives in, so a section header can show
// an error badge and stay expanded while any of its fields is invalid (otherwise a blocked Save
// leaves the error hidden inside a collapsed section). Sections not listed never carry these errors.
const SECTION_ERROR_FIELDS: Record<string, string[]> = {
  Description: ['borden', 'ofCMTsNumber', 'standofMonumentalCedar', 'otherdescription'],
  Location: ['locationOtherDescription', 'locationReservetype'],
  Effectiveness: ['bufferWidthMeter'],
  Summary: ['q4Description', 'q5Description', 'q6Description', 'featureRating'],
};

// Legacy Q3 radio (ManagementEffectiveness.vue): No / Don't Know / Yes → N / D / Y.
const Q3_OPTIONS: CodeOption[] = [
  { code: 'N', label: 'No' },
  { code: 'D', label: "Don't know" },
  { code: 'Y', label: 'Yes' },
];

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
}> = ({ feature, onPatch, readOnly, showErrors = false }) => {
  const ind = (field: string): Indicator | undefined => feature[field] as Indicator | undefined;
  const str = (field: string): string | undefined => feature[field] as string | undefined;
  const on = (field: string): boolean => ind(field) === 'true';

  // An explicit "not a composite". The submit rule compares the indicator to the string "false", so
  // a feature whose composite box has never been touched owes neither code — and must not be
  // marked as if it did.
  const notComposite = feature.compositeFeatureInd === 'false';
  const chk = (field: string, label: string) => (
    <IndicatorCheckbox
      id={`feat-${field}`}
      labelText={label}
      value={ind(field)}
      disabled={readOnly}
      onToggle={(v) => onPatch({ [field]: v })}
    />
  );

  // Age is single-select: a feature has one age. Once a box is checked, the other three are disabled
  // — to switch, uncheck the active one first. Submit still requires at least one to be selected.
  const selectedAge = AGE_FIELDS.find((f) => on(f));
  const ageChk = (field: string, label: string) => (
    <IndicatorCheckbox
      id={`feat-${field}`}
      labelText={label}
      value={ind(field)}
      disabled={readOnly || (selectedAge !== undefined && selectedAge !== field)}
      onToggle={(v) => onPatch({ [field]: v })}
    />
  );

  // Size-of-area unit toggle (legacy `metersOrBuffer`): UI-only, derived from the data (an area
  // value ⇒ hectares mode). Switching units clears the other set of fields (legacy `reinitUntis`).
  const [areaUnit, setAreaUnit] = useState<'metres' | 'hectares'>(() =>
    str('areaofFeature') ? 'hectares' : 'metres',
  );
  const switchUnit = (unit: 'metres' | 'hectares') => {
    setAreaUnit(unit);
    onPatch(
      unit === 'hectares' ? { widthofFeature: '', lengthofFeature: '' } : { areaofFeature: '' },
    );
  };

  // Live inline validation (lightweight high-value subset; the full rule set runs server-side at
  // submit). Empty when read-only. Save is blocked in FeatureList while any error remains. Memoised
  // on the feature so the auto-open effect below only re-runs when the data actually changes.
  const fieldErrors: Record<string, string> = useMemo(
    () => (readOnly || !showErrors ? {} : featureErrors(feature)),
    [readOnly, showErrors, feature],
  );
  const err = (key: string): string | undefined => fieldErrors[key];

  // Accordion sections track their own open state; a section also shows an error-count badge.
  const [openSections, setOpenSections] = useState<Record<string, boolean>>({ Description: true });
  const sectionErrorCount = (section: string): number =>
    (SECTION_ERROR_FIELDS[section] ?? []).filter((key) => Boolean(fieldErrors[key])).length;
  // A section that holds an error is auto-opened so a blocked Save never hides the reason — but the
  // open state is made STICKY (persisted into openSections) rather than derived live from the error
  // count. Otherwise filling the field clears the error and the section would collapse mid-edit
  // (the reported bug: adding a Feature rating / description snapped the Summary section shut).
  useEffect(() => {
    setOpenSections((prev) => {
      let next = prev;
      for (const section of Object.keys(SECTION_ERROR_FIELDS)) {
        const hasError = SECTION_ERROR_FIELDS[section].some((key) => Boolean(fieldErrors[key]));
        if (hasError && !prev[section]) {
          if (next === prev) next = { ...prev };
          next[section] = true;
        }
      }
      return next;
    });
  }, [fieldErrors]);
  const isOpen = (section: string): boolean => Boolean(openSections[section]);
  const toggleSection = (section: string) =>
    setOpenSections((prev) => ({ ...prev, [section]: !prev[section] }));
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
  const showFN = on('managementStrategyFN');
  const showSP = on('managementStrategySP');
  const recommendationsEnabled = showFN || showSP || on('sitePermitIssued');

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
    const columnVisible = { fn: showFN, aia: true, sp: showSP }[variant];
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
        options={RESERVE_TYPE_CODES}
        disabled={readOnly}
        onChange={(v) => onPatch({ [def.field]: v })}
      />
    );
  };

  const variantLabel = { fn: 'FN', aia: 'AIA', sp: 'SP' } as const;
  const planningSubFields = (['fn', 'aia', 'sp'] as const).flatMap((v) => [
    subField(v, BUFFER_LENGTH[v], `Buffer length — ${variantLabel[v]} (m)`, 'buffer'),
    subField(v, ROTATIONAL_RESERVE[v], `Rotational reserve type — ${variantLabel[v]}`, 'reserve'),
    subField(v, TEMPORARY_RESERVE[v], `Reserve type — ${variantLabel[v]}`, 'reserve'),
  ]);
  const hasPlanningSubFields = planningSubFields.some(Boolean);

  return (
    <Accordion>
      <AccordionItem
        title={sectionTitle('Description')}
        open={isOpen('Description')}
        onHeadingClick={() => toggleSection('Description')}
      >
        {/* Description */}
        <fieldset className="rip-form__group">
          <legend>Feature description</legend>
          <div className="rip-form__grid rip-form__grid--wide">
            <TextField
              id="feat-label"
              labelText={requiredLabel('Feature label', true)}
              value={str('featureLabel')}
              maxLength={FEATURE_SINGLE_LINE_MAX.featureLabel}
              disabled={readOnly}
              onChange={(v) => onPatch({ featureLabel: v })}
            />
            <CodeSelect
              id="feat-class"
              // Owed only by an explicit non-composite, exactly as the submit rule asks for it: a
              // composite is described through its members, and a feature whose composite box has
              // never been touched is not asked either.
              labelText={requiredLabel('Feature class', notComposite)}
              value={str('featureDescriptionCode')}
              options={FEATURE_CLASS_CODES}
              includeBlank
              disabled={readOnly}
              onChange={(v) => onPatch({ featureDescriptionCode: v })}
            />
            <CodeSelect
              id="feat-source"
              labelText={requiredLabel('Information source', notComposite)}
              value={str('featureInfoSourceCode')}
              options={INFORMATION_SOURCE_CODES}
              includeBlank
              disabled={readOnly}
              onChange={(v) => onPatch({ featureInfoSourceCode: v })}
            />
            {/* Its own full-width row directly under label/class/source. The grid auto-flows, so
                left at the end of the list this landed in whatever cell was free — wedged beside the
                composite hint and the registered-site checkbox. */}
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
            {chk('chrRegisteredSite', 'Registered archaeological site')}
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
                onChange={(v) => onPatch({ borden: v })}
              />
            )}
          </div>
        </fieldset>

        <fieldset className="rip-form__group">
          <legend>Size of area influenced</legend>
          <RadioButtonGroup
            legendText=""
            name="feat-area-unit"
            valueSelected={areaUnit}
            disabled={readOnly}
            onChange={(v) => switchUnit(v as 'metres' | 'hectares')}
          >
            <RadioButton labelText="Metres" value="metres" id="feat-area-metres" />
            <RadioButton labelText="Hectares" value="hectares" id="feat-area-hectares" />
          </RadioButtonGroup>
          <div className="rip-form__grid">
            {areaUnit === 'metres' ? (
              <>
                <TextField
                  id="feat-width"
                  labelText="Width (m)"
                  value={str('widthofFeature')}
                  disabled={readOnly}
                  onChange={(v) => onPatch({ widthofFeature: v })}
                />
                <TextField
                  id="feat-length"
                  labelText="Length (m)"
                  value={str('lengthofFeature')}
                  disabled={readOnly}
                  onChange={(v) => onPatch({ lengthofFeature: v })}
                />
              </>
            ) : (
              <TextField
                id="feat-area"
                labelText="Area (ha)"
                value={str('areaofFeature')}
                disabled={readOnly}
                onChange={(v) => onPatch({ areaofFeature: v })}
              />
            )}
          </div>
        </fieldset>

        <fieldset className="rip-form__group">
          <legend>Type of feature(s)</legend>
          <div className="rip-form__grid chr-checklist__check-grid">
            {FEATURE_TYPES.map((t) => chk(t.field, t.label))}
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
            {chk('other', 'Other')}
            {on('other') && (
              <TextField
                id="feat-other-desc"
                labelText={requiredLabel('Other description', true)}
                value={str('otherdescription')}
                disabled={readOnly}
                maxLength={FEATURE_SINGLE_LINE_MAX.otherdescription}
                invalid={Boolean(err('otherdescription'))}
                invalidText={err('otherdescription')}
                onChange={(v) => onPatch({ otherdescription: v })}
              />
            )}
          </div>
        </fieldset>
      </AccordionItem>

      <AccordionItem
        title={sectionTitle('Location')}
        open={isOpen('Location')}
        onHeadingClick={() => toggleSection('Location')}
      >
        {/* Location */}
        <div className="rip-form__grid rip-form__grid--wide chr-checklist__check-grid">
          {chk('inharvestedarea', 'In harvested area')}
          {chk('adjacenttoblock', 'Adjacent to block')}
          {chk('adjacenttowater', 'Adjacent to water')}
          {chk('entirecutblock', 'Entire cut block')}
          {chk('locationOther', 'Other location')}
          {on('locationOther') && (
            <TextField
              id="feat-loc-other"
              labelText={requiredLabel('Other location description', true)}
              value={str('locationOtherDescription')}
              disabled={readOnly}
              maxLength={FEATURE_SINGLE_LINE_MAX.locationOtherDescription}
              invalid={Boolean(err('locationOtherDescription'))}
              invalidText={err('locationOtherDescription')}
              onChange={(v) => onPatch({ locationOtherDescription: v })}
            />
          )}
          {chk('inReserve', 'In reserve')}
          {on('inReserve') && (
            <CodeSelect
              id="feat-reserve"
              labelText={requiredLabel('Reserve type', true)}
              value={str('locationReservetype')}
              options={RESERVE_TYPE_CODES}
              disabled={readOnly}
              invalid={Boolean(err('locationReservetype'))}
              invalidText={err('locationReservetype')}
              onChange={(v) => onPatch({ locationReservetype: v })}
            />
          )}
        </div>
      </AccordionItem>

      <AccordionItem title="Age">
        {/* Age */}
        <div className="rip-form__grid chr-checklist__check-grid">
          {ageChk('pre1846', 'Pre-1846')}
          {ageChk('post1846', 'Post-1846')}
          {ageChk('ageUnknown', 'Age unknown')}
          {ageChk('historicalUse', 'Historical use')}
        </div>
      </AccordionItem>

      <AccordionItem title="Planning">
        {/* Planning */}
        <div className="rip-form__grid chr-checklist__planning-header">
          {chk('managementStrategyFN', 'FN management recommendations provided')}
          {chk('managementStrategySP', 'Site plan strategies noted')}
          {chk('sitePermitIssued', 'AIA / site-alteration permit issued')}
          {on('sitePermitIssued') && (
            <TextField
              id="feat-permit"
              labelText={requiredLabel('Permit number', true)}
              value={str('permit')}
              maxLength={FEATURE_SINGLE_LINE_MAX.permit}
              disabled={readOnly}
              onChange={(v) => onPatch({ permit: v })}
            />
          )}
        </div>
        <table className="chr-checklist__planning">
          <thead>
            <tr>
              <th>Strategy</th>
              {showFN && <th>FN</th>}
              <th>AIA/SAP</th>
              {showSP && <th>Site plan</th>}
            </tr>
          </thead>
          <tbody>
            {PLANNING_STRATEGIES.map((s) => (
              <tr key={s.label}>
                <td>{s.label}</td>
                {showFN && <td>{chk(s.fn, '')}</td>}
                <td>{chk(s.aia, '')}</td>
                {showSP && <td>{chk(s.sp, '')}</td>}
              </tr>
            ))}
          </tbody>
        </table>
        {hasPlanningSubFields && <div className="rip-form__grid">{planningSubFields}</div>}
        {recommendationsEnabled && (
          <fieldset className="rip-form__group">
            <legend>Other management strategies</legend>
            <div className="bio-strata">
              {!readOnly && (
                <div className="bio-strata__toolbar">
                  <Button
                    kind="tertiary"
                    size="lg"
                    className="bio-strata__add"
                    renderIcon={Add}
                    onClick={addStrategy}
                  >
                    Add strategy
                  </Button>
                </div>
              )}
              {strategies.length > 0 && (
                <Table size="sm" className="bio-strata__table">
                  <TableHead>
                    <TableRow>
                      <TableHeader>Strategy</TableHeader>
                      {showFN && <TableHeader>FN</TableHeader>}
                      <TableHeader>AIA/SAP</TableHeader>
                      {showSP && <TableHeader>Site plan</TableHeader>}
                      <TableHeader>Action</TableHeader>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {strategies.map((s, i) => (
                      <TableRow key={strategyKeys[i]}>
                        <TableCell>
                          <TextField
                            id={`other-strat-${i}`}
                            labelText="Other strategy"
                            value={s.otherStrategy}
                            maxLength={FEATURE_SINGLE_LINE_MAX.otherStrategy}
                            disabled={readOnly}
                            onChange={(v) => patchStrategy(i, { otherStrategy: v })}
                          />
                        </TableCell>
                        {showFN && (
                          <TableCell>
                            <IndicatorCheckbox
                              id={`other-strat-fn-${i}`}
                              labelText="FN"
                              value={s.fnInd}
                              disabled={readOnly}
                              onToggle={(v) => patchStrategy(i, { fnInd: v })}
                            />
                          </TableCell>
                        )}
                        <TableCell>
                          <IndicatorCheckbox
                            id={`other-strat-aia-${i}`}
                            labelText="AIA/SAP"
                            value={s.aiaInd}
                            disabled={readOnly}
                            onToggle={(v) => patchStrategy(i, { aiaInd: v })}
                          />
                        </TableCell>
                        {showSP && (
                          <TableCell>
                            <IndicatorCheckbox
                              id={`other-strat-sp-${i}`}
                              labelText="Site plan"
                              value={s.spInd}
                              disabled={readOnly}
                              onToggle={(v) => patchStrategy(i, { spInd: v })}
                            />
                          </TableCell>
                        )}
                        <TableCell className="table-actions">
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
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </div>
          </fieldset>
        )}
      </AccordionItem>

      <AccordionItem
        title={sectionTitle('Effectiveness')}
        open={isOpen('Effectiveness')}
        onHeadingClick={() => toggleSection('Effectiveness')}
      >
        {/* Effectiveness */}
        <div className="rip-form__grid rip-form__grid--wide chr-checklist__check-grid">
          {isComposite &&
            chk('forCompositeFeaturesInd', 'Same strategy for all features (composite)')}
          {!isComposite && chk('unabletoLocate', 'Unable to locate feature')}
          {chk('noManagement', 'No management applied')}
          {chk('partiallytemporaryreserve', 'Partially conserved in temporary reserve')}
          {on('partiallytemporaryreserve') && (
            <CodeSelect
              id="feat-eff-parttemp"
              labelText="Partial temporary reserve type"
              value={str('partiallytemporaryreservetype')}
              options={RESERVE_TYPE_CODES}
              disabled={readOnly}
              onChange={(v) => onPatch({ partiallytemporaryreservetype: v })}
            />
          )}
          {chk('fullyconservedintemporaryreserve', 'Fully conserved in temporary reserve')}
          {on('fullyconservedintemporaryreserve') && (
            <CodeSelect
              id="feat-eff-fulltemp"
              labelText="Full temporary reserve type"
              value={str('fullytemporaryreserve')}
              options={RESERVE_TYPE_CODES}
              disabled={readOnly}
              onChange={(v) => onPatch({ fullytemporaryreserve: v })}
            />
          )}
          {chk('partiallyconservedinpermanentreserve', 'Partially conserved in permanent reserve')}
          {on('partiallyconservedinpermanentreserve') && (
            <CodeSelect
              id="feat-eff-partperm"
              labelText="Partial permanent reserve type"
              value={str('partiallyconservedinpermanentreserveType')}
              options={RESERVE_TYPE_CODES}
              disabled={readOnly}
              onChange={(v) => onPatch({ partiallyconservedinpermanentreserveType: v })}
            />
          )}
          {chk('fullyconservedinpermanentreserve', 'Fully conserved in permanent reserve')}
          {on('fullyconservedinpermanentreserve') && (
            <CodeSelect
              id="feat-eff-fullperm"
              labelText="Full permanent reserve type"
              value={str('fullyconservedinpermanentreserveType')}
              options={RESERVE_TYPE_CODES}
              disabled={readOnly}
              onChange={(v) => onPatch({ fullyconservedinpermanentreserveType: v })}
            />
          )}
          {chk('modifiedblockboundary', 'Modified block boundary')}
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
          {chk('compledCrownorstandmodification', 'Completed crown/stand modification')}
          {chk('datedthefeature', 'Dated the feature')}
          {chk('retainedinharvestareanobuffer', 'Retained in harvest area (no buffer)')}
          {chk('leftStanding', 'Left standing')}
          {chk('stubbed', 'Stubbed (CMT)')}
          {chk('stubbedNon', 'Stubbed (non-CMT)')}
          {chk('avoidSilvAvoidPlanting', 'Avoided planting')}
          {chk('avoidSilvAvoidSitePrep', 'Avoided site prep')}
          {chk('machineFreeZone', 'Machine-free zone')}
          {chk('harvestUnderSap', 'Harvest under SAP')}
          {chk('winterHarvestFrozenGround', 'Winter harvest / frozen ground')}
          <TextField
            id="feat-eff-other"
            labelText="Other activities"
            value={str('otherActivities')}
            maxLength={FEATURE_SINGLE_LINE_MAX.otherActivities}
            disabled={readOnly}
            onChange={(v) => onPatch({ otherActivities: v })}
          />
        </div>
      </AccordionItem>

      <AccordionItem title="Damage">
        {/* Damage — two columns: the stacked Q1/Q2/Q3 questions on the left, the description
            text area aligned next to Q1 on the right. */}
        <div className="chr-checklist__two-col">
          <div className="chr-checklist__field-stack">
            {chk(
              'q1Isthereevidenceofdamagetothesiteorfeature',
              'Q1 — Evidence of damage to the site/feature',
            )}
            {on('q1Isthereevidenceofdamagetothesiteorfeature') && (
              <>
                <fieldset className="rip-form__group">
                  <legend>Q2 — Most likely cause of damage</legend>
                  {DAMAGE_AGENTS.map((d) => chk(d.field, d.label))}
                  {chk('otherQ2Wheredamagehasoccurredwhatisthemostlikelycause', 'Other')}
                  {on('otherQ2Wheredamagehasoccurredwhatisthemostlikelycause') && (
                    <TextField
                      id="feat-damage-other"
                      labelText="Other cause description"
                      value={str(
                        'ifotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause',
                      )}
                      maxLength={
                        FEATURE_SINGLE_LINE_MAX.ifotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause
                      }
                      disabled={readOnly}
                      onChange={(v) =>
                        onPatch({
                          ifotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause:
                            v,
                        })
                      }
                    />
                  )}
                </fieldset>
                <CodeSelect
                  id="feat-q3"
                  labelText="Q3 — Irreversibly damaged or unsuitable for continued use?"
                  value={str(
                    'q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse',
                  )}
                  options={Q3_OPTIONS}
                  includeBlank
                  disabled={readOnly}
                  onChange={(v) =>
                    onPatch({
                      q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse: v,
                    })
                  }
                />
              </>
            )}
          </div>
          {on('q1Isthereevidenceofdamagetothesiteorfeature') && (
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
          )}
        </div>
      </AccordionItem>

      <AccordionItem title="Windthrow">
        {/* Windthrow */}
        <div className="chr-checklist__two-col">
          <div className="chr-checklist__field-stack">
            {chk('windthrowManagement', 'Windthrow management applicable')}
            {on('windthrowManagement') && (
              <>
                {chk('windthrow', 'Area windfirm')}
                {!on('windthrow') && (
                  <TextField
                    id="feat-est-windthrow"
                    labelText="Estimated windthrow (%)"
                    value={str('estwindthrow')}
                    disabled={readOnly}
                    onChange={(v) => onPatch({ estwindthrow: v })}
                  />
                )}
                <fieldset className="rip-form__group">
                  <legend>Windthrow treatment</legend>
                  {WINDTHROW_TECHNIQUES.map((w) => chk(w.field, w.label))}
                  {chk('otherTechnique', 'Other technique')}
                  {on('otherTechnique') && (
                    <TextField
                      id="feat-windthrow-other"
                      labelText="Other technique description"
                      value={str('ifotherpleasedescribe')}
                      maxLength={FEATURE_SINGLE_LINE_MAX.ifotherpleasedescribe}
                      disabled={readOnly}
                      onChange={(v) => onPatch({ ifotherpleasedescribe: v })}
                    />
                  )}
                </fieldset>
              </>
            )}
          </div>
          <fieldset className="rip-form__group">
            {chk('trailfeatures', 'Trail features applicable')}
            {on('trailfeatures') && (
              <>
                {chk('canthetrailstillbelocated', 'Trail still locatable')}
                {chk('hasthetrailbeenmadelesspassble', 'Trail made less passable')}
                {chk('isthereevidenceofdamage', 'Evidence of damage to trail area')}
                {on('isthereevidenceofdamage') && (
                  <TextField
                    id="feat-trail-len"
                    labelText={requiredLabel('Estimated trail damage (%)', true)}
                    value={str('trailLength')}
                    disabled={readOnly}
                    onChange={(v) => onPatch({ trailLength: v })}
                  />
                )}
              </>
            )}
          </fieldset>
        </div>
      </AccordionItem>

      <AccordionItem
        title={sectionTitle('Summary')}
        open={isOpen('Summary')}
        onHeadingClick={() => toggleSection('Summary')}
      >
        {/* Summary */}
        <div className="chr-checklist__two-col">
          {chk(
            'q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature',
            'Q4 — Operational factors limited CHR management options?',
          )}
          <div>
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
          {chk(
            'q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective',
            'Q5 — Strategies/practices particularly effective?',
          )}
          <div>
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
          {chk(
            'q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature',
            'Q6 — Strategies that could have reduced impact?',
          )}
          <div>
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
          <CodeSelect
            id="feat-rating"
            labelText={requiredLabel('Feature rating', true)}
            value={str('featureRating')}
            options={RATING_CODES}
            includeBlank
            disabled={readOnly}
            invalid={Boolean(err('featureRating'))}
            invalidText={err('featureRating')}
            onChange={(v) => onPatch({ featureRating: v })}
          />
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
      </AccordionItem>

      <AccordionItem title="Comments">
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
      </AccordionItem>
    </Accordion>
  );
};

export default FeatureEditor;

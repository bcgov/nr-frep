import { Add, Edit, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  DatePicker,
  DatePickerInput,
  Modal,
  Select,
  SelectItem,
  SkeletonText,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC, type ReactNode } from 'react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import { requiredLabel } from '@/utils/requiredLabel';

import OutstandingPanel from './OutstandingPanel';
import RequiredLegend from './RequiredLegend';
import { BEC_SEARCH_MAX, STRATUM_FIELD_MAX, STRATUM_TEXT_LIMITS } from './stratumLimits';

import type { OutstandingGroup } from './tabStatus';
import type { BecRow, CodeOption } from '@/types/configuration';
import type { BioStratum, BioStratumRow, StratumComputed } from '@/types/protocolChecklist';
import type { ValidationMode } from '@/utils/validation';

import { useConfirm } from '@/context/confirm/useConfirm';
import { useNotification } from '@/context/notification/useNotification';
import { useSettledFields } from '@/hooks/useSettledFields';
import API from '@/services/APIs';
import { apiErrorMessage } from '@/utils/apiError';
import { NO_AUTOFILL } from '@/utils/autofill';
import { formatShortDate } from '@/utils/date';
import { byteLength, overLimitError } from '@/utils/textLimits';
import { errorsForSettledFields, isNumberInProgress } from '@/utils/validation';

/**
 * Biodiversity Stratum Summary section (FREP211) — edited inline in place. Master-detail editor with
 * coded dropdowns, a BEC catalogue picker, the legacy conditional show/hide cascade
 * (stratum-type → harvest-area forcing; the constraints/eco "present?" toggles and harvest=HNR
 * disable+clear their groups), read-only NAR + plots-completed, and client-side validation mirroring
 * Frep211ValidationManager so a bad save fails fast with field-level messages. Plots for the stratum
 * live on the separate Plots tab, as in legacy.
 */

type Props = {
  checklistId: string;
  canEdit: boolean;
  submitted: boolean;
  /** Outstanding submit rules for this tab, grouped by stratum (see OutstandingPanel). */
  outstanding?: OutstandingGroup[];
  /** Called after a save or delete lands, so the tab-completion dots re-derive. */
  onSaved?: () => void;
  /** `error` once a submit has been refused — see OutstandingPanel. */
  tone?: 'neutral' | 'error';
};

type FieldDef = { key: string; label: string };

const TEXT_GROUPS: { title: string; fields: FieldDef[] }[] = [
  {
    title: 'Summary',
    fields: [
      { key: 'stratumNumber', label: 'Stratum number' },
      { key: 'strataTypeCode', label: 'Stratum type' },
      { key: 'summaryDate', label: 'Summary date' },
      { key: 'assessorName', label: 'Assessor name' },
      { key: 'plotCount', label: 'Plot count' },
      { key: 'size', label: 'Stratum size (ha)' },
      { key: 'consistentMapInd', label: 'Consistent with map' },
      { key: 'estimatedSize', label: 'Estimated size (ha)' },
    ],
  },
  {
    title: 'Patch',
    fields: [
      { key: 'patchLocationCode', label: 'Patch location' },
      { key: 'patchEstimatedOldestTreeAge', label: 'Estimated oldest tree age' },
      { key: 'patchGeneralComment', label: 'Patch general comment' },
      { key: 'patchWindthrowPct', label: '% of trees in reserve windthrown' },
    ],
  },
  {
    title: 'Constraints',
    fields: [
      { key: 'wetlandPct', label: 'Wetland %' },
      { key: 'riparianManagementZonePct', label: 'Riparian mgmt zone %' },
      { key: 'riparianReserveZonePct', label: 'Riparian reserve zone %' },
      { key: 'rockOutcropPct', label: 'Rock outcrop %' },
      { key: 'nonCommercialBrushPct', label: 'Non-commercial brush %' },
      { key: 'nonMerchTimberPct', label: 'Non-merch timber %' },
      { key: 'sensitiveSoilPct', label: 'Sensitive soil %' },
      { key: 'ungHoofAnimalWinteringPct', label: 'Ungulate wintering %' },
      { key: 'wildlifeHabitatAreaPct', label: 'Wildlife habitat area %' },
      { key: 'oldGrowthManagementAreaPct', label: 'OGMA %' },
      { key: 'visualsPct', label: 'Visuals %' },
      { key: 'culturalHeritageFeaturePct', label: 'Cultural heritage feature %' },
      { key: 'recreationFeaturePct', label: 'Recreation feature %' },
      { key: 'otherConstraint', label: 'Other constraint' },
      { key: 'otherConstraintPct', label: 'Other constraint %' },
      { key: 'constrainedTotal', label: 'Total constrained %' },
    ],
  },
  {
    title: 'Eco anchors',
    fields: [
      { key: 'bearDenCnt', label: 'Bear den count' },
      { key: 'hibernaculumCnt', label: 'Hibernaculum count' },
      { key: 'vetTreeCnt', label: 'Veteran tree count' },
      { key: 'mineralLickCnt', label: 'Mineral lick count' },
      { key: 'largeStickNestCnt', label: 'Large stick nest count' },
      { key: 'cavityNestCnt', label: 'Cavity nest count' },
      { key: 'largeHallowTreeCnt', label: 'Large hollow tree count' },
      { key: 'largeWitchesBroomCnt', label: "Large witches' broom count" },
      { key: 'otherEcoAnchorCnt', label: 'Other eco anchor count' },
      { key: 'otherEcoAnchorDesc', label: 'Other eco anchor description' },
    ],
  },
  {
    title: 'BEC',
    fields: [
      { key: 'bgcZoneCode', label: 'BGC zone' },
      { key: 'bgcSubzoneCode', label: 'BGC subzone' },
      { key: 'bgcVariant', label: 'BGC variant' },
      { key: 'bgcPhase', label: 'BGC phase' },
      { key: 'becSiteSeriesCd', label: 'Site series' },
      { key: 'siteSeriesPhaseCd', label: 'Site series phase' },
      { key: 'seral', label: 'Seral' },
    ],
  },
  {
    title: 'Windthrow',
    fields: [
      { key: 'windthrowDistributionCode', label: 'Distribution of windthrow' },
      { key: 'otherWindthrowTreatment', label: 'Other windthrow treatment' },
    ],
  },
];

// Fixed legacy enums (hard-coded radios in frep211StratumSummary.jsp). Stratum type is the only
// DB-backed list — fetched from /configuration/strata-types at runtime.
const STATIC_OPTIONS: Record<string, CodeOption[]> = {
  harvestAreaCode: [
    { code: 'HNR', description: 'Harvest area with no retention' },
    { code: 'HDR', description: 'Harvest area with dispersed retention' },
    { code: 'PCH', description: 'Patch reserve' },
  ],
  consistentMapInd: [
    { code: 'Y', description: 'Yes' },
    { code: 'N', description: 'No' },
    { code: 'M', description: 'Not mapped' },
  ],
  patchLocationCode: [
    { code: 'IB', description: 'Internal to block' },
    { code: 'EB', description: 'Edge of block' },
    { code: 'ENTB', description: 'External / not touching block' },
    { code: 'NA', description: 'N/A' },
  ],
  windthrowDistributionCode: [
    { code: 'E', description: 'Edge' },
    { code: 'I', description: 'Internal' },
    { code: 'B', description: 'Both' },
    { code: 'NA', description: 'N/A' },
  ],
};

const WINDTHROW_TREATMENT_OPTIONS = [
  { code: 'F', label: 'Feathering' },
  { code: 'T', label: 'Topping' },
  { code: 'N', label: 'None' },
];

const REQUIRED_KEYS = new Set([
  'stratumNumber',
  'strataTypeCode',
  'consistentMapInd',
  'plotCount',
  'harvestAreaCode',
  'bgcZoneCode',
  // Legacy FREP211 (PT #43888) requires BGC subzone in addition to BGC zone.
  'bgcSubzoneCode',
]);

const CONSTRAINT_PCT_KEYS = [
  'wetlandPct',
  'riparianManagementZonePct',
  'riparianReserveZonePct',
  'rockOutcropPct',
  'nonCommercialBrushPct',
  'nonMerchTimberPct',
  'sensitiveSoilPct',
  'ungHoofAnimalWinteringPct',
  'wildlifeHabitatAreaPct',
  'oldGrowthManagementAreaPct',
  'visualsPct',
  'culturalHeritageFeaturePct',
  'recreationFeaturePct',
  'otherConstraintPct',
];
const ECO_COUNT_KEYS = [
  'bearDenCnt',
  'hibernaculumCnt',
  'vetTreeCnt',
  'mineralLickCnt',
  'largeStickNestCnt',
  'cavityNestCnt',
  'largeHallowTreeCnt',
  'largeWitchesBroomCnt',
  'otherEcoAnchorCnt',
];
const ECO_CHECKBOX_KEYS = [
  'karstFeatureInd',
  'largestTreeInd',
  'cwdHeavyConcentrationInd',
  'activeWildlifeTrailsInd',
  'activeWltCwdFeedingInd',
  'uncommonTreeSpeciesInd',
];
/**
 * Free-text fields rendered as a multi-line box rather than a single-line input. Only the general
 * comment earns it — the other two limited fields are short labels ("Other constraint" at 50,
 * "Other eco anchor description" at 30) that read fine on one line.
 */
const MULTILINE_KEYS = new Set(['patchGeneralComment']);

// Full groups the legacy enable/disable cascade clears when a group is turned off.
const CONSTRAINT_KEYS = [...CONSTRAINT_PCT_KEYS, 'otherConstraint', 'constrainedTotal'];
const ECO_KEYS = [...ECO_COUNT_KEYS, 'otherEcoAnchorDesc', ...ECO_CHECKBOX_KEYS];
const PATCH_KEYS = [
  'patchLocationCode',
  'patchEstimatedOldestTreeAge',
  'patchWindthrowPct',
  'windthrowDistributionCode',
  'otherWindthrowTreatment',
];

const LABELS: Record<string, string> = Object.fromEntries(
  TEXT_GROUPS.flatMap((g) => g.fields.map((f) => [f.key, f.label])),
);

// Two-column Reserve Constraints (left) / Ecological Anchors (right) rows, in legacy order
// (frep211StratumSummary.jsp). Eco anchors are counts for the first 8, checkboxes for the rest.
const CONSTRAINT_ECO_ROWS: {
  cLabel: string;
  cKey: string;
  eLabel: string;
  eKey: string;
  eKind: 'count' | 'check';
}[] = [
  { cLabel: 'Wetland', cKey: 'wetlandPct', eLabel: 'Bear den', eKey: 'bearDenCnt', eKind: 'count' },
  {
    cLabel: 'RMZ',
    cKey: 'riparianManagementZonePct',
    eLabel: 'Hibernaculum',
    eKey: 'hibernaculumCnt',
    eKind: 'count',
  },
  {
    cLabel: 'RRZ',
    cKey: 'riparianReserveZonePct',
    eLabel: 'Vet tree/ha',
    eKey: 'vetTreeCnt',
    eKind: 'count',
  },
  {
    cLabel: 'Rock outcrop',
    cKey: 'rockOutcropPct',
    eLabel: 'Mineral lick',
    eKey: 'mineralLickCnt',
    eKind: 'count',
  },
  {
    cLabel: 'Non-commercial brush',
    cKey: 'nonCommercialBrushPct',
    eLabel: 'Large stick nest',
    eKey: 'largeStickNestCnt',
    eKind: 'count',
  },
  {
    cLabel: 'Non (or low) merch timber',
    cKey: 'nonMerchTimberPct',
    eLabel: 'Cavity nest',
    eKey: 'cavityNestCnt',
    eKind: 'count',
  },
  {
    cLabel: 'Sensitive terrain or soil',
    cKey: 'sensitiveSoilPct',
    eLabel: 'Large hollow tree',
    eKey: 'largeHallowTreeCnt',
    eKind: 'count',
  },
  {
    cLabel: 'UWR',
    cKey: 'ungHoofAnimalWinteringPct',
    eLabel: 'Large witches broom',
    eKey: 'largeWitchesBroomCnt',
    eKind: 'count',
  },
  {
    cLabel: 'WHA',
    cKey: 'wildlifeHabitatAreaPct',
    eLabel: 'Karst feature',
    eKey: 'karstFeatureInd',
    eKind: 'check',
  },
  {
    cLabel: 'OGMA',
    cKey: 'oldGrowthManagementAreaPct',
    eLabel: 'Largest tree for site (not Vets)',
    eKey: 'largestTreeInd',
    eKind: 'check',
  },
  {
    cLabel: 'Visuals',
    cKey: 'visualsPct',
    eLabel: 'CWD heavy natural concentration',
    eKey: 'cwdHeavyConcentrationInd',
    eKind: 'check',
  },
  {
    cLabel: 'Cultural Heritage Feature',
    cKey: 'culturalHeritageFeaturePct',
    eLabel: 'Active wildlife trails',
    eKey: 'activeWildlifeTrailsInd',
    eKind: 'check',
  },
  {
    cLabel: 'Recreation feature',
    cKey: 'recreationFeaturePct',
    eLabel: 'Active WLT/CWD feeding',
    eKey: 'activeWltCwdFeedingInd',
    eKind: 'check',
  },
];

/**
 * The fields that make up a BEC combination, in form order. One list so the catalogue search and
 * the copy-from-previous action can never drift apart.
 */
const BEC_KEYS = [
  'bgcZoneCode',
  'bgcSubzoneCode',
  'bgcVariant',
  'bgcPhase',
  'becSiteSeriesCd',
  'siteSeriesPhaseCd',
  'seral',
] as const;

// BEC search modal criteria fields.
const BEC_CRITERIA: FieldDef[] = [
  { key: 'zone', label: 'BGC zone' },
  { key: 'subzone', label: 'Subzone' },
  { key: 'variant', label: 'Variant' },
  { key: 'phase', label: 'Phase' },
  { key: 'siteSeries', label: 'Site series' },
  { key: 'siteSeriesPhase', label: 'Site series phase' },
  { key: 'seral', label: 'Seral' },
];

// Mirrors FREP_BIODIVERSITY_STRATUM.validate_stratum_number: the mask is
// letters-then-digits (e.g. AB12) — first char must be a letter, no letter may
// follow a digit, ≤3 letters, ≤2 digits, length ≤5, no spaces. Empty is left to
// the required check (the proc only validates a non-null value).
const stratumNumberValid = (value: string): boolean => {
  if (!value) return true;
  if (value.length > 5 || value.includes(' ')) return false;
  const isDigit = (c: string) => c >= '0' && c <= '9';
  if (isDigit(value[0])) return false; // first character cannot be a digit
  let seenDigit = false;
  let digits = 0;
  let letters = 0;
  for (const c of value) {
    if (isDigit(c)) {
      seenDigit = true;
      digits += 1;
    } else {
      if (seenDigit) return false; // a letter may not follow a digit
      letters += 1;
    }
  }
  return digits <= 2 && letters <= 3;
};

const isIntInRange = (s: string, min: number, max: number): boolean =>
  /^\d+$/.test(s) && Number(s) >= min && Number(s) <= max;
const isNumInRange = (s: string, min: number, max: number): boolean => {
  const n = Number(s);
  return s !== '' && !Number.isNaN(n) && n >= min && n <= max;
};

// --- Stratum validation, split into rule groups so each stays simple (mirrors
// Frep211ValidationManager + the proc's validate()). Each group mutates the field-keyed error map
// `e`; the first rule to flag a field wins (guarded by the `!e[k]` checks). `v` reads a trimmed value.
type StratumErrors = Record<string, string>;
type ValueReader = (key: string) => string;

const decimalPlaces = (s: string): number => {
  const dot = s.indexOf('.');
  return dot < 0 ? 0 : s.length - dot - 1;
};

/** Range check for one field (whole-number or numeric); no-op when already errored or blank. */
const checkRange = (
  e: StratumErrors,
  v: ValueReader,
  k: string,
  min: number,
  max: number,
  integer: boolean,
  mode: ValidationMode = 'settled',
) => {
  if (e[k] || !v(k)) return;
  const text = v(k);
  const ok = integer ? isIntInRange(text, min, max) : isNumInRange(text, min, max);
  if (ok) return;
  // While the user is still typing, hold back the one failure more typing can fix: a value below
  // the floor is on its way up. Anything malformed, or already past the ceiling, is said at once.
  if (mode === 'typing') {
    if (isNumberInProgress(text)) return;
    const n = Number(text);
    if (Number.isFinite(n) && n < min) return;
  }
  // Name the end that actually failed. One message covering shape and both bounds ran to "Wetland %
  // must be a whole number from 1 to 100." — too long for the bare table cells these percentages and
  // counts live in, where it was clipped mid-sentence, and it described three rules to someone who
  // broke one.
  const label = LABELS[k] ?? k;
  const n = Number(text);
  if (!Number.isFinite(n) || (integer && !Number.isInteger(n))) {
    e[k] = `${label} must be ${integer ? 'a whole number' : 'a number'}.`;
  } else {
    e[k] = n > max ? `${label} must be at most ${max}.` : `${label} must be at least ${min}.`;
  }
};

/**
 * Fields whose "not filled in yet" error is advisory: nullable columns the tab still marks required
 * and still counts against submit, but that no longer stop a stratum being stored.
 *
 * Deliberately excluded — the database refuses these outright (BIODIVERSITY_STRATUM declares them
 * NOT NULL, and FREP_BIODIVERSITY_STRATUM.validate_mandatories re-checks three of them): plot count,
 * consistent-with-map, harvest area and BGC zone. BGC subzone joins them because validate_bec runs
 * the whole BEC combination through FREP_VALIDATE_BGC.
 */
const ADVISORY_WHEN_BLANK = new Set([
  'stratumNumber',
  'strataTypeCode',
  'size',
  'estimatedSize',
  'patchWindthrowPct',
  'patchLocationCode',
]);

/** Current value of a stratum field, for the advisory test above. */
const valueOf = (stratum: BioStratum | null, key: string): string =>
  ((stratum as unknown as Record<string, string | undefined>)?.[key] ?? '').trim();

const checkRequiredAndFormat = (e: StratumErrors, v: ValueReader) => {
  REQUIRED_KEYS.forEach((k) => {
    if (!v(k)) e[k] = `${LABELS[k] ?? k} is required.`;
  });
  if (!e.stratumNumber && !stratumNumberValid(v('stratumNumber'))) {
    e.stratumNumber = 'Use 1-3 letters then 0-2 digits, e.g. AB12.';
  }
  // A stratum with 0 plots is only valid for a patch stratum type (legacy numplots.zero).
  const type = v('strataTypeCode');
  if (!e.plotCount && v('plotCount') === '0' && type && !type.startsWith('P')) {
    e.plotCount = 'A stratum with 0 plots must be a patch stratum type.';
  }
};

const checkSizeConsistency = (e: StratumErrors, v: ValueReader) => {
  const type = v('strataTypeCode');
  const harvestVal = v('harvestAreaCode');
  const consistent = v('consistentMapInd');
  if (consistent === 'Y' && !v('size')) {
    e.size = 'Stratum size is required when consistent with map is "Yes".';
  }
  if ((consistent === 'N' || consistent === 'M') && !v('estimatedSize')) {
    e.estimatedSize = 'Estimated size is required when not consistent with map.';
  }
  if (!e.size && consistent === 'M' && v('size') && Number(v('size')) !== 0) {
    e.size = 'Stratum size must be blank when "Not mapped".';
  }
  if (harvestVal === 'PCH' && !v('patchWindthrowPct')) {
    e.patchWindthrowPct = '% of trees windthrown is required for a patch reserve.';
  }
  if (type.startsWith('P') && !v('patchLocationCode')) {
    e.patchLocationCode = 'Patch location is required for a patch stratum type.';
  }
};

const checkNumericRanges = (e: StratumErrors, v: ValueReader, mode: ValidationMode) => {
  checkRange(e, v, 'plotCount', 0, 99, true, mode);
  checkRange(e, v, 'size', 0.01, 9999.99, false, mode);
  checkRange(e, v, 'estimatedSize', 0.01, 9999.99, false, mode);
  checkRange(e, v, 'patchEstimatedOldestTreeAge', 0, 999, true, mode);
  checkRange(e, v, 'patchWindthrowPct', 0, 100, false, mode);
  CONSTRAINT_PCT_KEYS.forEach((k) => checkRange(e, v, k, 1, 100, true, mode));
  ECO_COUNT_KEYS.forEach((k) => checkRange(e, v, k, 1, 999, true, mode));
};

const checkDecimalsAndLengths = (e: StratumErrors, v: ValueReader) => {
  // Decimal-place limits: stratum/estimated size ≤2, % windthrown ≤1.
  if (!e.size && v('size') && decimalPlaces(v('size')) > 2) {
    e.size = 'Mapped stratum size can have at most 2 decimal places.';
  }
  if (!e.estimatedSize && v('estimatedSize') && decimalPlaces(v('estimatedSize')) > 2) {
    e.estimatedSize = 'Estimated size can have at most 2 decimal places.';
  }
  if (!e.patchWindthrowPct && v('patchWindthrowPct') && decimalPlaces(v('patchWindthrowPct')) > 1) {
    e.patchWindthrowPct = '% of trees windthrown can have at most 1 decimal place.';
  }
  // Free-text length limits.
  if (
    !e.otherConstraint &&
    byteLength(v('otherConstraint')) > STRATUM_TEXT_LIMITS.otherConstraint
  ) {
    e.otherConstraint = `Other constraint — ${overLimitError(
      v('otherConstraint'),
      STRATUM_TEXT_LIMITS.otherConstraint,
    )}`;
  }
  if (
    !e.otherEcoAnchorDesc &&
    byteLength(v('otherEcoAnchorDesc')) > STRATUM_TEXT_LIMITS.otherEcoAnchorDesc
  ) {
    e.otherEcoAnchorDesc = `Other eco anchor description — ${overLimitError(
      v('otherEcoAnchorDesc'),
      STRATUM_TEXT_LIMITS.otherEcoAnchorDesc,
    )}`;
  }
  if (
    !e.patchGeneralComment &&
    byteLength(v('patchGeneralComment')) > STRATUM_TEXT_LIMITS.patchGeneralComment
  ) {
    e.patchGeneralComment = `Comments — ${overLimitError(
      v('patchGeneralComment'),
      STRATUM_TEXT_LIMITS.patchGeneralComment,
    )}`;
  }
};

const checkConstrainedTotal = (e: StratumErrors, v: ValueReader, mode: ValidationMode) => {
  // Total constrained: 0–100, ≥ largest single %, and ≥1 constraint if total > 0.
  const totalStr = v('constrainedTotal');
  if (!totalStr || e.constrainedTotal) return;
  if (!isIntInRange(totalStr, 0, 100)) {
    if (mode === 'typing' && isNumberInProgress(totalStr)) return;
    e.constrainedTotal = 'Total constrained % must be a whole number, 0 to 100.';
    return;
  }
  // The two rules below compare this field against the constraint percentages, so they read as an
  // accusation while one of those is still being typed. They wait for Save.
  if (mode === 'typing') return;
  const total = Number(totalStr);
  const maxSingle = Math.max(0, ...CONSTRAINT_PCT_KEYS.map((k) => Number(v(k)) || 0));
  if (total > 0 && total < maxSingle) {
    e.constrainedTotal = 'Total constrained must be at least the largest single constraint %.';
  } else if (total > 0 && maxSingle === 0) {
    e.constrainedTotal = 'Enter at least one constraint when total constrained is greater than 0.';
  }
};

const checkHarvestArea = (e: StratumErrors, v: ValueReader) => {
  const type = v('strataTypeCode');
  if (!type || e.harvestAreaCode) return;
  const harvestVal = v('harvestAreaCode');
  const isPatch = type.startsWith('P');
  if (isPatch && harvestVal && harvestVal !== 'PCH') {
    e.harvestAreaCode = 'A patch stratum type requires harvest area "Patch reserve".';
  } else if (!isPatch && harvestVal === 'PCH') {
    e.harvestAreaCode = 'Harvest area "Patch reserve" is only valid for a patch stratum type.';
  }
};

const checkPatchLocation = (e: StratumErrors, v: ValueReader) => {
  if (e.patchLocationCode) return;
  const harvestVal = v('harvestAreaCode');
  const loc = v('patchLocationCode');
  if (harvestVal === 'PCH' && loc === 'NA') {
    e.patchLocationCode = 'Patch location cannot be N/A for a patch reserve.';
  } else if (harvestVal === 'HDR' && loc && loc !== 'NA') {
    e.patchLocationCode = 'Patch location must be N/A for dispersed retention.';
  }
};

const checkCrossField = (
  e: StratumErrors,
  v: ValueReader,
  treatmentChecked: (code: string) => boolean,
) => {
  checkHarvestArea(e, v);
  checkPatchLocation(e, v);
  if (
    treatmentChecked('N') &&
    (treatmentChecked('F') || treatmentChecked('T') || v('otherWindthrowTreatment'))
  ) {
    e.otherWindthrowTreatment =
      'Windthrow treatment "None" cannot be combined with other treatments.';
  }
  if (Boolean(v('otherConstraint')) !== Boolean(v('otherConstraintPct'))) {
    e.otherConstraintPct = 'Other constraint needs both a name and a %.';
  }
  if (Boolean(v('otherEcoAnchorDesc')) !== Boolean(v('otherEcoAnchorCnt'))) {
    e.otherEcoAnchorCnt = 'Other eco anchor needs both a description and a count.';
  }
};

const BioStratumView: FC<Props> = ({
  checklistId,
  canEdit,
  submitted,
  onSaved,
  outstanding = [],
  tone,
}) => {
  const { display } = useNotification();
  const confirm = useConfirm();
  const [rows, setRows] = useState<BioStratumRow[]>([]);
  const [current, setCurrent] = useState<BioStratum | null>(null);
  // Errors stay hidden until a save is attempted on the open stratum; reset whenever a different
  // stratum is opened or a new one is added.
  const [showErrors, setShowErrors] = useState(false);
  const [computed, setComputed] = useState<StratumComputed | null>(null);
  const [strataTypes, setStrataTypes] = useState<CodeOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  // BEC search modal.
  const [becOpen, setBecOpen] = useState(false);
  const [becCriteria, setBecCriteria] = useState<Record<string, string>>({});
  const [becResults, setBecResults] = useState<BecRow[]>([]);
  const [becBusy, setBecBusy] = useState(false);

  // "Copy BEC from another stratum" dialog.
  const [becCopyOpen, setBecCopyOpen] = useState(false);
  const [becCopyBusy, setBecCopyBusy] = useState(false);
  const [becCopyRows, setBecCopyRows] = useState<{ stratumNumber: string; bec: BioStratum }[]>([]);

  const reportError = useCallback(
    (title: string, err: unknown) =>
      display({
        kind: 'error',
        title,
        subtitle: apiErrorMessage(err),
        timeout: 9000,
      }),
    [display],
  );

  const loadList = useCallback(async () => {
    const list = await API.protocolChecklist.listBioStrata(checklistId);
    setRows(list);
    return list;
  }, [checklistId]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    API.protocolChecklist
      .listBioStrata(checklistId)
      .then((list) => {
        if (!cancelled) setRows(list);
      })
      .catch((err: unknown) => {
        if (!cancelled) reportError("We couldn't load the strata", err);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    API.configuration
      .getStrataTypes()
      .then((opts) => !cancelled && setStrataTypes(opts))
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
    // Load once per checklist. reportError is intentionally excluded — including it would re-run
    // (and cancel) the load on every render whenever the notification context isn't referentially
    // stable, leaving `loading` stuck true.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [checklistId]);

  const readOnly = !canEdit || submitted;

  const get = (key: string): string =>
    ((current as Record<string, unknown>)?.[key] as string | undefined) ?? '';
  const set = (key: string, value: string) =>
    setCurrent((prev) => (prev ? ({ ...prev, [key]: value } as BioStratum) : prev));

  const optionsFor = (key: string): CodeOption[] | undefined =>
    key === 'strataTypeCode' ? strataTypes : STATIC_OPTIONS[key];

  // --- conditional enable/disable cascade (legacy stratumType / harvestAreaCode / *Indicator JS) ---
  const harvest = get('harvestAreaCode');
  const constraintsOff = get('constraintIndicator') !== 'Y';
  const ecoOff = get('ecoIndicator') !== 'Y';
  const patchOff = harvest === 'HNR';
  const isCC = get('strataTypeCode') === 'CC';

  const disabledKey = (key: string): boolean => {
    if (CONSTRAINT_KEYS.includes(key)) return constraintsOff;
    if (ECO_KEYS.includes(key)) return ecoOff;
    if (PATCH_KEYS.includes(key)) return patchOff;
    return false;
  };

  const clearAll = (obj: Record<string, unknown>, keys: string[]) =>
    keys.forEach((k) => {
      obj[k] = '';
    });

  // Uncheck every windthrow row (saved → delete, unsaved → no-op) while keeping the
  // full code list so the SAVE_STRATUM VARRAY is never empty. Used when harvest=HNR.
  const clearedTreatments = (prev: BioStratum) =>
    (prev.windthrowTreatments ?? []).map((t) => ({ ...t, checkInd: 'N' }));

  const applyStratumType = (value: string) =>
    setCurrent((prev) => {
      if (!prev) return prev;
      const next = { ...prev, strataTypeCode: value } as BioStratum;
      const o = next as Record<string, unknown>;
      if (value === 'CC') {
        o.harvestAreaCode = 'HNR';
        o.constraintIndicator = 'N';
        o.ecoIndicator = 'N';
        clearAll(o, [...CONSTRAINT_KEYS, ...ECO_KEYS]);
      } else if (['DO', 'DR', 'DT', 'DW'].includes(value)) {
        o.harvestAreaCode = 'HDR';
      } else if (value.startsWith('P')) {
        o.harvestAreaCode = 'PCH';
      }
      if (o.harvestAreaCode === 'HNR') {
        clearAll(o, PATCH_KEYS);
        next.windthrowTreatments = clearedTreatments(prev);
      }
      return next;
    });

  const applyHarvest = (value: string) =>
    setCurrent((prev) => {
      if (!prev) return prev;
      const next = { ...prev, harvestAreaCode: value } as BioStratum;
      if (value === 'HNR') {
        clearAll(next as Record<string, unknown>, PATCH_KEYS);
        next.windthrowTreatments = clearedTreatments(prev);
      }
      return next;
    });

  const toggleIndicator = (key: 'constraintIndicator' | 'ecoIndicator', checked: boolean) =>
    setCurrent((prev) => {
      if (!prev) return prev;
      const next = { ...prev, [key]: checked ? 'Y' : 'N' } as BioStratum;
      if (!checked) {
        clearAll(
          next as Record<string, unknown>,
          key === 'constraintIndicator' ? CONSTRAINT_KEYS : ECO_KEYS,
        );
      }
      return next;
    });

  const select = async (stratumId: string) => {
    setBusy(true);
    try {
      const [s, c] = await Promise.all([
        API.protocolChecklist.getBioStratum(stratumId),
        // Computed NAR/plots are non-blocking — never fail the form if they can't load.
        API.protocolChecklist.getStratumComputed(stratumId).catch(() => null),
      ]);
      setCurrent(s);
      setComputed(c);
      setShowErrors(false);
      resetSettled();
    } catch (err) {
      reportError('Could not load the stratum', err);
    } finally {
      setBusy(false);
    }
  };

  const addStratum = async () => {
    setShowErrors(false);
    resetSettled();
    // The legacy `add_new` default was a sequence value that always failed its own
    // format validation, so we don't prefill the Stratum Id. Seed the full windthrow
    // code list (all unchecked) — SAVE_STRATUM loops the VARRAY and crashes on empty.
    setCurrent({
      checklistId,
      windthrowTreatments: WINDTHROW_TREATMENT_OPTIONS.map((o) => ({
        code: o.code,
        checkInd: 'N',
      })),
    });
    // NAR is site-level (shown even for a new stratum); plots-completed is 0 — matches
    // the legacy "Add New" flow. Non-blocking: leave the header blank if it can't load.
    setComputed(await API.protocolChecklist.getNewStratumComputed(checklistId).catch(() => null));
  };

  // --- windthrow treatment checkboxes (toggle VARRAY membership) ---
  const treatments = current?.windthrowTreatments ?? [];
  const treatmentChecked = (code: string): boolean =>
    treatments.some((t) => t.code === code && t.checkInd !== 'N');
  // Flip check_ind in place, preserving the full code list (and any saved row id) so
  // the SAVE_STRATUM VARRAY is never empty: saved+unchecked → delete, new+checked →
  // insert, the other two combinations → no-op (matches save_windthrow_treatment).
  const toggleTreatment = (code: string, checked: boolean) =>
    setCurrent((prev) => {
      if (!prev) return prev;
      const list = prev.windthrowTreatments ?? [];
      const checkInd = checked ? 'Y' : 'N';
      const next = list.some((t) => t.code === code)
        ? list.map((t) => (t.code === code ? { ...t, checkInd } : t))
        : [...list, { code, checkInd }];
      return { ...prev, windthrowTreatments: next };
    });

  // --- BEC search modal ---
  const runBecSearch = async () => {
    setBecBusy(true);
    try {
      setBecResults(await API.configuration.searchBec(becCriteria));
    } catch (err) {
      reportError('BEC search failed', err);
    } finally {
      setBecBusy(false);
    }
  };
  /**
   * Set the whole BEC combination from a source that carries the same field names — a catalogue row
   * or another stratum.
   *
   * All seven together, never a subset: `FREP_VALIDATE_BGC` validates the combination, so a
   * half-copied BEC is a validation failure rather than a head start.
   */
  const applyBecFrom = (source: Partial<Record<(typeof BEC_KEYS)[number], string | undefined>>) =>
    setCurrent((prev) =>
      prev ? { ...prev, ...Object.fromEntries(BEC_KEYS.map((k) => [k, source[k] ?? ''])) } : prev,
    );

  const applyBec = (r: BecRow) => {
    applyBecFrom(r);
    setBecOpen(false);
  };

  /** The other strata on this checklist — the ones whose BEC can be copied. */
  const otherRows = rows.filter((r) => !current?.stratumId || r.stratumId !== current.stratumId);

  /**
   * Load the BEC every other stratum is using, so the user can pick any of them rather than only
   * the one immediately before this stratum.
   *
   * Fetched when the dialog opens, not when the form does: the list rows carry no BEC fields, so
   * this is one read per stratum and most stratum edits never ask for it.
   *
   * Strata within one block usually share a BEC, which is why the browser's own autofill was being
   * used for it — but that fills whatever else it infers belongs to the same group, and the rest of
   * a stratum is per-stratum evaluation data. Copying only the seven named BEC fields makes it
   * impossible for this to touch anything else. See utils/autofill.ts for the other half of that fix.
   */
  const openBecCopy = async () => {
    setBecCopyOpen(true);
    setBecCopyBusy(true);
    try {
      const loaded = await Promise.all(
        otherRows
          .filter((r) => r.stratumId)
          .map(async (r) => ({
            stratumNumber: r.stratumNumber ?? r.stratumId ?? '',
            bec: await API.protocolChecklist.getBioStratum(r.stratumId as string),
          })),
      );
      // Only strata that actually have a BEC, and one entry per distinct combination — repeating
      // the same BEC once per stratum would make the list longer without offering another choice.
      const seen = new Set<string>();
      setBecCopyRows(
        loaded
          .filter(({ bec }) => (bec.bgcZoneCode ?? '').trim() !== '')
          .filter(({ bec }) => {
            const key = BEC_KEYS.map((k) => (bec[k] ?? '').trim().toLowerCase()).join('|');
            if (seen.has(key)) return false;
            seen.add(key);
            return true;
          }),
      );
    } catch (err) {
      reportError("We couldn't load the other strata's BEC", err);
      setBecCopyRows([]);
    } finally {
      setBecCopyBusy(false);
    }
  };

  // Mirror Frep211ValidationManager + the proc's validate(). Returns a field-keyed map (one message
  // per field) so each error renders inline on its own input; the first rule to flag a field wins.
  // Orchestrates the rule groups (each a small module-level function); the first rule to flag a
  // field wins. Mirrors Frep211ValidationManager + the proc's validate().
  const validate = (mode: ValidationMode = 'settled'): Record<string, string> => {
    const e: StratumErrors = {};
    const v: ValueReader = (k) => get(k).trim();
    // Blank required fields, the stratum-number pattern and the size/type consistency rules are all
    // states a correct entry passes through, so they are held back while the user is still typing.
    // Ranges and decimal places run in both modes — the mode decides which half of a range applies.
    if (mode === 'settled') {
      checkRequiredAndFormat(e, v);
      checkSizeConsistency(e, v);
    }
    checkNumericRanges(e, v, mode);
    checkDecimalsAndLengths(e, v);
    checkConstrainedTotal(e, v, mode);
    if (mode === 'settled') checkCrossField(e, v, treatmentChecked);
    return e;
  };

  // Validation runs live off the edited stratum, but is only *displayed* once a save has been
  // attempted — opening an incomplete stratum should not greet the user with a wall of red. See the
  // same gate in BioOpeningView. `allErrors` drives the save guard, `fieldErrors` the rendering, so
  // every `invalid`/`invalidText` site below is gated without touching each one.
  const allErrors: Record<string, string> = current && !readOnly ? validate() : {};
  // Before the first save attempt, only the errors no further typing can rescue — see the same gate
  // in BioPlotsView and utils/validation.ts.
  const typingErrors: Record<string, string> = current && !readOnly ? validate('typing') : {};
  // Between the two: once a field has been filled in and left, it is finished enough to judge
  // against the full rules — the Stratum Id's pattern, a size below its floor. Blank fields are
  // exempt, so tabbing through an empty stratum marks nothing.
  const { settled, markSettled, resetSettled } = useSettledFields();
  const fieldErrors = showErrors
    ? allErrors
    : { ...typingErrors, ...errorsForSettledFields(allErrors, settled, (key) => get(key)) };

  // Which of those errors actually stop the save. A blank field in ADVISORY_WHEN_BLANK is a gap —
  // marked, counted on the tab and blocking submit, but stored happily. A field that *has* a value
  // can only be failing a format or range rule, so its error still blocks.
  const blockingErrors = Object.fromEntries(
    Object.entries(allErrors).filter(
      ([key]) => !(ADVISORY_WHEN_BLANK.has(key) && !valueOf(current, key)),
    ),
  );
  const hasErrors = Object.keys(blockingErrors).length > 0;

  const handleSave = async () => {
    if (!current) return;
    // First point the user has asked for the form to be complete — reveal the errors now.
    setShowErrors(true);
    if (hasErrors) return;
    setBusy(true);
    try {
      await API.protocolChecklist.saveBioStratum(checklistId, current);
      setCurrent(null);
      setComputed(null);
      await loadList();
      onSaved?.();
      display({ kind: 'success', title: 'Stratum saved', timeout: 4000 });
    } catch (err) {
      reportError('Save failed', err);
    } finally {
      setBusy(false);
    }
  };

  const deleteRow = async (row: BioStratumRow) => {
    if (!row.stratumId) return;
    if (
      !(await confirm({
        title: 'Are you sure you want to delete this stratum?',
        message: (
          <>
            <strong>Stratum {row.stratumNumber || row.stratumId}</strong> will be permanently
            deleted from this checklist. This action cannot be undone.
          </>
        ),
      }))
    )
      return;
    setBusy(true);
    try {
      await API.protocolChecklist.deleteBioStratum(row.stratumId, row.revisionCount ?? '');
      await loadList();
      onSaved?.();
      display({ kind: 'success', title: 'Stratum deleted', timeout: 4000 });
    } catch (err) {
      reportError('Delete failed', err);
    } finally {
      setBusy(false);
    }
  };

  // Map a strata type code to its display label (falls back to the raw code).
  const strataTypeLabel = (code?: string): string =>
    (code && strataTypes.find((t) => t.code === code)?.description) || code || '';

  // The change handler for a field: strata-type and harvest-area cascade to other fields; everything
  // else is a plain set().
  const onChangeFor = (key: string): ((val: string) => void) => {
    if (key === 'strataTypeCode') return applyStratumType;
    if (key === 'harvestAreaCode') return applyHarvest;
    return (val: string) => set(key, val);
  };

  // Read-only display of a Yes/No indicator field.
  const yesNo = (key: string): string => (get(key) === 'Y' ? 'Yes' : 'No');

  /**
   * Whether the field is owed, for the asterisk.
   *
   * {@link REQUIRED_KEYS} covers the fields that are always owed. The two sizes are conditional —
   * which one applies depends on the map-consistency answer — so they are decided here, on the same
   * test the validation below uses, rather than being left unmarked because a static set could not
   * express them.
   */
  const isRequired = (key: string): boolean => {
    if (REQUIRED_KEYS.has(key)) return true;
    const consistent = get('consistentMapInd');
    if (key === 'size') return consistent === 'Y';
    if (key === 'estimatedSize') return consistent === 'N' || consistent === 'M';
    return false;
  };

  const field = (key: string, label: string): ReactNode => {
    const lbl = requiredLabel(label, isRequired(key));
    const opts = optionsFor(key);
    const disabled = disabledKey(key);
    const onChange = onChangeFor(key);

    if (opts) {
      if (readOnly) {
        const text = opts.find((o) => o.code === get(key))?.description ?? get(key);
        return (
          <div className="protocol-checklist__field" key={key}>
            <span className="protocol-checklist__label">{lbl}</span>
            <span className="protocol-checklist__value">{text || '—'}</span>
          </div>
        );
      }
      return (
        <Select
          autoComplete="off"
          key={key}
          id={`stratum-${key}`}
          labelText={lbl}
          value={get(key)}
          disabled={disabled}
          invalid={Boolean(fieldErrors[key])}
          invalidText={fieldErrors[key]}
          onChange={(e) => onChange(e.target.value)}
        >
          <SelectItem value="" text="Choose an option" />
          {opts.map((o) => (
            <SelectItem key={o.code} value={o.code} text={o.description} />
          ))}
        </Select>
      );
    }

    if (key === 'summaryDate' && !readOnly) {
      // Summary date uses a calendar picker (writes back the YYYY-MM-DD the proc expects).
      return (
        <DatePicker
          key={key}
          className="frep-date-picker"
          datePickerType="single"
          dateFormat="Y-m-d"
          value={get(key) ? [get(key)] : []}
          onChange={(dates: Date[]) =>
            set(key, dates[0] ? dates[0].toISOString().slice(0, 10) : '')
          }
        >
          <DatePickerInput
            {...NO_AUTOFILL}
            id={`stratum-${key}`}
            labelText={lbl}
            placeholder="YYYY-MM-DD"
            disabled={disabled}
          />
        </DatePicker>
      );
    }

    if (readOnly) {
      return (
        <div className="protocol-checklist__field" key={key}>
          <span className="protocol-checklist__label">{lbl}</span>
          <span className="protocol-checklist__value">
            {(key === 'summaryDate' ? formatShortDate(get(key)) : get(key)) || '—'}
          </span>
        </div>
      );
    }
    const inputProps = {
      key,
      id: `stratum-${key}`,
      // Off across the checklist forms: every field keeps a stable id across strata / plots /
      // features, so the browser treats the next one as the same field and offers what was typed
      // last time. Accepting one suggestion then cascades into the rest of the group — these are
      // per-record evaluation values, never a repeat of the previous record.
      autoComplete: 'off',

      labelText: lbl,
      value: get(key),
      // Undefined for anything not in the map — notably patchGeneralComment, which uses the byte
      // counter below instead. A field takes one mechanism or the other, never both.
      maxLength: STRATUM_FIELD_MAX[key],
      disabled,
      invalid: Boolean(fieldErrors[key]),
      invalidText: fieldErrors[key],
      onBlur: () => markSettled(key),
      onChange: (e: { target: { value: string } }) => set(key, e.target.value),
    };
    const input = MULTILINE_KEYS.has(key) ? (
      <TextArea {...inputProps} rows={4} />
    ) : (
      <TextInput {...inputProps} />
    );
    // Length-limited free text carries a live counter; checkStratumLengths already blocks the save
    // and supplies the error text, so the counter only reports the count.
    const limit = STRATUM_TEXT_LIMITS[key];
    return limit === undefined ? (
      input
    ) : (
      <FieldWithCounter key={key} used={byteLength(get(key))} limit={limit}>
        {input}
      </FieldWithCounter>
    );
  };

  // Read-only label/value cell (used for computed NAR / plots-completed).
  const roCell = (label: string, value: string): ReactNode => (
    <div className="protocol-checklist__field">
      <span className="protocol-checklist__label">{label}</span>
      <span className="protocol-checklist__value">{value || '—'}</span>
    </div>
  );

  // Bare numeric input for the constraint % / eco count table cells (label lives in its own cell).
  const numCell = (key: string): ReactNode =>
    readOnly ? (
      get(key) || '—'
    ) : (
      <TextInput
        autoComplete="off"
        id={`stratum-${key}`}
        labelText={LABELS[key] ?? key}
        hideLabel
        size="sm"
        value={get(key)}
        disabled={disabledKey(key)}
        invalid={Boolean(fieldErrors[key])}
        invalidText={fieldErrors[key]}
        onBlur={() => markSettled(key)}
        onChange={(e) => set(key, e.target.value)}
      />
    );

  // Bare eco-anchor checkbox cell (label supplied for the hidden a11y name).
  const checkCell = (key: string, label: string): ReactNode =>
    readOnly ? (
      yesNo(key)
    ) : (
      <Checkbox
        id={`stratum-${key}`}
        labelText={label}
        hideLabel
        checked={get(key) === 'Y'}
        disabled={ecoOff}
        onChange={(_e, { checked }) => set(key, checked ? 'Y' : 'N')}
      />
    );

  // The constraints/eco "present?" toggle cells (legacy "None" column position).
  const toggleCell = (key: 'constraintIndicator' | 'ecoIndicator'): ReactNode =>
    readOnly ? (
      yesNo(key)
    ) : (
      <Checkbox
        id={`stratum-${key}`}
        labelText={key}
        hideLabel
        checked={get(key) === 'Y'}
        disabled={isCC}
        onChange={(_e, { checked }) => toggleIndicator(key, checked)}
      />
    );

  if (loading) {
    return <SkeletonText paragraph lineCount={8} />;
  }

  return (
    <div className="rip-form">
      <OutstandingPanel groups={outstanding} tone={tone} />
      {/* The strata table and the stratum form are mutually exclusive — each takes the
          full width; the table is hidden while a stratum form is open. */}
      <div>
        {/* The stratum list + "Add stratum" is hidden while a stratum form is open. */}
        {!current && (
          <div className="bio-strata">
            {/* Add stratum sits top-right above the table; the + leads the label. */}
            {!readOnly && (
              <div className="bio-strata__toolbar">
                <Button
                  kind="tertiary"
                  size="lg"
                  className="bio-strata__add"
                  renderIcon={Add}
                  disabled={busy}
                  onClick={() => void addStratum()}
                >
                  Add stratum
                </Button>
              </div>
            )}
            {rows.length > 0 && (
              <Table size="sm" className="bio-strata__table">
                <TableHead>
                  <TableRow>
                    <TableHeader>Stratum number</TableHeader>
                    <TableHeader>Stratum type</TableHeader>
                    <TableHeader>Action</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow key={row.stratumId}>
                      <TableCell>{row.stratumNumber || row.stratumId}</TableCell>
                      <TableCell>{strataTypeLabel(row.strataTypeCode)}</TableCell>
                      <TableCell className="table-actions">
                        <Button
                          kind="ghost"
                          size="sm"
                          renderIcon={Edit}
                          disabled={busy}
                          onClick={() => void select(row.stratumId ?? '')}
                        >
                          Edit
                        </Button>
                        {!readOnly && (
                          <Button
                            kind="danger--ghost"
                            size="sm"
                            renderIcon={TrashCan}
                            disabled={busy}
                            onClick={() => void deleteRow(row)}
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
        )}

        <div className="bio-master__detail">
          {/* The form stays hidden until the user clicks "Add stratum" (new) or picks an
              existing stratum from the rail. */}
          {current && (
            <>
              {/* Actions at the top, mirroring the Opening info tab. */}
              <div className="protocol-checklist__section-actions">
                {!readOnly && (
                  <Button size="lg" disabled={busy} onClick={() => void handleSave()}>
                    Save
                  </Button>
                )}
                <Button
                  kind="ghost"
                  size="lg"
                  disabled={busy}
                  onClick={() => {
                    setCurrent(null);
                    setComputed(null);
                  }}
                >
                  Cancel
                </Button>
              </div>

              {/* Under the buttons, directly above the fields it describes — and only where fields
                  are marked, which is the open form, not the list behind it. */}
              {!readOnly && <RequiredLegend />}

              {/* Stratum Summary (legacy frep211StratumSummary.jsp top block) */}
              <fieldset className="rip-form__group">
                <legend>Stratum summary</legend>
                {/* Two rows of three: the stratum's identity, then its plots and size. Left as one
                    row the six ran the width of the page and read as an undifferentiated strip. */}
                <div className="rip-form__grid">
                  {field('stratumNumber', 'Stratum Id')}
                  {field('strataTypeCode', 'Stratum type')}
                  {roCell('NAR', computed?.nar ?? '')}
                </div>
                <div className="rip-form__grid">
                  {field('plotCount', '# of plots in stratum')}
                  {roCell('# of plots completed', computed?.plotsCompleted ?? '')}
                  {field('size', 'Mapped stratum size (ha)')}
                </div>
              </fieldset>

              {/* BEC */}
              <fieldset className="rip-form__group">
                <legend>BEC</legend>
                <div className="rip-form__grid">
                  {field('bgcZoneCode', 'BGC zone')}
                  {field('bgcSubzoneCode', 'BGC subzone')}
                  {field('bgcVariant', 'BGC variant')}
                  {field('bgcPhase', 'BGC phase')}
                  {field('becSiteSeriesCd', 'Site series')}
                  {field('siteSeriesPhaseCd', 'Site series phase')}
                  {field('seral', 'Seral')}
                </div>
                {!readOnly && (
                  <div className="rip-form__group-actions">
                    <Button kind="ghost" size="sm" onClick={() => setBecOpen(true)}>
                      Search BEC
                    </Button>
                    {/* Only offered when there is another stratum to copy from. */}
                    {otherRows.length > 0 && (
                      <Button
                        kind="ghost"
                        size="sm"
                        disabled={busy}
                        onClick={() => void openBecCopy()}
                      >
                        Same BEC as another stratum
                      </Button>
                    )}
                  </div>
                )}
              </fieldset>

              {/* Map consistency */}
              <fieldset className="rip-form__group">
                <legend>Map consistency</legend>
                <div className="rip-form__grid">
                  {field('consistentMapInd', 'Stratum location and size consistent with map?')}
                  {field('estimatedSize', "If 'no' or 'not mapped', estimated size (ha)")}
                </div>
              </fieldset>

              {/* Harvest area */}
              <fieldset className="rip-form__group">
                <legend>Harvest area</legend>
                <div className="rip-form__grid rip-form__grid--wide">
                  {field('harvestAreaCode', 'Tick one of')}
                </div>
              </fieldset>

              {/* Patch / Dispersed Summary */}
              <fieldset className="rip-form__group">
                <legend>Patch / dispersed summary</legend>
                <div className="rip-form__grid">
                  {field(
                    'patchEstimatedOldestTreeAge',
                    'Estimated age of oldest trees in reserve (other than Vets)',
                  )}
                  {field('patchLocationCode', 'Patch location')}
                  {field('patchWindthrowPct', '% of trees in reserve windthrown')}
                  {field('windthrowDistributionCode', 'Distribution of windthrow')}
                </div>
              </fieldset>

              {/* Windthrow treatment in reserve */}
              <fieldset className="rip-form__group">
                <legend>Windthrow treatment in reserve</legend>
                {readOnly ? (
                  <div className="protocol-checklist__field">
                    <span className="protocol-checklist__label">Treatments</span>
                    <span className="protocol-checklist__value">
                      {WINDTHROW_TREATMENT_OPTIONS.filter((o) => treatmentChecked(o.code))
                        .map((o) => o.label)
                        .join(', ') || '—'}
                    </span>
                  </div>
                ) : (
                  /* "Other" joins the treatments it belongs with rather than sitting on a row of
                     its own. Bottom-aligned so the checkboxes line up with the input beside them:
                     they carry no label above, so a top-aligned row left them floating a label's
                     height clear of it. */
                  <div className="rip-form__grid rip-form__grid--checks">
                    {WINDTHROW_TREATMENT_OPTIONS.map((o) => (
                      <Checkbox
                        key={o.code}
                        id={`wt-${o.code}`}
                        labelText={o.label}
                        checked={treatmentChecked(o.code)}
                        disabled={patchOff}
                        onChange={(_e, { checked }) => toggleTreatment(o.code, checked)}
                      />
                    ))}
                    {field('otherWindthrowTreatment', 'Other')}
                  </div>
                )}
              </fieldset>

              {/* Reserve Constraints | Ecological Anchors (two-column, legacy layout) */}
              <fieldset className="rip-form__group">
                <legend>Reserve constraints &amp; ecological anchors</legend>
                <table className="rip-field-grid">
                  <thead>
                    <tr>
                      <th scope="col">Reserve Constraints</th>
                      <th scope="col">% of reserve</th>
                      <th scope="col">Ecological Anchors</th>
                      <th scope="col">Stratum estimate</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td>Constraints present</td>
                      <td className="rip-grid__choice">{toggleCell('constraintIndicator')}</td>
                      <td>Eco anchors present</td>
                      <td className="rip-grid__choice">{toggleCell('ecoIndicator')}</td>
                    </tr>
                    {CONSTRAINT_ECO_ROWS.map((r) => (
                      <tr key={r.cKey}>
                        <td>{r.cLabel}</td>
                        <td>{numCell(r.cKey)}</td>
                        <td>{r.eLabel}</td>
                        <td className={r.eKind === 'check' ? 'rip-grid__choice' : undefined}>
                          {r.eKind === 'count' ? numCell(r.eKey) : checkCell(r.eKey, r.eLabel)}
                        </td>
                      </tr>
                    ))}
                    <tr>
                      <td>
                        Other:{' '}
                        {readOnly ? (
                          get('otherConstraint') || '—'
                        ) : (
                          <TextInput
                            autoComplete="off"
                            id="stratum-otherConstraint"
                            labelText="Other constraint"
                            hideLabel
                            size="sm"
                            // Rendered raw rather than through `textField`, so it gets neither the
                            // byte counter nor a visible error: invalidText collapses to zero
                            // height in a size="sm" table cell. The length rule above still blocks
                            // the save, but the user would see no reason why — so cap the input.
                            maxLength={STRATUM_TEXT_LIMITS.otherConstraint}
                            value={get('otherConstraint')}
                            disabled={disabledKey('otherConstraint')}
                            onChange={(e) => set('otherConstraint', e.target.value)}
                          />
                        )}
                      </td>
                      <td>{numCell('otherConstraintPct')}</td>
                      <td>Uncommon tree species</td>
                      <td className="rip-grid__choice">
                        {checkCell('uncommonTreeSpeciesInd', 'Uncommon tree species')}
                      </td>
                    </tr>
                    <tr>
                      <td>Total constrained</td>
                      <td>{numCell('constrainedTotal')}</td>
                      <td>
                        Other:{' '}
                        {readOnly ? (
                          get('otherEcoAnchorDesc') || '—'
                        ) : (
                          <TextInput
                            autoComplete="off"
                            id="stratum-otherEcoAnchorDesc"
                            labelText="Other eco anchor"
                            hideLabel
                            size="sm"
                            // Same as otherConstraint above — raw table cell, no counter or visible
                            // error, so the cap is the only feedback. 30 bytes is the tightest
                            // free-text column in either protocol.
                            maxLength={STRATUM_TEXT_LIMITS.otherEcoAnchorDesc}
                            value={get('otherEcoAnchorDesc')}
                            disabled={disabledKey('otherEcoAnchorDesc')}
                            onChange={(e) => set('otherEcoAnchorDesc', e.target.value)}
                          />
                        )}
                      </td>
                      <td>{numCell('otherEcoAnchorCnt')}</td>
                    </tr>
                  </tbody>
                </table>
                <div className="rip-form__grid">{field('patchGeneralComment', 'Comments')}</div>
              </fieldset>
            </>
          )}
        </div>
      </div>

      <Modal
        // Its own class as well as the shared one: the button that opens this dialog carries the
        // same words as its heading, so the dialog needs an identifier that is not its text.
        className="bec-modal bec-copy-modal"
        open={becCopyOpen}
        modalHeading="Same BEC as another stratum"
        passiveModal
        onRequestClose={() => setBecCopyOpen(false)}
        size="lg"
      >
        <p>
          Pick the stratum whose BEC this one shares. Only the BEC is copied — everything else on
          this stratum is its own evaluation.
        </p>
        {becCopyRows.length > 0 ? (
          <table className="rip-field-grid" style={{ marginTop: '1rem' }}>
            <thead>
              <tr>
                <th scope="col">Stratum</th>
                <th scope="col">Zone</th>
                <th scope="col">Subzone</th>
                <th scope="col">Variant</th>
                <th scope="col">Phase</th>
                <th scope="col">Site series</th>
                <th scope="col">Seral</th>
                <th scope="col" aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {becCopyRows.map(({ stratumNumber, bec }) => (
                <tr key={`bec-copy-${stratumNumber}`}>
                  <td>{stratumNumber || '—'}</td>
                  <td>{bec.bgcZoneCode || '—'}</td>
                  <td>{bec.bgcSubzoneCode || '—'}</td>
                  <td>{bec.bgcVariant || '—'}</td>
                  <td>{bec.bgcPhase || '—'}</td>
                  <td>{bec.becSiteSeriesCd || '—'}</td>
                  <td>{bec.seral || '—'}</td>
                  <td>
                    <Button
                      kind="ghost"
                      size="sm"
                      onClick={() => {
                        applyBecFrom(bec);
                        setBecCopyOpen(false);
                      }}
                    >
                      Select
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <p style={{ marginTop: '1rem' }}>
            {becCopyBusy ? 'Loading…' : 'None of the other strata have a BEC recorded yet.'}
          </p>
        )}
      </Modal>

      <Modal
        className="bec-modal"
        open={becOpen}
        modalHeading="Search the BEC catalogue"
        primaryButtonText="Search"
        secondaryButtonText="Close"
        primaryButtonDisabled={becBusy}
        onRequestSubmit={() => void runBecSearch()}
        onRequestClose={() => setBecOpen(false)}
        size="lg"
      >
        <div className="rip-form__grid">
          {BEC_CRITERIA.map((c) => (
            <TextInput
              autoComplete="off"
              key={c.key}
              id={`bec-${c.key}`}
              labelText={c.label}
              maxLength={BEC_SEARCH_MAX[c.key]}
              value={becCriteria[c.key] ?? ''}
              onChange={(e) => setBecCriteria((prev) => ({ ...prev, [c.key]: e.target.value }))}
            />
          ))}
        </div>
        {becResults.length > 0 ? (
          <table className="rip-field-grid" style={{ marginTop: '1rem' }}>
            <thead>
              <tr>
                <th scope="col">Zone</th>
                <th scope="col">Subzone</th>
                <th scope="col">Variant</th>
                <th scope="col">Phase</th>
                <th scope="col">Site series</th>
                <th scope="col">Seral</th>
                <th scope="col">Description</th>
                <th scope="col" aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {becResults.map((r, i) => (
                <tr key={`bec-${i}`}>
                  <td>{r.bgcZoneCode || '—'}</td>
                  <td>{r.bgcSubzoneCode || '—'}</td>
                  <td>{r.bgcVariant || '—'}</td>
                  <td>{r.bgcPhase || '—'}</td>
                  <td>{r.becSiteSeriesCd || '—'}</td>
                  <td>{r.seral || '—'}</td>
                  <td>{r.description || '—'}</td>
                  <td>
                    <Button kind="ghost" size="sm" onClick={() => applyBec(r)}>
                      Select
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <p style={{ marginTop: '1rem' }}>
            {becBusy ? 'Searching…' : 'Enter criteria and search (all fields optional).'}
          </p>
        )}
      </Modal>
    </div>
  );
};

export default BioStratumView;

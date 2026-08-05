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

import type { BecRow, CodeOption } from '@/types/configuration';
import type { BioStratum, BioStratumRow, StratumComputed } from '@/types/protocolChecklist';

import { useConfirm } from '@/context/confirm/useConfirm';
import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';
import { apiErrorMessage } from '@/utils/apiError';
import { formatShortDate } from '@/utils/date';
import { byteLength, overLimitError } from '@/utils/textLimits';

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
// Full groups the legacy enable/disable cascade clears when a group is turned off.
/**
 * Byte limits for the stratum's free-text fields, keyed by field — the same numbers
 * checkStratumLengths enforces, shared with the counter so display and validation can't drift
 * apart. Bytes, not characters: the columns are byte-semantic (`BIODIVERSITY_STRATUM.
 * OTHER_CONSTRAINT VARCHAR2(50 BYTE)` and friends, nr-mof-db V2.00406__BIODIVERSITY_STRATUM.sql).
 */
const STRATUM_TEXT_LIMITS: Record<string, number> = {
  otherConstraint: 50,
  otherEcoAnchorDesc: 30,
  patchGeneralComment: 2000,
};

/**
 * Free-text fields rendered as a multi-line box rather than a single-line input. Only the general
 * comment earns it — the other two limited fields are short labels ("Other constraint" at 50,
 * "Other eco anchor description" at 30) that read fine on one line.
 */
const MULTILINE_KEYS = new Set(['patchGeneralComment']);

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
) => {
  if (e[k] || !v(k)) return;
  const ok = integer ? isIntInRange(v(k), min, max) : isNumInRange(v(k), min, max);
  if (!ok) {
    const kind = integer ? 'a whole number' : 'a number';
    e[k] = `${LABELS[k] ?? k} must be ${kind} from ${min} to ${max}.`;
  }
};

const checkRequiredAndFormat = (e: StratumErrors, v: ValueReader) => {
  REQUIRED_KEYS.forEach((k) => {
    if (!v(k)) e[k] = `${LABELS[k] ?? k} is required.`;
  });
  if (!e.stratumNumber && !stratumNumberValid(v('stratumNumber'))) {
    e.stratumNumber =
      'Stratum Id must start with a letter, in letters-then-digits order — ' +
      'up to 3 letters then 2 digits, max 5 characters, no spaces (e.g. AB12).';
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

const checkNumericRanges = (e: StratumErrors, v: ValueReader) => {
  checkRange(e, v, 'plotCount', 0, 99, true);
  checkRange(e, v, 'size', 0.01, 9999.99, false);
  checkRange(e, v, 'estimatedSize', 0.01, 9999.99, false);
  checkRange(e, v, 'patchEstimatedOldestTreeAge', 0, 999, true);
  checkRange(e, v, 'patchWindthrowPct', 0, 100, false);
  CONSTRAINT_PCT_KEYS.forEach((k) => checkRange(e, v, k, 1, 100, true));
  ECO_COUNT_KEYS.forEach((k) => checkRange(e, v, k, 1, 999, true));
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

const checkConstrainedTotal = (e: StratumErrors, v: ValueReader) => {
  // Total constrained: 0–100, ≥ largest single %, and ≥1 constraint if total > 0.
  const totalStr = v('constrainedTotal');
  if (!totalStr || e.constrainedTotal) return;
  if (!isIntInRange(totalStr, 0, 100)) {
    e.constrainedTotal = 'Total constrained % must be a whole number from 0 to 100.';
    return;
  }
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

const BioStratumView: FC<Props> = ({ checklistId, canEdit, submitted }) => {
  const { display } = useNotification();
  const confirm = useConfirm();
  const [rows, setRows] = useState<BioStratumRow[]>([]);
  const [current, setCurrent] = useState<BioStratum | null>(null);
  const [computed, setComputed] = useState<StratumComputed | null>(null);
  const [strataTypes, setStrataTypes] = useState<CodeOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  // BEC search modal.
  const [becOpen, setBecOpen] = useState(false);
  const [becCriteria, setBecCriteria] = useState<Record<string, string>>({});
  const [becResults, setBecResults] = useState<BecRow[]>([]);
  const [becBusy, setBecBusy] = useState(false);

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
    } catch (err) {
      reportError('Could not load the stratum', err);
    } finally {
      setBusy(false);
    }
  };

  const addStratum = async () => {
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
  const applyBec = (r: BecRow) => {
    setCurrent((prev) =>
      prev
        ? {
            ...prev,
            bgcZoneCode: r.bgcZoneCode ?? '',
            bgcSubzoneCode: r.bgcSubzoneCode ?? '',
            bgcVariant: r.bgcVariant ?? '',
            bgcPhase: r.bgcPhase ?? '',
            becSiteSeriesCd: r.becSiteSeriesCd ?? '',
            siteSeriesPhaseCd: r.siteSeriesPhaseCd ?? '',
            seral: r.seral ?? '',
          }
        : prev,
    );
    setBecOpen(false);
  };

  // Mirror Frep211ValidationManager + the proc's validate(). Returns a field-keyed map (one message
  // per field) so each error renders inline on its own input; the first rule to flag a field wins.
  // Orchestrates the rule groups (each a small module-level function); the first rule to flag a
  // field wins. Mirrors Frep211ValidationManager + the proc's validate().
  const validate = (): Record<string, string> => {
    const e: StratumErrors = {};
    const v: ValueReader = (k) => get(k).trim();
    checkRequiredAndFormat(e, v);
    checkSizeConsistency(e, v);
    checkNumericRanges(e, v);
    checkDecimalsAndLengths(e, v);
    checkConstrainedTotal(e, v);
    checkCrossField(e, v, treatmentChecked);
    return e;
  };

  // Inline validation runs live off the edited stratum (like SiteDetail): a field's error shows the
  // moment it's invalid and clears when fixed. The Save handler blocks while any remain — no toast.
  const fieldErrors: Record<string, string> = current && !readOnly ? validate() : {};
  const hasErrors = Object.keys(fieldErrors).length > 0;

  const handleSave = async () => {
    if (!current) return;
    // Errors are already shown inline; just block the save while any remain (no error toast).
    if (hasErrors) return;
    setBusy(true);
    try {
      await API.protocolChecklist.saveBioStratum(checklistId, current);
      setCurrent(null);
      setComputed(null);
      await loadList();
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
        title: 'Delete stratum?',
        message: `Delete stratum ${row.stratumNumber || row.stratumId}? This can't be undone.`,
      }))
    )
      return;
    setBusy(true);
    try {
      await API.protocolChecklist.deleteBioStratum(row.stratumId, row.revisionCount ?? '');
      await loadList();
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

  const field = (key: string, label: string): ReactNode => {
    const lbl = requiredLabel(label, REQUIRED_KEYS.has(key));
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
          key={key}
          id={`stratum-${key}`}
          labelText={lbl}
          value={get(key)}
          disabled={disabled}
          invalid={Boolean(fieldErrors[key])}
          invalidText={fieldErrors[key]}
          onChange={(e) => onChange(e.target.value)}
        >
          <SelectItem value="" text="—" />
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
      labelText: lbl,
      value: get(key),
      disabled,
      invalid: Boolean(fieldErrors[key]),
      invalidText: fieldErrors[key],
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
        id={`stratum-${key}`}
        labelText={LABELS[key] ?? key}
        hideLabel
        size="sm"
        value={get(key)}
        disabled={disabledKey(key)}
        invalid={Boolean(fieldErrors[key])}
        invalidText={fieldErrors[key]}
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
                  disabled={busy}
                  onClick={() => void addStratum()}
                >
                  <Add size={16} className="bio-strata__add-icon" />
                  Add stratum
                </Button>
              </div>
            )}
            {rows.length === 0 ? (
              <p>No strata yet.</p>
            ) : (
              <Table size="sm" className="bio-strata__table">
                <TableHead>
                  <TableRow>
                    <TableHeader>Stratum number</TableHeader>
                    <TableHeader>Stratum type</TableHeader>
                    <TableHeader>Actions</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow key={row.stratumId}>
                      <TableCell>{row.stratumNumber || row.stratumId}</TableCell>
                      <TableCell>{strataTypeLabel(row.strataTypeCode)}</TableCell>
                      <TableCell>
                        <Button
                          kind="ghost"
                          size="sm"
                          renderIcon={Edit}
                          iconDescription="Edit"
                          hasIconOnly
                          disabled={busy}
                          onClick={() => void select(row.stratumId ?? '')}
                        />
                        {!readOnly && (
                          <Button
                            kind="danger--ghost"
                            size="sm"
                            renderIcon={TrashCan}
                            iconDescription="Delete"
                            hasIconOnly
                            disabled={busy}
                            onClick={() => void deleteRow(row)}
                          />
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

              {/* Stratum Summary (legacy frep211StratumSummary.jsp top block) */}
              <fieldset className="rip-form__group">
                <legend>Stratum summary</legend>
                <div className="rip-form__grid">
                  {field('stratumNumber', 'Stratum Id')}
                  {field('strataTypeCode', 'Stratum type')}
                  {roCell('NAR', computed?.nar ?? '')}
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
                  <Button kind="ghost" size="sm" onClick={() => setBecOpen(true)}>
                    Search BEC catalogue…
                  </Button>
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
                <div className="rip-form__grid">{field('harvestAreaCode', 'Tick one of')}</div>
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
                  <div className="rip-form__grid">
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
                  </div>
                )}
                <div className="rip-form__grid">{field('otherWindthrowTreatment', 'Other')}</div>
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
                            id="stratum-otherConstraint"
                            labelText="Other constraint"
                            hideLabel
                            size="sm"
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
                            id="stratum-otherEcoAnchorDesc"
                            labelText="Other eco anchor"
                            hideLabel
                            size="sm"
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
              key={c.key}
              id={`bec-${c.key}`}
              labelText={c.label}
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

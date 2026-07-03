import type { BioPlot } from '@/types/protocolChecklist';

// Mirrors the legacy FREP212 Frep212ValidationManager save chains (plot header + stand-table and
// CWD rows) so a bad save is caught inline rather than only at the proc.

type Row = Record<string, string | undefined>;

const isBlank = (value?: string): boolean => value == null || value.trim() === '';
const decimals = (text: string): number => {
  const dot = text.indexOf('.');
  return dot < 0 ? 0 : text.length - dot - 1;
};

/** Whole number within [min, max]; rejects signs/decimals/letters. */
const intError = (value: string, label: string, min: number, max: number): string => {
  if (!/^-?\d+$/.test(value)) return `${label} must be a whole number.`;
  const n = Number(value);
  if (n < min || n > max) return `${label} must be a whole number from ${min} to ${max}.`;
  return '';
};

/** Non-negative decimal within [min, max] (min exclusive when `exclusiveMin`), ≤ maxDecimals. */
const floatError = (
  value: string,
  label: string,
  min: number,
  max: number,
  maxDecimals: number,
  exclusiveMin = false,
): string => {
  if (!/^(?:\d+(?:\.\d+)?|\.\d+)$/.test(value)) return `${label} must be a number.`;
  if (decimals(value) > maxDecimals) {
    return `${label} can have at most ${maxDecimals} decimal place${maxDecimals === 1 ? '' : 's'}.`;
  }
  const n = Number(value);
  if ((exclusiveMin ? n <= min : n < min) || n > max) {
    return `${label} must be between ${min} and ${max}.`;
  }
  return '';
};

/** Required decimal field (blank → required), otherwise the {@link floatError} rules. */
const requiredFloat = (
  value: string,
  label: string,
  min: number,
  max: number,
  maxDecimals: number,
  exclusiveMin = false,
): string =>
  isBlank(value)
    ? `${label} is required.`
    : floatError(value, label, min, max, maxDecimals, exclusiveMin);

const put = (errors: Record<string, string>, key: string, message: string): void => {
  if (message) errors[key] = message;
};

type Getter = (key: string) => string;

const utmErrors = (e: Record<string, string>, g: Getter, noUtm: boolean): void => {
  if (noUtm) return;
  if (isBlank(g('utmZone'))) e.utmZone = 'Zone is required.';
  if (isBlank(g('utmEasting'))) e.utmEasting = 'Easting is required.';
  else if (!/^\d{6}$/.test(g('utmEasting'))) e.utmEasting = 'Easting must be exactly 6 digits.';
  if (isBlank(g('utmNorthing'))) e.utmNorthing = 'Northing is required.';
  else if (!/^\d{7}$/.test(g('utmNorthing'))) e.utmNorthing = 'Northing must be exactly 7 digits.';
};

const bearingErrors = (e: Record<string, string>, g: Getter): void => {
  const leg = (key: string, label: string) => {
    if (isBlank(g(key))) put(e, key, `${label} is required.`);
    else put(e, key, intError(g(key), label, 0, 359));
  };
  leg('firstLegTransect', 'Bearing 1st leg');
  leg('secondLegTransect', '2nd leg');
};

const plotNumberErrors = (e: Record<string, string>, g: Getter): void => {
  if (isBlank(g('assessorName'))) e.assessorName = 'Evaluated by is required.';
  // FREP_212_BIOPLOT.save_plot rejects a blank plot number (sil.error.usr.isrequired:Plot Number),
  // so enforce it here too; otherwise validate the range when present.
  if (isBlank(g('plotNumber'))) {
    e.plotNumber = 'Plot # is required.';
  } else {
    put(e, 'plotNumber', intError(g('plotNumber'), 'Plot #', 0, 999));
  }
  if (g('plotComment').length > 2000) e.plotComment = 'Comments must be 2000 characters or fewer.';
  if (!isBlank(g('basalAreaFactor'))) {
    put(e, 'basalAreaFactor', intError(g('basalAreaFactor'), 'BAF', 1, 99));
  }
  if (!isBlank(g('fixedAreaRadius'))) {
    put(
      e,
      'fixedAreaRadius',
      floatError(g('fixedAreaRadius'), 'Fixed area radius', 0.01, 999.99, 2),
    );
  }
  if (!isBlank(g('fullCountArea'))) {
    put(e, 'fullCountArea', floatError(g('fullCountArea'), 'Full count area', 0.01, 9999.99, 2));
  }
};

const measurementMethodErrors = (
  e: Record<string, string>,
  g: Getter,
  stratumType: string,
): void => {
  // Measurement method: clear-cut → fixed-area radius only; otherwise exactly one of the three.
  const baf = !isBlank(g('basalAreaFactor'));
  const fixed = !isBlank(g('fixedAreaRadius'));
  const full = !isBlank(g('fullCountArea'));
  if (stratumType === 'CC') {
    if (!fixed && !e.fixedAreaRadius) {
      e.fixedAreaRadius = 'Fixed area radius is required for a clear-cut plot.';
    }
    if (baf && !e.basalAreaFactor) e.basalAreaFactor = 'BAF must be blank for a clear-cut plot.';
    if (full && !e.fullCountArea) {
      e.fullCountArea = 'Full count area must be blank for a clear-cut plot.';
    }
  } else if ([baf, fixed, full].filter(Boolean).length !== 1 && !e.basalAreaFactor) {
    e.basalAreaFactor = 'Enter exactly one of BAF, fixed area radius, or full count area.';
  }
};

/**
 * "Trees exist" is not allowed on a clear-cut (CC) stratum — EXCEPT the NAR stratum, which may record
 * retained/residual trees on a clear cut. Mirrors the backend FREP_BIODIVERSITY_STRATUM.VALIDATE rule
 * (strata_type = 'CC' AND UPPER(TRIM(stratum_number)) <> 'NAR') so the stratum-save block is caught
 * inline here instead. A blank stratum type (summary not filled in) is not 'CC', so this relaxes
 * automatically. Keyed on stratum_number (the UI "Stratum ID"), never the numeric stratum_id PK.
 */
const treesExistErrors = (
  e: Record<string, string>,
  g: Getter,
  stratumType: string,
  stratumNumber: string,
): void => {
  const isNar = stratumNumber.trim().toUpperCase() === 'NAR';
  if (stratumType === 'CC' && !isNar && g('treeIndicator') === 'Y') {
    e.treeIndicator =
      "Trees exist isn't allowed on a clear-cut stratum (except NAR). Uncheck it to save.";
  }
};

/**
 * Plot-header field errors keyed by field key. Legacy parity: UTM (conditional on the "no signal"
 * toggle), bearings (required + 0–359), Evaluated by required, Plot # / BAF / fixed-area / full-count
 * numeric ranges and decimals, comment length, the "exactly one measurement method" rule
 * (clear-cut → fixed-area radius only), and the CC/NAR "trees exist" gate. Split into rule groups to
 * keep each simple. `stratumNumber` (the UI "Stratum ID") is optional — blank relaxes the CC gate.
 */
export const plotHeaderErrors = (
  plot: BioPlot,
  stratumType: string,
  stratumNumber = '',
): Record<string, string> => {
  const e: Record<string, string> = {};
  // Read a plot field as a trimmed string; non-string values (e.g. the table arrays) read as ''.
  const g: Getter = (k) => {
    const raw = (plot as Record<string, unknown>)[k];
    return typeof raw === 'string' ? raw.trim() : '';
  };
  utmErrors(e, g, g('utmSignal') === 'N');
  bearingErrors(e, g);
  plotNumberErrors(e, g);
  measurementMethodErrors(e, g, stratumType);
  treesExistErrors(e, g, stratumType, stratumNumber);
  return e;
};

/** Stand-table (tree) row errors keyed by column key: species + WT class required; DBH/height
 * required, ≤1 decimal, within range. */
export const standRowErrors = (row: Row): Record<string, string> => {
  const e: Record<string, string> = {};
  const g = (k: string) => String(row[k] ?? '').trim();
  if (isBlank(g('speciesCode'))) e.speciesCode = 'Species is required.';
  if (isBlank(g('decayClassCode'))) e.decayClassCode = 'WT class is required.';
  put(e, 'dbh', requiredFloat(g('dbh'), 'DBH', 12.6, 400, 1));
  put(e, 'height', requiredFloat(g('height'), 'Height', 1.4, 99.9, 1));
  return e;
};

/** CWD row errors keyed by column key: species + decay class required; diameter/length required,
 * ≤1 decimal, within range (length must be greater than 0). */
export const cwdRowErrors = (row: Row): Record<string, string> => {
  const e: Record<string, string> = {};
  const g = (k: string) => String(row[k] ?? '').trim();
  if (isBlank(g('speciesCode'))) e.speciesCode = 'Species is required.';
  if (isBlank(g('decayClassCode'))) e.decayClassCode = 'Decay class is required.';
  put(e, 'logDiameter', requiredFloat(g('logDiameter'), 'Diameter', 7.6, 400, 1));
  put(e, 'logLength', requiredFloat(g('logLength'), 'Length', 0, 99.9, 1, true));
  return e;
};

import type { BioPlot } from '@/types/protocolChecklist';

import { byteLength, overLimitError } from '@/utils/textLimits';

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
    return exclusiveMin
      ? `${label} must be greater than ${min} and no more than ${max}.`
      : `${label} must be between ${min} and ${max}.`;
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

/**
 * UTM coordinates, owed only when the plot affirmatively records a signal.
 *
 * `signalled` is the plot saying "yes, there was a signal" — `utm_signal = 'Y'`, which is the
 * "No UTM signal available" box left unchecked. A blank signal is *not* treated as a yes: the column
 * is nullable and rows predating this app never answered the question, so reading silence as "there
 * was a signal" demanded coordinates from records nothing else has ever asked for. Neither the
 * column (`UTM_ZONE`/`UTM_EASTING`/`UTM_NORTHING` are all nullable) nor
 * `FREP_BIODIVERSITY_PLOT.VALIDATE` requires them, and legacy tested the same `'Y'` — see
 * Frep212ValidationManager's UtmSignalCompleteValidator.
 *
 * The checklist-wide rule is separate and unaffected: `FREP_TOMBSTONE` still refuses a submit unless
 * at least one plot really holds all three (`frep.submit.biodiversity.plot.utmrequired`).
 */
const utmErrors = (e: Record<string, string>, g: Getter, signalled: boolean): void => {
  // Owed only on an affirmative signal.
  if (signalled) {
    if (isBlank(g('utmZone'))) e.utmZone = 'Zone is required.';
    if (isBlank(g('utmEasting'))) e.utmEasting = 'Easting is required.';
    if (isBlank(g('utmNorthing'))) e.utmNorthing = 'Northing is required.';
  }
  // Shape is checked whatever the signal says: a value the evaluator actually typed is wrong
  // regardless of whether it was owed, and legacy registered its Easting/Northing field validators
  // unconditionally for the same reason. This is the file's standing rule — only a *blank* field is
  // ever exempted (see ADVISORY_WHEN_BLANK).
  if (!isBlank(g('utmEasting')) && !/^\d{6}$/.test(g('utmEasting'))) {
    e.utmEasting = 'Easting must be exactly 6 digits.';
  }
  if (!isBlank(g('utmNorthing')) && !/^\d{7}$/.test(g('utmNorthing'))) {
    e.utmNorthing = 'Northing must be exactly 7 digits.';
  }
};

const bearingErrors = (e: Record<string, string>, g: Getter): void => {
  const leg = (key: string, label: string) => {
    if (isBlank(g(key))) put(e, key, `${label} is required.`);
    else put(e, key, intError(g(key), label, 0, 359));
  };
  leg('firstLegTransect', 'Bearing 1st leg');
  leg('secondLegTransect', '2nd leg');
};

/**
 * Plot numbers already used by *other* plots in the same stratum.
 *
 * Compared numerically rather than as text: `BIODIVERSITY_PLOT.PLOT_NUMBER` is `NUMBER(3)`
 * (nr-mof-db V2.00403__BIODIVERSITY_PLOT.sql), so "01" and "1" are the same plot number to Oracle.
 * A string comparison here would let a duplicate through to
 * `FREP_BIODIVERSITY_PLOT.VALIDATE`, which rejects the save with
 * `frep.web.usr.database.record.plot.number.already.exists`.
 */
const isTaken = (value: string, taken: readonly string[]): boolean => {
  const wanted = Number(value);
  return (
    Number.isFinite(wanted) &&
    taken.some((other) => other.trim() !== '' && Number(other) === wanted)
  );
};

const plotNumberErrors = (
  e: Record<string, string>,
  g: Getter,
  takenPlotNumbers: readonly string[],
): void => {
  // Matches the Opening tab's Evaluator error: name the remedy, since this field is read-only and
  // "Evaluated by is required" gives no hint that "Assign it to me" is how you fill it.
  if (isBlank(g('assessorName'))) {
    e.assessorName = 'An evaluator is required — use "Assign it to me".';
  }
  // FREP_212_BIOPLOT.save_plot rejects a blank plot number (sil.error.usr.isrequired:Plot Number),
  // so enforce it here too; otherwise validate the range when present.
  if (isBlank(g('plotNumber'))) {
    e.plotNumber = 'Plot # is required.';
  } else {
    put(e, 'plotNumber', intError(g('plotNumber'), 'Plot #', 0, 999));
    // Caught here rather than left to the proc: the number is unique per stratum, and finding that
    // out from a failed save costs the evaluator the round-trip. Only when the number is otherwise
    // valid — "Plot # must be a whole number" is the more useful of the two messages.
    if (!e.plotNumber && isTaken(g('plotNumber'), takenPlotNumbers)) {
      e.plotNumber = `Plot ${g('plotNumber')} already exists in this stratum. Use a different number.`;
    }
  }
  if (byteLength(g('plotComment')) > PLOT_TEXT_LIMITS.plotComment) {
    e.plotComment = `Comments — ${overLimitError(g('plotComment'), PLOT_TEXT_LIMITS.plotComment)}`;
  }
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
 * Plot-header field errors keyed by field key. Legacy parity: UTM (only when the plot records a
 * signal — see {@link utmErrors}), bearings (required + 0–359), Evaluated by required, Plot # / BAF / fixed-area / full-count
 * numeric ranges and decimals, comment length, and the "exactly one measurement method" rule
 * (clear-cut → fixed-area radius only). Split into rule groups to keep each simple.
 *
 * Note: "Trees exist" carries no stratum-type restriction — it's allowed on every stratum type,
 * including clear-cut (CC). (Earlier the app mirrored the legacy CC-except-NAR block; per requirement
 * that gate was removed here and in FREP_BIODIVERSITY_STRATUM.VALIDATE.)
 */
export const plotHeaderErrors = (
  plot: BioPlot,
  stratumType: string,
  /** Plot numbers held by the other plots in this stratum — see {@link isTaken}. */
  takenPlotNumbers: readonly string[] = [],
): Record<string, string> => {
  const e: Record<string, string> = {};
  // Read a plot field as a trimmed string; non-string values (e.g. the table arrays) read as ''.
  const g: Getter = (k) => {
    const raw = (plot as Record<string, unknown>)[k];
    return typeof raw === 'string' ? raw.trim() : '';
  };
  utmErrors(e, g, g('utmSignal') === 'Y');
  bearingErrors(e, g);
  plotNumberErrors(e, g, takenPlotNumbers);
  measurementMethodErrors(e, g, stratumType);
  return e;
};

/**
 * Byte limit for the plot's free-text comment, keyed by field — the same number the rule above
 * enforces, exported to the counter so display and validation can't drift apart. Bytes, not
 * characters: `BIODIVERSITY_PLOT.PLOT_COMMENT` is `VARCHAR2(2000 BYTE)` (nr-mof-db
 * V2.00403__BIODIVERSITY_PLOT.sql).
 */
export const PLOT_TEXT_LIMITS: Record<string, number> = {
  plotComment: 2000,
};

/** Stand-table (tree) row errors keyed by column key: species + WT class required; DBH/height
 * required, ≤1 decimal, within range. */
export const standRowErrors = (row: Row): Record<string, string> => {
  const e: Record<string, string> = {};
  const g = (k: string) => String(row[k] ?? '').trim();
  if (isBlank(g('speciesCode'))) e.speciesCode = 'Species is required.';
  if (isBlank(g('decayClassCode'))) e.decayClassCode = 'WT class is required.';
  // DBH rule is "> 12.5 cm" (legacy help); with 1-decimal precision the smallest valid value is 12.6.
  // Exclusive-min 12.5 enforces exactly that while showing 12.5 (the threshold) in the message.
  put(e, 'dbh', requiredFloat(g('dbh'), 'DBH', 12.5, 400, 1, true));
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

/**
 * Fields whose "not filled in yet" error is advisory: nullable columns the tab still marks required
 * and still counts against submit, but that no longer stop a plot being stored. A value that *is*
 * entered must still be the right shape, so only a blank field is exempted.
 */
const ADVISORY_WHEN_BLANK = [
  'utmZone',
  'utmEasting',
  'utmNorthing',
  'firstLegTransect',
  'secondLegTransect',
  'basalAreaFactor',
  'fixedAreaRadius',
  'fullCountArea',
];

/**
 * The subset of {@link plotErrors} that must still stop the save.
 *
 * A plot recorded before the GPS gets a fix, before the transect is walked or before it is measured
 * is a legitimate thing to store — those gaps are reported on the tab and block submit instead.
 * What remains blocking: `ASSESSOR_NAME` and `PLOT_NUMBER` (NOT NULL on BIODIVERSITY_PLOT), any
 * malformed value, and naming two measurement methods at once, which is a contradiction rather than
 * a gap.
 */
export const plotBlockingErrors = (
  plot: BioPlot,
  errors: Record<string, string>,
): Record<string, string> => {
  const blocking = { ...errors };
  ADVISORY_WHEN_BLANK.forEach((key) => {
    if (isBlank((plot as Record<string, string | undefined>)[key])) delete blocking[key];
  });

  const methods = ['basalAreaFactor', 'fixedAreaRadius', 'fullCountArea'].filter(
    (key) => !isBlank((plot as Record<string, string | undefined>)[key]),
  ).length;
  if (methods > 1) {
    // Re-stated because the original message can sit on a field that is itself blank.
    blocking.basalAreaFactor = 'Enter only one of BAF, fixed area radius, or full count area.';
  }
  return blocking;
};

import type { Feature } from '@/types/chrChecklist';

import { FEATURE_TEXT_LIMITS } from '@/pages/ChrChecklist/textLimits';
import { addTextLimitErrors } from '@/utils/textLimits';

// Lightweight, high-value feature validation mirroring the most important CHR submit checks, for live
// inline feedback. The full rule set (at-least-one groups, FN/AIA/SP planning, windthrow/damage
// details, composite membership, etc.) lives in `tabStatus.ts`, which drives the tab dots and the
// submit pre-flight, and stays authoritative server-side at submit.
//
// The errors are split by what each rule can do. A required field left blank is advisory: it is
// marked, counted on the Features tab and blocks submit, but the feature still saves — an evaluator
// in the field routinely has some answers and not others. Only a value the column cannot store (a
// malformed Borden #, a non-numeric count, free text past its byte limit) blocks the save.

/** Borden # format, shared with the submit-rule mirror in tabStatus.ts. */
export const BORDEN_RE = /^[A-U][a-l][A-W][a-x]-\d{1,4}$/;
const isYes = (v?: string) => v === 'true';
const isBlank = (v?: string) => v == null || `${v}`.trim() === '';

/** A whole number within [0, max]; returns an error or '' (blank is handled by the caller). */
const intRange = (value: string | undefined, label: string, max: number): string => {
  const v = (value ?? '').trim();
  if (!v) return '';
  if (!/^\d+$/.test(v)) return `${label} must be a whole number.`;
  return Number(v) > max ? `${label} must be from 0 to ${max}.` : '';
};

/** A numeric field that is required only when its gating checkbox is Yes. Blank only — advisory. */
const gatedNumberMissing = (
  e: Record<string, string>,
  on: boolean,
  key: string,
  value: string | undefined,
  label: string,
) => {
  if (on && isBlank(value)) e[key] = `${label} is required.`;
};

/** The shape rule for the same field: an entered value must be a whole number in range. Blocking. */
const gatedNumberFormat = (
  e: Record<string, string>,
  key: string,
  value: string | undefined,
  label: string,
  max: number,
) => {
  const err = intRange(value, label, max);
  if (err) e[key] = err;
};

/**
 * Required feature fields that are still blank, keyed by `Feature` field.
 *
 * Advisory: reported and counted, but they do not stop the feature being stored. See
 * {@link featureBlockingErrors} for the rules that do.
 */
export const featureRequiredErrors = (f: Feature): Record<string, string> => {
  const e: Record<string, string> = {};

  // Summary — rating required; description required when its question is "Yes".
  if (isBlank(f.featureRating)) e.featureRating = 'A rating is required.';
  if (
    isYes(f.q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature) &&
    isBlank(f.q4Description)
  ) {
    e.q4Description = 'A description is required.';
  }
  if (
    isYes(
      f.q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective,
    ) &&
    isBlank(f.q5Description)
  ) {
    e.q5Description = 'A description is required.';
  }
  if (
    isYes(
      f.q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature,
    ) &&
    isBlank(f.q6Description)
  ) {
    e.q6Description = 'A description is required.';
  }

  // Description — CMT / Monumental cedar counts, "Other" description.
  gatedNumberMissing(e, isYes(f.ofCMTs), 'ofCMTsNumber', f.ofCMTsNumber, '# of CMTs');
  gatedNumberMissing(
    e,
    isYes(f.ofMonumentalCedars),
    'standofMonumentalCedar',
    f.standofMonumentalCedar,
    '# of Monumental Cedars',
  );
  if (isYes(f.other) && isBlank(f.otherdescription)) {
    e.otherdescription = 'A description is required.';
  }

  // Location — "Other" description + reserve type.
  if (isYes(f.locationOther) && isBlank(f.locationOtherDescription)) {
    e.locationOtherDescription = 'A description is required.';
  }
  if (isYes(f.inReserve) && isBlank(f.locationReservetype)) {
    e.locationReservetype = 'Select a reserve type.';
  }

  // Effectiveness — retained-buffer width.
  gatedNumberMissing(
    e,
    isYes(f.retainabuffer),
    'bufferWidthMeter',
    f.bufferWidthMeter,
    'Buffer size (m)',
  );

  return e;
};

/**
 * Feature errors the stored row could not survive: a Borden # in the wrong format, a count that is
 * not a whole number or is out of range, or free text past its byte-semantic column limit. These
 * <b>do</b> block the save.
 */
export const featureBlockingErrors = (f: Feature): Record<string, string> => {
  const e: Record<string, string> = {};

  if (
    isYes(f.chrRegisteredSite) &&
    (f.borden ?? '').length > 0 &&
    !BORDEN_RE.test(f.borden ?? '')
  ) {
    e.borden = 'Must match the Borden format, e.g. AaBb-0000.';
  }
  gatedNumberFormat(e, 'ofCMTsNumber', f.ofCMTsNumber, '# of CMTs', 999);
  gatedNumberFormat(
    e,
    'standofMonumentalCedar',
    f.standofMonumentalCedar,
    '# of Monumental Cedars',
    999,
  );
  gatedNumberFormat(e, 'bufferWidthMeter', f.bufferWidthMeter, 'Buffer size (m)', 9999);

  // Free-text length. Enforced here rather than left to the database: these columns are byte-limited
  // and nothing else checks them, so an over-long entry used to surface only as a failed save.
  addTextLimitErrors(e, f as Record<string, unknown>, FEATURE_TEXT_LIMITS);

  return e;
};

/**
 * Every field-level error on the feature editor — what to show the user, blocking or not. A blocking
 * error wins over a required one on the same field, since it names a value the user actually typed.
 */
export const featureErrors = (
  f: Feature,
  takenLabels: readonly string[] = [],
): Record<string, string> => ({
  ...featureRequiredErrors(f),
  ...featureBlockingErrors(f),
  ...duplicateLabelError(f, takenLabels),
});

/** True when the feature cannot be stored as it stands (used to block Save). */
export const featureHasErrors = (f: Feature, takenLabels: readonly string[] = []): boolean =>
  Object.keys(featureBlockingErrors(f)).length > 0 ||
  Object.keys(duplicateLabelError(f, takenLabels)).length > 0;

/**
 * A label already used by another feature on this checklist.
 *
 * `CHFID_UK` is `UNIQUE (CHR_CHECKLIST_ID, FEATURE_LABEL)`, so a repeat is refused at the database
 * and comes back as a failed save. Caught here instead, next to the field, because the whole list is
 * already in front of us — the round trip told the user only that something went wrong, and only
 * after they had filled the rest of the feature in.
 *
 * Compared case-insensitively, which is stricter than the constraint: Oracle would allow "A" and
 * "a" side by side, but composite membership matches labels case-insensitively (see
 * `matchesCompositeLabel`), so the pair would be indistinguishable to the thing that reads them.
 */
export const duplicateLabelError = (
  f: Feature,
  takenLabels: readonly string[],
): Record<string, string> => {
  const label = (f.featureLabel ?? '').trim().toLowerCase();
  if (label === '') return {};
  const clash = takenLabels.some((other) => (other ?? '').trim().toLowerCase() === label);
  return clash ? { featureLabel: 'Already used by another feature.' } : {};
};

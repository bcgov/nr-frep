import type { Feature } from '@/types/chrChecklist';

import { FEATURE_TEXT_LIMITS } from '@/pages/ChrChecklist/textLimits';
import { addTextLimitErrors } from '@/utils/textLimits';

// Lightweight, high-value feature validation mirroring the most important CHR submit checks, for live
// inline feedback. The full rule set (at-least-one groups, FN/AIA/SP planning, windthrow/damage
// details, composite membership, etc.) stays server-side at submit.

const BORDEN_RE = /^[A-U][a-l][A-W][a-x]-\d{1,4}$/;
const isYes = (v?: string) => v === 'true';
const isBlank = (v?: string) => v == null || `${v}`.trim() === '';

/** A whole number within [0, max]; returns an error or '' (blank is handled by the caller). */
const intRange = (value: string | undefined, label: string, max: number): string => {
  const v = (value ?? '').trim();
  if (!v) return '';
  if (!/^\d+$/.test(v)) return `${label} must be a whole number.`;
  return Number(v) > max ? `${label} must be from 0 to ${max}.` : '';
};

/** A numeric field that is required (and ranged) only when its gating checkbox is Yes. */
const gatedNumber = (
  e: Record<string, string>,
  on: boolean,
  key: string,
  value: string | undefined,
  label: string,
  max: number,
) => {
  if (!on) return;
  if (isBlank(value)) e[key] = `${label} is required.`;
  else {
    const err = intRange(value, label, max);
    if (err) e[key] = err;
  }
};

/** Field-level errors keyed by Feature field. An empty object means the feature passes these checks. */
export const featureErrors = (f: Feature): Record<string, string> => {
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

  // Description — Borden format, CMT / Monumental cedar counts, "Other" description.
  if (
    isYes(f.chrRegisteredSite) &&
    (f.borden ?? '').length > 0 &&
    !BORDEN_RE.test(f.borden ?? '')
  ) {
    e.borden = 'Must match the Borden format, e.g. AaBb-0000.';
  }
  gatedNumber(e, isYes(f.ofCMTs), 'ofCMTsNumber', f.ofCMTsNumber, '# of CMTs', 999);
  gatedNumber(
    e,
    isYes(f.ofMonumentalCedars),
    'standofMonumentalCedar',
    f.standofMonumentalCedar,
    '# of Monumental Cedars',
    999,
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
  gatedNumber(
    e,
    isYes(f.retainabuffer),
    'bufferWidthMeter',
    f.bufferWidthMeter,
    'Buffer size (m)',
    9999,
  );

  // Free-text length. Enforced here rather than left to the database: these columns are byte-limited
  // and nothing else checks them, so an over-long entry used to surface only as a failed save.
  addTextLimitErrors(e, f as Record<string, unknown>, FEATURE_TEXT_LIMITS);

  return e;
};

/** True when the feature has any of the lightweight errors (used to block Save). */
export const featureHasErrors = (f: Feature): boolean => Object.keys(featureErrors(f)).length > 0;

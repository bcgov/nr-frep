import type { BiodiversityOpening } from '@/types/protocolChecklist';

import { byteLength, overLimitError } from '@/utils/textLimits';

// Mirrors the legacy FREP210 Frep210ValidationManager "Save" chain so a bad save is caught inline
// (matching field-level messages) rather than only at the proc.

const LOCATION_MAX = 50;
const COMMENT_MAX = 4000;
const RATIONALE_MAX = 2000;

/**
 * Byte limits for the Opening's free-text fields, keyed by field — the same numbers the rules below
 * enforce, exported so the view can show a live counter that always agrees with them.
 *
 * <p>Bytes, not characters. The backing columns are byte-semantic —
 * `BIODIVERSITY_CHECKLIST.LOCATION_DESCRIPTION VARCHAR2(50 BYTE)` and friends (nr-mof-db
 * `scripts/THE/TABLES/V2.00399__BIODIVERSITY_CHECKLIST.sql`) — so a 50-*character* description
 * carrying a few curly quotes is over the column's 50 bytes and fails on save. The legacy
 * Frep210ValidationManager counted characters and the backend still does; measuring bytes here
 * makes the client the stricter of the two, which is the correct direction.
 */
export const OPENING_TEXT_LIMITS: Record<string, number> = {
  locationDescription: LOCATION_MAX,
  innovativePracticesComment: COMMENT_MAX,
  invasivePlantComment: COMMENT_MAX,
  evaluatorOpinionComment: RATIONALE_MAX,
};
const OVERRIDE_MIN = 0.01;
const OVERRIDE_MAX = 99999.99;

const isBlank = (value?: string): boolean => value == null || value.trim() === '';

/** Lowercases the first letter so `overLimitError` reads as a clause after the field name. */
const lower = (message: string): string =>
  message ? message.charAt(0).toLowerCase() + message.slice(1) : message;

const todayIso = (): string => new Date().toISOString().slice(0, 10);

/**
 * A comment that is required when its Yes/No indicator is 'Y', and is otherwise capped at
 * {@link COMMENT_MAX}. Returns the matching error message, or null when valid.
 */
const validateConditionalComment = (
  indicator: string | undefined,
  comment: string | undefined,
  requiredMessage: string,
  lengthMessage: string,
): string | null => {
  if (indicator === 'Y' && isBlank(comment)) return requiredMessage;
  if (byteLength(comment) > COMMENT_MAX) return lengthMessage;
  return null;
};

const validateOverride = (value: string | undefined): string | null => {
  if (isBlank(value)) return null;
  const text = value!.trim();
  if (!/^[-+]?(?:\d+(?:\.\d+)?|\.\d+)$/.test(text))
    return 'FREP gross area override must be a number.';
  const number = Number(text);
  if (number < OVERRIDE_MIN || number > OVERRIDE_MAX) {
    return `FREP gross area override must be between ${OVERRIDE_MIN} and ${OVERRIDE_MAX}.`;
  }
  const dot = text.indexOf('.');
  if (dot >= 0 && text.length - dot - 1 > 2) {
    return 'FREP gross area override can have at most 2 decimal places.';
  }
  return null;
};

/**
 * Field-level errors for the Biodiversity Opening, keyed by {@link BiodiversityOpening} key. An empty
 * object means the form is valid to save. Location, Evaluation date, Invasive plant?, Innovative
 * practice? and Rating are all required to save (Evaluation date moved here from the Administration
 * tab), plus the conditional comments and length/number limits.
 */
export const validateOpening = (data: BiodiversityOpening): Record<string, string> => {
  const errors: Record<string, string> = {};

  // Required: Location description (+ length).
  if (isBlank(data.locationDescription)) {
    errors.locationDescription = 'Location description is required.';
  } else if (byteLength(data.locationDescription) > LOCATION_MAX) {
    errors.locationDescription = `Location description ${lower(
      overLimitError(data.locationDescription, LOCATION_MAX),
    )}`;
  }

  // Required: Evaluation date (submit needs it; blocked here like the other required fields, matching
  // the CHR Opening tab). Must not be in the future.
  if (isBlank(data.evaluationDate)) {
    errors.evaluationDate = 'Evaluation date is required.';
  } else if (data.evaluationDate!.trim() > todayIso()) {
    errors.evaluationDate = 'Evaluation date cannot be in the future.';
  }

  // Required: Evaluator (submit needs a team lead; blocked here like CHR's Assessed by).
  if (isBlank(data.teamLeadNameId)) {
    errors.teamLeadNameId = 'An evaluator is required — use “Assign it to me”.';
  }

  // Required dropdowns.
  if (isBlank(data.invasivePlantIndicator)) {
    errors.invasivePlantIndicator = 'Select whether invasive plant species are present.';
  }
  if (isBlank(data.innovativePracticeInd)) {
    errors.innovativePracticeInd = 'Select whether innovative practices were used.';
  }
  if (isBlank(data.frepSiteEvaluationCode)) {
    errors.frepSiteEvaluationCode = 'A rating is required.';
  }

  // Innovative practices comment — required when innovative practices = Yes (+ length).
  const innovativeComment = validateConditionalComment(
    data.innovativePracticeInd,
    data.innovativePracticesComment,
    'Describe the innovative practice.',
    `Description ${lower(overLimitError(data.innovativePracticesComment, COMMENT_MAX))}`,
  );
  if (innovativeComment) errors.innovativePracticesComment = innovativeComment;

  // Invasive plant comment — required when invasive plants = Yes (+ length).
  const invasiveComment = validateConditionalComment(
    data.invasivePlantIndicator,
    data.invasivePlantComment,
    'Enter a comment about the invasive plants.',
    `Comments ${lower(overLimitError(data.invasivePlantComment, COMMENT_MAX))}`,
  );
  if (invasiveComment) errors.invasivePlantComment = invasiveComment;

  // Rationale length.
  if (byteLength(data.evaluatorOpinionComment) > RATIONALE_MAX) {
    errors.evaluatorOpinionComment = `Rationale ${lower(
      overLimitError(data.evaluatorOpinionComment, RATIONALE_MAX),
    )}`;
  }

  // FREP gross area override — float within 0.01–99999.99, two decimals.
  const override = validateOverride(data.frepWtpOverride);
  if (override) errors.frepWtpOverride = override;

  return errors;
};

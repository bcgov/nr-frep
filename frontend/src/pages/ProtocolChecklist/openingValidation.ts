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

/** A comment is required once its Yes/No indicator is set to 'Y'. */
const conditionalCommentMissing = (indicator?: string, comment?: string): boolean =>
  indicator === 'Y' && isBlank(comment);

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
 * The fields an evaluator can actually edit on the Opening tab.
 *
 * The read-only RESULTS reference values (gross area, net area, harvest date) are deliberately
 * excluded: they are populated for every record from `frep_selected_site`, so counting them would
 * make an untouched tab look started.
 */
const OPENING_EDITABLE_FIELDS: (keyof BiodiversityOpening)[] = [
  'locationDescription',
  'evaluationDate',
  'teamLeadNameId',
  'frepWtpOverride',
  'patchReservesOnBlock',
  'patchReservesSampled',
  'innovativePracticeInd',
  'innovativePracticesComment',
  'invasivePlantIndicator',
  'invasivePlantComment',
  'frepSiteEvaluationCode',
  'evaluatorOpinionComment',
];

/**
 * Whether anything has ever been stored on this tab.
 *
 * Read from the saved record rather than tracked in component state, so it survives a reload: a
 * checklist saved half-finished last week still counts as saved today. This is what gates the
 * outstanding-fields banner and the tab's red count — telling someone what they have not filled in
 * before they have opened the form once is nagging, not helping.
 */
export const openingTouched = (data?: BiodiversityOpening | null): boolean =>
  data != null && OPENING_EDITABLE_FIELDS.some((key) => !isBlank(data[key] as string | undefined));

/**
 * The Opening fields the user must eventually fill in, with the label the banner names them by, in
 * the order they appear down the tab.
 *
 * Three of them block submit in the database
 * ({@code FREP_TOMBSTONE.validate_biodiversity_chklst}: evaluation date, evaluation team lead and
 * location description); the rest come from the legacy FREP210 "Save" chain. Both sets are treated
 * the same here — marked with an asterisk, counted on the tab, and named after a save — because to
 * an evaluator they are all just answers still owed.
 */
export const OPENING_REQUIRED_LABELS: Record<string, string> = {
  evaluationDate: 'Evaluation date',
  teamLeadNameId: 'Evaluator',
  locationDescription: 'Location description',
  innovativePracticeInd: 'Innovative / unique forest practices used?',
  innovativePracticesComment: 'Please describe the innovative practice',
  frepSiteEvaluationCode: 'Rating',
  invasivePlantIndicator: 'Invasive plant species present?',
  invasivePlantComment: 'Comments on the invasive plants',
};

/**
 * Required fields that are still blank, keyed by {@link BiodiversityOpening} key.
 *
 * These do <b>not</b> block the save. A part-finished Opening is a legitimate thing to store — an
 * evaluator in the field has the answers they have — so these are reported (asterisk, tab count,
 * post-save banner) and left for submit to enforce. See {@link openingFormatErrors} for the rules
 * that do block.
 */
export const openingRequiredErrors = (data: BiodiversityOpening): Record<string, string> => {
  const errors: Record<string, string> = {};

  if (isBlank(data.locationDescription)) {
    errors.locationDescription = 'Location description is required.';
  }
  if (isBlank(data.evaluationDate)) {
    errors.evaluationDate = 'Evaluation date is required.';
  }
  if (isBlank(data.teamLeadNameId)) {
    errors.teamLeadNameId = 'An evaluator is required — use “Assign it to me”.';
  }
  if (isBlank(data.invasivePlantIndicator)) {
    errors.invasivePlantIndicator = 'Select whether invasive plant species are present.';
  }
  if (isBlank(data.innovativePracticeInd)) {
    errors.innovativePracticeInd = 'Select whether innovative practices were used.';
  }
  if (isBlank(data.frepSiteEvaluationCode)) {
    errors.frepSiteEvaluationCode = 'A rating is required.';
  }
  if (conditionalCommentMissing(data.innovativePracticeInd, data.innovativePracticesComment)) {
    errors.innovativePracticesComment = 'Describe the innovative practice.';
  }
  if (conditionalCommentMissing(data.invasivePlantIndicator, data.invasivePlantComment)) {
    errors.invasivePlantComment = 'Enter a comment about the invasive plants.';
  }

  return errors;
};

/**
 * Errors the stored row could not survive: values too long for their byte-semantic column, a
 * future evaluation date, or an override that is not a number in range. These <b>do</b> block the
 * save — the backend rejects them with a 400 and the database would raise ORA-12899 — so the user
 * has to fix them before anything is written.
 */
export const openingFormatErrors = (data: BiodiversityOpening): Record<string, string> => {
  const errors: Record<string, string> = {};

  if (!isBlank(data.locationDescription) && byteLength(data.locationDescription) > LOCATION_MAX) {
    errors.locationDescription = `Location description ${lower(
      overLimitError(data.locationDescription, LOCATION_MAX),
    )}`;
  }
  if (!isBlank(data.evaluationDate) && data.evaluationDate!.trim() > todayIso()) {
    errors.evaluationDate = 'Evaluation date cannot be in the future.';
  }
  if (byteLength(data.innovativePracticesComment) > COMMENT_MAX) {
    errors.innovativePracticesComment = `Description ${lower(
      overLimitError(data.innovativePracticesComment, COMMENT_MAX),
    )}`;
  }
  if (byteLength(data.invasivePlantComment) > COMMENT_MAX) {
    errors.invasivePlantComment = `Comments ${lower(
      overLimitError(data.invasivePlantComment, COMMENT_MAX),
    )}`;
  }
  if (byteLength(data.evaluatorOpinionComment) > RATIONALE_MAX) {
    errors.evaluatorOpinionComment = `Rationale ${lower(
      overLimitError(data.evaluatorOpinionComment, RATIONALE_MAX),
    )}`;
  }
  const override = validateOverride(data.frepWtpOverride);
  if (override) errors.frepWtpOverride = override;

  return errors;
};

/**
 * The one edit the write path cannot express: removing an evaluation date that is already stored.
 *
 * {@code FREP_210_BIO_OPENING.SAVE} takes the date as an optional trailing parameter and applies it
 * only when it is not null — and Oracle binds an empty string as null, so "cleared" and "not
 * supplied" reach the proc identically and the stored date survives the save. The tab then re-reads
 * the record and the old date reappears in the field, looking like the form refused to clear.
 *
 * Rather than accept an edit that silently will not take, it is refused here with the reason. This
 * is deliberately the *narrowest* rule that closes the gap: it fires only when a stored date is
 * being removed. A checklist that never had one still saves blank — nothing is being removed, and
 * the field stays advisory like every other required field on the tab.
 *
 * The alternative is a database change (letting the proc distinguish "clear" from "not supplied"),
 * which this rule exists to avoid.
 */
export const evaluationDateRemovalError = (
  stored: BiodiversityOpening | null | undefined,
  data: BiodiversityOpening,
): Record<string, string> =>
  !isBlank(stored?.evaluationDate) && isBlank(data.evaluationDate)
    ? {
        evaluationDate:
          'Evaluation date can’t be removed once saved — enter a different date instead.',
      }
    : {};

/**
 * Every field-level error on the Opening tab — what to show the user, blocking or not. A format
 * error wins over a required one on the same field, since it names a value the user actually typed.
 */
export const validateOpening = (data: BiodiversityOpening): Record<string, string> => ({
  ...openingRequiredErrors(data),
  ...openingFormatErrors(data),
});

import type { CheckList } from '@/types/chrChecklist';

import { BLOCK_TEXT_LIMITS, OPENING_TEXT_LIMITS } from '@/pages/ChrChecklist/textLimits';
import { addTextLimitErrors } from '@/utils/textLimits';

/**
 * Checklist-level validation for the Opening info and Block summary tabs, split by what each rule
 * can actually do.
 *
 * The split is the point of this file. A required field left blank is <b>reported</b> — asterisk,
 * tab count, post-save banner — and left for submit to enforce; only a value the column cannot store
 * <b>blocks</b> the save. An evaluator working a block in the field routinely has some answers and
 * not others, and refusing the save cost them the answers they did have.
 *
 * The required set mirrors {@code ChrSubmitValidationService.validateChecklistLevel} — the service
 * that actually blocks submit — so a tab only reads as complete when it would not contribute a
 * submit error.
 */

const isBlank = (value?: string): boolean => value == null || `${value}`.trim() === '';

const isYes = (value?: string): boolean => `${value ?? ''}`.trim().toLowerCase() === 'true';

/** The Opening info fields an evaluator can edit — what "has this tab been started?" reads. */
const OPENING_EDITABLE_FIELDS = [
  'evaluationDate',
  'assessedBy',
  'firstNationName',
  'generalLocation',
  'targeted',
] as const;

/** The Block summary fields an evaluator can edit. */
const BLOCK_EDITABLE_FIELDS = [
  'q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock',
  'q8Comments',
  'q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues',
  'q9Comments',
  'q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock',
  'q10Comments',
  'rating',
  'ratingRationale',
] as const;

/**
 * The Opening fields the user must eventually fill in, with the label the banner names them by, in
 * the order they appear down the tab.
 *
 * Harvest Completion Year is here because submit requires it and the page maps it to this tab, even
 * though it arrives with the record rather than being typed — a blank one has to be raised
 * somewhere, and this is the tab that carries the rest of the block's identity.
 */
/**
 * The fieldset each required field is edited in, keyed as the label maps below.
 *
 * `yearOfHarvest` is absent because the Opening tab does not edit it — it arrives with the record
 * and is shown in the tombstone, so there is no section to send the reader to.
 */
export const OPENING_REQUIRED_SECTIONS: Record<string, string> = {
  evaluationDate: 'Evaluation',
  assessedBy: 'Evaluation',
  generalLocation: 'Evaluation',
};

export const BLOCK_REQUIRED_SECTIONS: Record<string, string> = {
  q8Comments: 'Operational review',
  q9Comments: 'Operational review',
  q10Comments: 'Operational review',
  rating: 'Block rating',
};

export const OPENING_REQUIRED_LABELS: Record<string, string> = {
  evaluationDate: 'Evaluation date',
  assessedBy: 'Evaluator — use “Assign it to me”',
  generalLocation: 'General location',
  yearOfHarvest: 'Harvest completion year',
};

/** The Block summary fields the user must eventually fill in, in tab order. */
export const BLOCK_REQUIRED_LABELS: Record<string, string> = {
  q8Comments: 'Q8 — description of the limiting operational factors',
  q9Comments: 'Q9 — description of the effective strategies used',
  q10Comments: 'Q10 — description of the strategies that could have been used',
  rating: 'Rating',
};

/** Whether anything has ever been stored on the Opening info tab. */
export const openingTouched = (value?: Partial<CheckList> | null): boolean =>
  value != null && OPENING_EDITABLE_FIELDS.some((key) => !isBlank(value[key] as string));

/** Whether anything has ever been stored on the Block summary tab. */
export const blockSummaryTouched = (value?: Partial<CheckList> | null): boolean =>
  value != null && BLOCK_EDITABLE_FIELDS.some((key) => !isBlank(value[key] as string));

/**
 * Required Opening fields that are still blank. These do <b>not</b> block the save — see the note at
 * the top of this file.
 *
 * `yearOfHarvest` is only counted against the whole checklist, never against an in-progress edit:
 * the Opening form has no field for it, so a draft never carries it and its absence there would be
 * an error the user could not act on.
 */
export const openingRequiredErrors = (
  value: Partial<CheckList>,
  options: { includeReadOnly?: boolean } = {},
): Record<string, string> => {
  const errors: Record<string, string> = {};
  if (isBlank(value.evaluationDate)) {
    errors.evaluationDate = 'Evaluation date is required.';
  }
  if (isBlank(value.generalLocation)) {
    errors.generalLocation = 'General location is required.';
  }
  if (isBlank(value.assessedBy)) {
    errors.assessedBy = 'Evaluator is required — choose “Assign it to me”.';
  }
  if (options.includeReadOnly && isBlank(value.yearOfHarvest)) {
    errors.yearOfHarvest = 'Harvest completion year is required.';
  }
  return errors;
};

/**
 * Opening errors the stored row could not survive: free text longer than its byte-semantic column.
 * These <b>do</b> block the save — the database would raise ORA-12899.
 */
export const openingFormatErrors = (value: Partial<CheckList>): Record<string, string> => {
  const errors: Record<string, string> = {};
  addTextLimitErrors(errors, value as Record<string, unknown>, OPENING_TEXT_LIMITS);
  return errors;
};

/**
 * Every field-level error on the Opening tab — what to show the user, blocking or not. A format
 * error wins over a required one on the same field, since it names a value the user actually typed.
 */
export const openingErrors = (value: Partial<CheckList>): Record<string, string> => ({
  ...openingRequiredErrors(value),
  ...openingFormatErrors(value),
});

/** Required Block summary fields that are still blank. Advisory — see the note above. */
export const blockSummaryRequiredErrors = (value: Partial<CheckList>): Record<string, string> => {
  const errors: Record<string, string> = {};
  if (isBlank(value.rating)) errors.rating = 'A rating is required.';
  if (
    isYes(value.q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock) &&
    isBlank(value.q8Comments)
  ) {
    errors.q8Comments = 'A description is required.';
  }
  if (
    isYes(
      value.q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues,
    ) &&
    isBlank(value.q9Comments)
  ) {
    errors.q9Comments = 'A description is required.';
  }
  if (
    isYes(
      value.q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock,
    ) &&
    isBlank(value.q10Comments)
  ) {
    errors.q10Comments = 'A description is required.';
  }
  return errors;
};

/** Block summary errors the stored row could not survive. These block the save. */
export const blockSummaryFormatErrors = (value: Partial<CheckList>): Record<string, string> => {
  const errors: Record<string, string> = {};
  addTextLimitErrors(errors, value as Record<string, unknown>, BLOCK_TEXT_LIMITS);
  return errors;
};

/** Every field-level error on the Block summary tab. Format wins over required on the same field. */
export const blockSummaryErrors = (value: Partial<CheckList>): Record<string, string> => ({
  ...blockSummaryRequiredErrors(value),
  ...blockSummaryFormatErrors(value),
});

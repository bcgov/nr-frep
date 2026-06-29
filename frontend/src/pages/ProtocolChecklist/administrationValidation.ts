import type { AdministrationData } from '@/types/protocolChecklist';

// Mirrors the legacy FREP301 FrepCostResourceValidatingManager "Save" chain so a bad save is caught
// inline (matching field-level messages) rather than only at the proc.

const HOURS_MAX = 9999.99;
const PEOPLE_MAX = 10;

const isBlank = (value?: string): boolean => value == null || value.trim() === '';

/** Today as a `yyyy-MM-dd` string — usable as the date-picker `maxDate` and for an ISO compare. */
export const todayIso = (): string => new Date().toISOString().slice(0, 10);

/** Team size = the team lead (when set) plus the non-lead members, per the legacy people count. */
export const teamCountOf = (data: AdministrationData): number => {
  const lead = isBlank(data.teamLeadNameId) ? 0 : 1;
  const members = (data.teamMembers ?? []).filter((m) => m.teamLeadInd !== 'Y').length;
  return lead + members;
};

const validateHours = (value: string | undefined, label: string): string | null => {
  if (isBlank(value)) return null;
  const text = value!.trim();
  if (!/^[-+]?\d*\.?\d+$/.test(text)) return `${label} must be a number.`;
  const number = Number(text);
  if (number < 0 || number > HOURS_MAX) return `${label} must be between 0 and ${HOURS_MAX}.`;
  const dot = text.indexOf('.');
  if (dot >= 0 && text.length - dot - 1 > 2) return `${label} can have at most 2 decimal places.`;
  return null;
};

const validatePeople = (value: string | undefined): string | null => {
  if (isBlank(value)) return null;
  const text = value!.trim();
  if (!/^-?\d+$/.test(text)) return 'People on block must be a whole number.';
  const number = Number(text);
  if (number < 0 || number > PEOPLE_MAX)
    return `People on block must be between 0 and ${PEOPLE_MAX}.`;
  return null;
};

const validateEvaluationDate = (value: string | undefined): string | null => {
  if (isBlank(value)) return null;
  // The value is a `yyyy-MM-dd` string (date picker), so a lexicographic compare is chronological.
  if (value!.trim() > todayIso()) return 'Evaluation date cannot be in the future.';
  return null;
};

/**
 * Field-level errors for the Administration scalar fields, keyed by {@link AdministrationData} key.
 * An empty object means the form is valid to save.
 */
export const validateAdministration = (data: AdministrationData): Record<string, string> => {
  const errors: Record<string, string> = {};

  const dateError = validateEvaluationDate(data.evaluationDate);
  if (dateError) errors.evaluationDate = dateError;

  const accessError = validateHours(data.blockAccessTime, 'Hrs. access time');
  if (accessError) errors.blockAccessTime = accessError;

  const hoursError = validateHours(data.hoursOnBlock, 'Hrs. on block');
  if (hoursError) errors.hoursOnBlock = hoursError;

  const peopleError = validatePeople(data.peopleOnBlock);
  if (peopleError) {
    errors.peopleOnBlock = peopleError;
  } else {
    // Cross-field (legacy "minimumPeople"): people on block must cover the listed team (blank → 0).
    const peopleOnBlock = isBlank(data.peopleOnBlock) ? 0 : Number(data.peopleOnBlock);
    if (teamCountOf(data) > peopleOnBlock) {
      errors.peopleOnBlock =
        'People on block must be greater than or equal to the total number of people listed on the team.';
    }
  }

  return errors;
};

/**
 * Legacy "Add Team Member / Team Lead" guard (`minimumPeopleOther`): the current team must be
 * smaller than People on block before another person can be added, so People on block always covers
 * the team. A blank or invalid People on block counts as 0 (i.e. blocks until it's increased).
 */
export const teamMemberAddBlocked = (data: AdministrationData): boolean => {
  const peopleOnBlock = isBlank(data.peopleOnBlock) ? 0 : Number(data.peopleOnBlock);
  if (!Number.isFinite(peopleOnBlock)) return true;
  return teamCountOf(data) >= peopleOnBlock;
};

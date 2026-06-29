import type { BiodiversityOpening } from '@/types/protocolChecklist';

// Mirrors the legacy FREP210 Frep210ValidationManager "Save" chain so a bad save is caught inline
// (matching field-level messages) rather than only at the proc.

const LOCATION_MAX = 50;
const COMMENT_MAX = 4000;
const RATIONALE_MAX = 2000;
const OVERRIDE_MIN = 0.01;
const OVERRIDE_MAX = 99999.99;

const isBlank = (value?: string): boolean => value == null || value.trim() === '';

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
 * object means the form is valid to save. Legacy parity: Location, Invasive plant?, Innovative
 * practice? and Rating are all required to save, plus the conditional comments and length/number limits.
 */
export const validateOpening = (data: BiodiversityOpening): Record<string, string> => {
  const errors: Record<string, string> = {};

  // Required: Location description (+ length).
  if (isBlank(data.locationDescription)) {
    errors.locationDescription = 'Location description is required.';
  } else if (data.locationDescription!.length > LOCATION_MAX) {
    errors.locationDescription = `Location description must be ${LOCATION_MAX} characters or fewer.`;
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
  if (data.innovativePracticeInd === 'Y' && isBlank(data.innovativePracticesComment)) {
    errors.innovativePracticesComment = 'Describe the innovative practice.';
  } else if ((data.innovativePracticesComment?.length ?? 0) > COMMENT_MAX) {
    errors.innovativePracticesComment = `Description must be ${COMMENT_MAX} characters or fewer.`;
  }

  // Invasive plant comment — required when invasive plants = Yes (+ length).
  if (data.invasivePlantIndicator === 'Y' && isBlank(data.invasivePlantComment)) {
    errors.invasivePlantComment = 'Enter a comment about the invasive plants.';
  } else if ((data.invasivePlantComment?.length ?? 0) > COMMENT_MAX) {
    errors.invasivePlantComment = `Comments must be ${COMMENT_MAX} characters or fewer.`;
  }

  // Rationale length.
  if ((data.evaluatorOpinionComment?.length ?? 0) > RATIONALE_MAX) {
    errors.evaluatorOpinionComment = `Rationale must be ${RATIONALE_MAX} characters or fewer.`;
  }

  // FREP gross area override — float within 0.01–99999.99, two decimals.
  const override = validateOverride(data.frepWtpOverride);
  if (override) errors.frepWtpOverride = override;

  return errors;
};

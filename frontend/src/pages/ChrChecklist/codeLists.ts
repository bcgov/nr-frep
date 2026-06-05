/**
 * CHR code lists, ported verbatim from the legacy Vue app (these were hardcoded there, not fetched):
 *  - feature class / information source / reserve type:
 *      nr-frep-legacy/.../components/js/cultureHeritage/reference/ReferenceCard.ts
 *  - reserve type dropdown: .../components/checklist/CultureHeritage/Location.vue
 *  - contact role: .../FirstNationProponentContacts.vue
 *  - feature/block rating: .../FeatureSummary.vue
 *  - MRVA calculation: .../components/js/observer.ts (calculateMRVARatingCode)
 */

import type { Feature } from '@/types/chrChecklist';

export type CodeOption = { code: string; label: string };

export const FEATURE_CLASS_CODES: CodeOption[] = [
  { code: 'ARCH', label: 'Archaeological Resource' },
  { code: 'CMT', label: 'Culturally Modified Tree(s)' },
  { code: 'PLNT', label: 'Cultural Plants' },
  { code: 'CT', label: 'Cultural Trail' },
  { code: 'EF', label: 'Ecological Feature(s)' },
  { code: 'TUA', label: 'Traditional Use Area' },
  { code: 'AOP', label: 'Area of Potential' },
  { code: 'HPZ', label: 'High Potential Zone' },
  { code: 'OTH', label: 'Other Feature Class' },
];

export const INFORMATION_SOURCE_CODES: CodeOption[] = [
  { code: 'AIA', label: 'AIA - Archaeological Impact Assessment' },
  { code: 'AOA', label: 'AOA - Archaeological Overview Assessment' },
  { code: 'CMTS', label: 'CMTS - CMT Survey' },
  { code: 'ISR', label: 'ISR - Information Sharing Reports' },
  { code: 'PCOM', label: 'PCOM - Personal Communication' },
  { code: 'PFR', label: 'PFR - Preliminary Field Reconnaissance' },
  { code: 'CHRS', label: 'CHRS - Pre-harvest CHR survey or assessment report' },
  { code: 'SP', label: 'SP - Site Plan' },
  { code: 'TUS', label: 'TUS - Traditional Use Study' },
  { code: 'OTH', label: 'OTH - Other Information Source' },
];

export const RESERVE_TYPE_CODES: CodeOption[] = [
  { code: '', label: 'None' },
  { code: 'PR', label: 'Patch riparian (PR)' },
  { code: 'PW', label: 'Patch wildlife (PW)' },
  { code: 'PO', label: 'Patch other (PO)' },
  { code: 'PT', label: 'Patch Temporary (PT)' },
  { code: 'PU', label: 'Patch unidentified (PU)' },
  { code: 'DR', label: 'Dispersed riparian (DR)' },
  { code: 'DW', label: 'Dispersed wildlife (DW)' },
  { code: 'DO', label: 'Dispersed other (DO)' },
  { code: 'DT', label: 'Dispersed temporary (DT)' },
  { code: 'HPZ', label: 'High Potential Zone (HPZ)' },
  { code: 'AOP', label: 'Area of potential (AOP)' },
  { code: 'OGMA', label: 'Old Growth Management Area (OGMA)' },
  { code: 'LUOR', label: 'Land Use Objectives Regulation (LUOR)' },
  { code: 'CCSRZ', label: 'Cultural Cedar Stand Reserve Zone (CCSRZ)' },
  { code: 'CC', label: 'Clearcut (CC)' },
];

export const CONTACT_ROLE_CODES: CodeOption[] = [
  { code: '', label: 'None' },
  { code: 'FN', label: 'First Nations' },
  { code: 'PROPONENT', label: 'Proponent (Licensee)' },
];

/** Feature and block-summary rating share the same scale (ChrSiteEvaluationCode). */
export const RATING_CODES: CodeOption[] = [
  { code: 'V', label: 'Very Poorly' },
  { code: 'P', label: 'Poorly' },
  { code: 'M', label: 'Moderately' },
  { code: 'W', label: 'Well' },
  { code: 'E', label: 'Very Well' },
  { code: 'U', label: "Don't know" },
];

const anyFeatureRatedPoorlyOrVeryPoorly = (features: Feature[] | undefined): boolean =>
  (features ?? []).some((f) => f.featureRating === 'P' || f.featureRating === 'V');

/**
 * Derive the MRVA rating code from the block-summary rating, mirroring legacy
 * observer.ts#calculateMRVARatingCode. Returns '' when no block rating is set.
 */
export const calculateMrvaRatingCode = (
  blockRating: string | undefined,
  features: Feature[] | undefined,
): string => {
  if (!blockRating) {
    return '';
  }
  switch (blockRating) {
    case 'U':
      return 'NUL';
    case 'P':
    case 'V':
      return 'HIGH';
    case 'E':
      return 'VERYLOW';
    case 'W':
      return anyFeatureRatedPoorlyOrVeryPoorly(features) ? 'LOW' : 'VERYLOW';
    case 'M':
      return anyFeatureRatedPoorlyOrVeryPoorly(features) ? 'MEDIUM' : 'LOW';
    default:
      return '';
  }
};

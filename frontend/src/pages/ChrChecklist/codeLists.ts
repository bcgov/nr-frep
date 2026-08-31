/**
 * What is left of the CHR code lists once the dropdowns stopped holding their own copies.
 *
 * The five option lists that used to live here — feature class, information source, reserve type,
 * contact role, rating — now come from their code tables through `useChrCodeLists`. Only their
 * display order and label shape stayed behind, because no table records either.
 *
 * The MRVA calculation below is not a code list. It is a rule that derives one code from another
 * (legacy `observer.ts#calculateMRVARatingCode`), and there is no table to read it from.
 */

import type { Feature } from '@/types/chrChecklist';

export type CodeOption = { code: string; label: string };

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

import { describe, expect, it } from 'vitest';

import type { Feature } from '@/types/chrChecklist';

import {
  CONTACT_ROLE_CODES,
  FEATURE_CLASS_CODES,
  RATING_CODES,
  RESERVE_TYPE_CODES,
  calculateMrvaRatingCode,
} from '@/pages/ChrChecklist/codeLists';

describe('CHR code lists', () => {
  it('ports the legacy static lists', () => {
    expect(FEATURE_CLASS_CODES.map((c) => c.code)).toContain('CMT');
    expect(CONTACT_ROLE_CODES.map((c) => c.code)).toEqual(['', 'FN', 'PROPONENT']);
    expect(RATING_CODES.map((c) => c.code)).toEqual(['V', 'P', 'M', 'W', 'E', 'U']);
    expect(RESERVE_TYPE_CODES.map((c) => c.code)).toContain('OGMA');
  });
});

describe('calculateMrvaRatingCode', () => {
  const featureRated = (rating: string): Feature[] => [{ featureRating: rating }];

  it('returns empty when no block rating', () => {
    expect(calculateMrvaRatingCode(undefined, [])).toBe('');
    expect(calculateMrvaRatingCode('', [])).toBe('');
  });

  it('maps direct ratings', () => {
    expect(calculateMrvaRatingCode('U', [])).toBe('NUL');
    expect(calculateMrvaRatingCode('P', [])).toBe('HIGH');
    expect(calculateMrvaRatingCode('V', [])).toBe('HIGH');
    expect(calculateMrvaRatingCode('E', [])).toBe('VERYLOW');
  });

  it('W depends on whether any feature is rated P or V', () => {
    expect(calculateMrvaRatingCode('W', featureRated('M'))).toBe('VERYLOW');
    expect(calculateMrvaRatingCode('W', featureRated('P'))).toBe('LOW');
    expect(calculateMrvaRatingCode('W', featureRated('V'))).toBe('LOW');
  });

  it('M depends on whether any feature is rated P or V', () => {
    expect(calculateMrvaRatingCode('M', featureRated('W'))).toBe('LOW');
    expect(calculateMrvaRatingCode('M', featureRated('P'))).toBe('MEDIUM');
  });
});

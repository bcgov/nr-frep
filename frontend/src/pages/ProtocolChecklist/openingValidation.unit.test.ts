import { describe, expect, it } from 'vitest';

import type { BiodiversityOpening } from '@/types/protocolChecklist';

import { validateOpening } from '@/pages/ProtocolChecklist/openingValidation';

const valid = (overrides: Partial<BiodiversityOpening> = {}): BiodiversityOpening => ({
  locationDescription: 'A clear-cut block near the lake',
  invasivePlantIndicator: 'N',
  innovativePracticeInd: 'N',
  frepSiteEvaluationCode: 'M',
  ...overrides,
});

describe('validateOpening', () => {
  it('returns no errors when all required fields are filled', () => {
    expect(validateOpening(valid())).toEqual({});
  });

  it('flags all four legacy-required fields when blank', () => {
    const errors = validateOpening({});
    expect(errors.locationDescription).toMatch(/required/);
    expect(errors.invasivePlantIndicator).toMatch(/invasive plant/i);
    expect(errors.innovativePracticeInd).toMatch(/innovative practices/i);
    expect(errors.frepSiteEvaluationCode).toMatch(/rating/i);
  });

  it('enforces the 50-char location description limit', () => {
    expect(
      validateOpening(valid({ locationDescription: 'x'.repeat(51) })).locationDescription,
    ).toMatch(/50 characters/);
  });

  it('requires the innovative practices comment only when the answer is Yes', () => {
    expect(
      validateOpening(valid({ innovativePracticeInd: 'Y' })).innovativePracticesComment,
    ).toMatch(/describe/i);
    expect(
      validateOpening(
        valid({ innovativePracticeInd: 'Y', innovativePracticesComment: 'did a thing' }),
      ).innovativePracticesComment,
    ).toBeUndefined();
  });

  it('requires the invasive plant comment only when the answer is Yes', () => {
    expect(validateOpening(valid({ invasivePlantIndicator: 'Y' })).invasivePlantComment).toMatch(
      /invasive plants/i,
    );
  });

  it('enforces comment and rationale length limits', () => {
    expect(
      validateOpening(valid({ invasivePlantComment: 'x'.repeat(4001) })).invasivePlantComment,
    ).toMatch(/4000 characters/);
    expect(
      validateOpening(valid({ evaluatorOpinionComment: 'x'.repeat(2001) })).evaluatorOpinionComment,
    ).toMatch(/2000 characters/);
  });

  it('validates the FREP gross area override (number, range, decimals)', () => {
    expect(validateOpening(valid({ frepWtpOverride: 'abc' })).frepWtpOverride).toMatch(/number/);
    expect(validateOpening(valid({ frepWtpOverride: '0' })).frepWtpOverride).toMatch(
      /between 0.01 and 99999.99/,
    );
    expect(validateOpening(valid({ frepWtpOverride: '100000' })).frepWtpOverride).toMatch(
      /between 0.01 and 99999.99/,
    );
    expect(validateOpening(valid({ frepWtpOverride: '1.234' })).frepWtpOverride).toMatch(
      /2 decimal/,
    );
    expect(validateOpening(valid({ frepWtpOverride: '12.5' })).frepWtpOverride).toBeUndefined();
  });
});

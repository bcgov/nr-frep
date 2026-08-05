import { describe, expect, it } from 'vitest';

import type { BiodiversityOpening } from '@/types/protocolChecklist';

import { validateOpening } from '@/pages/ProtocolChecklist/openingValidation';

const valid = (overrides: Partial<BiodiversityOpening> = {}): BiodiversityOpening => ({
  locationDescription: 'A clear-cut block near the lake',
  evaluationDate: '2024-06-01',
  teamLeadNameId: 'IDIR\\LEAD',
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

  it('enforces the 50-byte location description limit', () => {
    expect(
      validateOpening(valid({ locationDescription: 'x'.repeat(51) })).locationDescription,
    ).toMatch(/the limit is 50 and this entry uses 51/);
  });

  it('measures the length limits in bytes, matching the byte-semantic columns', () => {
    // BIODIVERSITY_CHECKLIST.LOCATION_DESCRIPTION is VARCHAR2(50 BYTE), so 26 curly quotes (26
    // characters, 78 bytes) overflows it even though a character count would say it fits.
    const smartQuotes = '\u2019'.repeat(26);
    expect(smartQuotes).toHaveLength(26);
    expect(
      validateOpening(valid({ locationDescription: smartQuotes })).locationDescription,
    ).toMatch(/the limit is 50 and this entry uses 78/);
    // …and a value that fits in bytes is still accepted.
    expect(
      validateOpening(valid({ locationDescription: 'x'.repeat(50) })).locationDescription,
    ).toBeUndefined();
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
    ).toMatch(/the limit is 4000 and this entry uses 4001/);
    expect(
      validateOpening(valid({ evaluatorOpinionComment: 'x'.repeat(2001) })).evaluatorOpinionComment,
    ).toMatch(/the limit is 2000 and this entry uses 2001/);
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

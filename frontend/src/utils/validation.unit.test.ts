import { describe, expect, it } from 'vitest';

import { errorsForSettledFields, isNumberInProgress } from '@/utils/validation';

describe('isNumberInProgress', () => {
  it('recognises a number the user is part way through writing', () => {
    for (const text of ['', '  ', '-', '+', '.', '12.', '007.']) {
      expect(isNumberInProgress(text)).toBe(true);
    }
  });

  it('treats a finished number as finished', () => {
    // The important half: "100" is a complete value, so a range rule must be free to judge it while
    // it is still on screen.
    for (const text of ['0', '100', '12.5', 'tset', '1.2.3']) {
      expect(isNumberInProgress(text)).toBe(false);
    }
  });
});

describe('errorsForSettledFields', () => {
  const errors = { size: 'too small', plotNumber: 'is required.', bearing: 'out of range' };
  const values: Record<string, string> = { size: '0.001', plotNumber: '', bearing: '400' };
  const valueOf = (key: string) => values[key];

  it('reports a field the user filled in and left', () => {
    expect(errorsForSettledFields(errors, new Set(['size']), valueOf)).toEqual({
      size: 'too small',
    });
  });

  it('says nothing about a field that was left blank', () => {
    // Visiting an empty field is not the same as answering it wrongly.
    expect(errorsForSettledFields(errors, new Set(['plotNumber']), valueOf)).toEqual({});
  });

  it('says nothing about a field the user has not left yet', () => {
    expect(errorsForSettledFields(errors, new Set(), valueOf)).toEqual({});
  });
});

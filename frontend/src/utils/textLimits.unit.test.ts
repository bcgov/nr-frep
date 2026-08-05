import { describe, expect, it } from 'vitest';

import { FEATURE_TEXT_LIMITS, NOTES_TEXT_LIMITS } from '@/pages/ChrChecklist/textLimits';
import { addTextLimitErrors, byteLength, overLimitError } from '@/utils/textLimits';

describe('byteLength', () => {
  it('counts ASCII as one each', () => {
    expect(byteLength('hello')).toBe(5);
    expect(byteLength('')).toBe(0);
    expect(byteLength(undefined)).toBe(0);
  });

  it('counts the multi-byte characters Word substitutes as more than one', () => {
    // The whole reason the limit is measured in bytes: these look like one character each.
    expect(byteLength('é')).toBe(2); // accented Latin
    expect(byteLength('’')).toBe(3); // curly apostrophe
    expect(byteLength('—')).toBe(3); // em-dash
    expect(byteLength('…')).toBe(3); // ellipsis
  });
});

describe('overLimitError', () => {
  it('accepts a value exactly at the limit', () => {
    expect(overLimitError('a'.repeat(500), 500)).toBe('');
  });

  it('rejects one byte over', () => {
    expect(overLimitError('a'.repeat(501), 500)).toBe(
      'Too long — the limit is 500 and this entry uses 501.',
    );
  });

  it('rejects a value that is under the limit in characters but over it in bytes', () => {
    // 200 curly apostrophes = 200 characters but 600 bytes. A character-based maxLength would
    // have let this through and the save would have failed at the database.
    const smartQuotes = '’'.repeat(200);
    expect(smartQuotes.length).toBe(200);
    expect(overLimitError(smartQuotes, 500)).toBe(
      'Too long — the limit is 500 and this entry uses 600.',
    );
  });
});

describe('addTextLimitErrors', () => {
  it('flags only the fields that are over', () => {
    const errors: Record<string, string> = {};
    addTextLimitErrors(
      errors,
      { featureComment: 'a'.repeat(501), featureDescription: 'a'.repeat(999) },
      FEATURE_TEXT_LIMITS,
    );

    expect(Object.keys(errors)).toEqual(['featureComment']);
  });

  it('leaves an existing error on the same field alone', () => {
    // "A description is required." is more useful than a length complaint on the same field.
    const errors: Record<string, string> = { q4Description: 'A description is required.' };
    addTextLimitErrors(errors, { q4Description: 'a'.repeat(2001) }, FEATURE_TEXT_LIMITS);

    expect(errors.q4Description).toBe('A description is required.');
  });

  it('ignores fields that are absent or not strings', () => {
    const errors: Record<string, string> = {};
    addTextLimitErrors(errors, { featureComment: undefined }, FEATURE_TEXT_LIMITS);

    expect(errors).toEqual({});
  });
});

describe('the limit tables', () => {
  it('matches the legacy DDL column widths', () => {
    // Transcribed from nr-frep-legacy/database/ddl/tab/CHR_FEATURE_DETAIL.tab and
    // CHR_FEATURE_IDENTITY.tab — a mismatch here means a save fails at the database instead.
    expect(FEATURE_TEXT_LIMITS).toEqual({
      featureDescription: 1000,
      descriptionofdamage: 1000,
      q4Description: 2000,
      q5Description: 2000,
      q6Description: 2000,
      featureRatingRationale: 2000,
      featureComment: 500,
    });
    // The Notes tab writes CHR_CHECKLIST.BLOCK_COMMENTS — 500, not the 2000 of the question boxes.
    expect(NOTES_TEXT_LIMITS).toEqual({ commentaires: 500 });
  });
});

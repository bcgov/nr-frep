import { describe, expect, it } from 'vitest';

import type { Feature } from '@/types/chrChecklist';

import {
  duplicateLabelError,
  featureBlockingErrors,
  featureHasErrors,
  featureTypingErrors,
} from '@/pages/ChrChecklist/featureValidation';

const feature = (over: Partial<Feature> = {}): Feature =>
  ({ featureLabel: '1', ...over }) as Feature;

/**
 * The split that decides what can be said on every keystroke: a value no further typing can rescue
 * is reported at once, a value that is merely unfinished waits for Save. See utils/validation.ts.
 */
describe('featureTypingErrors', () => {
  it('names a letter in a number straight away', () => {
    expect(featureTypingErrors(feature({ trailLength: 'tset' })).trailLength).toMatch(
      /whole number/,
    );
  });

  it('names a value past the maximum straight away', () => {
    expect(featureTypingErrors(feature({ estwindthrow: '500' })).estwindthrow).toMatch(
      /at most 100/,
    );
  });

  it('names a decimal place too many straight away', () => {
    expect(featureTypingErrors(feature({ areaofFeature: '2.55555' })).areaofFeature).toMatch(
      /4 decimal places/,
    );
  });

  it('leaves a number half-typed alone', () => {
    // "12." is on its way to 12.5. Reported once the user says the feature is finished.
    expect(featureTypingErrors(feature({ areaofFeature: '12.' })).areaofFeature).toBeUndefined();
    expect(featureBlockingErrors(feature({ areaofFeature: '12.' })).areaofFeature).toMatch(
      /must be a number/,
    );
  });

  it('leaves a half-typed Borden number alone', () => {
    // Every Borden number passes through "Aa" and "AaBb-" on the way to being right.
    const partial = feature({ chrRegisteredSite: 'true', borden: 'AaBb' });
    expect(featureTypingErrors(partial).borden).toBeUndefined();
    expect(featureBlockingErrors(partial).borden).toMatch(/Borden format/);
  });

  it('says nothing about a required field left blank', () => {
    // Blank is a gap, not a bad value — marked and counted, but never nagged about mid-edit.
    expect(featureTypingErrors(feature({ ofCMTs: 'true' }))).toEqual({});
  });
});

/**
 * The label is system-assigned and its field is read-only, so this can no longer be provoked by
 * typing — but `CHFID_UK` is still UNIQUE (CHR_CHECKLIST_ID, FEATURE_LABEL), and the check still
 * gates the feature through `featureHasErrors`. Covered here because the browser tests that used to
 * exercise it drove the message through the field, which now renders no invalid state.
 */
describe('duplicateLabelError', () => {
  it('flags a label another feature already uses', () => {
    expect(duplicateLabelError(feature({ featureLabel: '2' }), ['1', '2', '3'])).toEqual({
      featureLabel: 'Already used by another feature.',
    });
  });

  it('does not flag a feature against labels it does not clash with', () => {
    expect(duplicateLabelError(feature({ featureLabel: '2' }), ['1', '3'])).toEqual({});
  });

  it('treats labels differing only in case as the same', () => {
    // Stricter than the constraint on purpose: Oracle would allow "A" and "a", but composite
    // membership matches labels case-insensitively, so the pair would be indistinguishable.
    expect(duplicateLabelError(feature({ featureLabel: 'a' }), ['A'])).toEqual({
      featureLabel: 'Already used by another feature.',
    });
  });

  it('says nothing about a feature with no label yet', () => {
    expect(duplicateLabelError(feature({ featureLabel: '' }), ['1'])).toEqual({});
  });

  it('still fails the feature through featureHasErrors', () => {
    expect(featureHasErrors(feature({ featureLabel: '2' }), ['2'])).toBe(true);
  });
});

import { describe, expect, it } from 'vitest';

import type { Feature } from '@/types/chrChecklist';

import { featureBlockingErrors, featureTypingErrors } from '@/pages/ChrChecklist/featureValidation';

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

import { describe, expect, it } from 'vitest';

import { BEC_SEARCH_MAX } from '@/pages/ProtocolChecklist/stratumLimits';
import { BEC_CRITERIA_FIELD, filterBec } from '@/services/offline/bioReferenceCache';

import type { BecRow } from '@/types/configuration';

/**
 * The offline BEC search filters the cached catalogue itself, so it has to agree with the picker on
 * what the criteria are called. It didn't: the filter read the criteria object using the *row's*
 * field names, so every needle was undefined and the search returned everything.
 *
 * The parity test is the one that matters — the cases below would all still pass if a *new* field
 * were added to the picker and forgotten here.
 */
describe('offline BEC search', () => {
  it('covers exactly the fields the picker offers', () => {
    // BEC_SEARCH_MAX stands in for the picker's own BEC_CRITERIA: textLimits.unit.test already holds
    // those two key sets equal, so chaining through it keeps this honest without exporting a
    // constant from a component module (which trips react-refresh/only-export-components).
    expect(Object.keys(BEC_CRITERIA_FIELD).sort()).toEqual(Object.keys(BEC_SEARCH_MAX).sort());
  });

  const rows: BecRow[] = [
    { bgcZoneCode: 'SBS', bgcSubzoneCode: 'mk', becSiteSeriesCd: '01', seral: 'Y' },
    { bgcZoneCode: 'SBS', bgcSubzoneCode: 'wk', becSiteSeriesCd: '05', seral: 'N' },
    { bgcZoneCode: 'ICH', bgcSubzoneCode: 'mw', becSiteSeriesCd: '01', seral: 'N' },
  ];

  it('filters on site series — the field that was reported broken', () => {
    expect(filterBec(rows, { siteSeries: '05' })).toEqual([rows[1]]);
  });

  it('filters on zone, which silently matched everything before', () => {
    expect(filterBec(rows, { zone: 'ICH' })).toEqual([rows[2]]);
  });

  it('ANDs multiple criteria', () => {
    expect(filterBec(rows, { zone: 'SBS', siteSeries: '01' })).toEqual([rows[0]]);
  });

  it('is case-insensitive and matches substrings', () => {
    expect(filterBec(rows, { subzone: 'W' })).toEqual([rows[1], rows[2]]);
  });

  it('returns everything when nothing is entered, and ignores whitespace-only input', () => {
    expect(filterBec(rows, {})).toEqual(rows);
    expect(filterBec(rows, { siteSeries: '   ' })).toEqual(rows);
  });

  it('returns nothing when a criterion matches nothing', () => {
    // The old filter returned all three here — the actual user-visible symptom.
    expect(filterBec(rows, { siteSeries: '99' })).toEqual([]);
  });
});

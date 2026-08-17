import { describe, expect, it } from 'vitest';

import { OPENING_TEXT_LIMITS } from './openingValidation';
import { PLOT_TEXT_LIMITS } from './plotValidation';
import { BEC_SEARCH_MAX, STRATUM_FIELD_MAX, STRATUM_TEXT_LIMITS } from './stratumLimits';

/**
 * Deployed SLR column widths, from nr-mof-db `scripts/THE/TABLES/`.
 *
 * <p>The tables are `BIODIVERSITY_*` — **without** the `FREP_` prefix that the PL/SQL packages
 * carry (`FREP_BIODIVERSITY_CHECKLIST` is the package; `BIODIVERSITY_CHECKLIST` is the table).
 * Looking for the prefixed name finds nothing and reads as "the DDL isn't in the repo", which is
 * how these limits came to be transcribed by hand in the first place.
 *
 * <p>Deployed widths only: base DDL plus migrations merged to main. `V999999999999.3` widens
 * BIODIVERSITY_CHKLST_ATTACH.DESCRIPTION 120 → 2000 but is not merged, so 120 is still the live
 * width — raising the UI to 2000 now would let a user type text the database rejects.
 */
const DEPLOYED_WIDTH: Record<string, number> = {
  // BIODIVERSITY_CHECKLIST (V2.00399)
  locationDescription: 50,
  innovativePracticesComment: 4000,
  invasivePlantComment: 4000,
  evaluatorOpinionComment: 2000,
  // BIODIVERSITY_STRATUM (V2.00406)
  otherConstraint: 50,
  otherEcoAnchorDesc: 30,
  patchGeneralComment: 2000,
  // BIODIVERSITY_PLOT (V2.00403)
  plotComment: 2000,
};

describe('SLR free-text limits track the columns', () => {
  const maps: Array<[string, Record<string, number>]> = [
    ['OPENING_TEXT_LIMITS', OPENING_TEXT_LIMITS],
    ['STRATUM_TEXT_LIMITS', STRATUM_TEXT_LIMITS],
    ['PLOT_TEXT_LIMITS', PLOT_TEXT_LIMITS],
  ];

  it.each(maps)('%s never exceeds the deployed column', (_name, map) => {
    for (const [field, limit] of Object.entries(map)) {
      expect(DEPLOYED_WIDTH[field], `${field} has no recorded column width`).toBeDefined();
      expect(limit, `${field} limit exceeds its column`).toBeLessThanOrEqual(DEPLOYED_WIDTH[field]);
    }
  });

  it('keeps the tightest columns tight', () => {
    // These two are the narrowest free-text columns in either protocol and the easiest to overrun
    // in ordinary use; a silent widening here is the regression worth catching.
    expect(STRATUM_TEXT_LIMITS.otherEcoAnchorDesc).toBe(30);
    expect(STRATUM_TEXT_LIMITS.otherConstraint).toBe(50);
    expect(OPENING_TEXT_LIMITS.locationDescription).toBe(50);
  });
});

/**
 * Columns the BEC search matches against, from nr-mof-db `V2.00408__BIOGEOCLIMATIC_CATALOGUE.sql`
 * and `V2.03073__SITE_SERIES_CATALOGUE.sql` — the two tables FREP_52_BGC_SEARCH.mainline joins.
 */
const BEC_COLUMN_WIDTH: Record<string, number> = {
  zone: 4,
  subzone: 3,
  variant: 1,
  phase: 1,
  siteSeries: 4,
  siteSeriesPhase: 3,
  seral: 4,
};

describe('BEC search criteria caps', () => {
  it('never exceeds the column each criterion searches', () => {
    // The match is LIKE '%term%', so a term longer than the column cannot be a substring of it —
    // the search would return nothing with no indication why.
    for (const [field, cap] of Object.entries(BEC_SEARCH_MAX)) {
      expect(BEC_COLUMN_WIDTH[field], `${field} has no recorded column width`).toBeDefined();
      expect(cap, `${field} cap exceeds its column`).toBeLessThanOrEqual(BEC_COLUMN_WIDTH[field]);
    }
  });

  it('covers all seven criteria the dialog renders', () => {
    expect(Object.keys(BEC_SEARCH_MAX).sort()).toEqual(Object.keys(BEC_COLUMN_WIDTH).sort());
  });
});

/** BIODIVERSITY_STRATUM widths for the short persisted inputs (V2.00406). */
const STRATUM_COLUMN_WIDTH: Record<string, number> = {
  bgcZoneCode: 4,
  bgcSubzoneCode: 3,
  bgcVariant: 1,
  bgcPhase: 1,
  becSiteSeriesCd: 4,
  siteSeriesPhaseCd: 3,
  seral: 4,
  otherWindthrowTreatment: 50,
  stratumNumber: 10,
};

describe('stratum short-input caps', () => {
  it('never exceeds the column each field writes', () => {
    for (const [field, cap] of Object.entries(STRATUM_FIELD_MAX)) {
      expect(STRATUM_COLUMN_WIDTH[field], `${field} has no recorded column width`).toBeDefined();
      expect(cap, `${field} cap exceeds its column`).toBeLessThanOrEqual(
        STRATUM_COLUMN_WIDTH[field],
      );
    }
  });

  it('never gives a field both a character cap and a byte counter', () => {
    // The two mechanisms are mutually exclusive by design: maxLength truncates on characters, the
    // counter measures bytes and blocks Save. Together, a paste would be silently cut before the
    // counter could show it was over — which is the failure the counter exists to prevent.
    const both = Object.keys(STRATUM_FIELD_MAX).filter((k) => k in STRATUM_TEXT_LIMITS);
    expect(both).toEqual([]);
  });

  it('leaves the counter fields entirely uncapped', () => {
    for (const field of Object.keys(STRATUM_TEXT_LIMITS)) {
      expect(STRATUM_FIELD_MAX[field], `${field} must not carry a maxLength`).toBeUndefined();
    }
  });

  it('keeps the search-dialog caps separate from the persisted ones', () => {
    // `seral` exists in both, meaning different things: a search term vs the stored code. They
    // happen to share a width; asserting it stops one being "fixed" to match the other.
    expect(BEC_SEARCH_MAX.seral).toBe(4);
    expect(STRATUM_FIELD_MAX.seral).toBe(4);
  });
});

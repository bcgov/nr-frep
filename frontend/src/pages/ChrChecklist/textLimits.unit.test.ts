import { describe, expect, it } from 'vitest';

import { FEATURE_SINGLE_LINE_MAX, FEATURE_TEXT_LIMITS } from './textLimits';

/**
 * Deployed column widths, transcribed from nr-mof-db `scripts/THE/TABLES/`.
 *
 * <p>Deliberately the *deployed* widths — base DDL plus only migrations merged to main. Three CHR
 * columns have widening migrations sitting on an unmerged branch (BLOCK_COMMENTS 500→2000,
 * EVALUATION_RATING_RATIONALE 2000→4000, CHR_FEATURE_IDENTITY.COMMENTS 500→2000); raising the UI to
 * the wider value before those ship would let a user type text the live database rejects.
 */
const DEPLOYED_WIDTH: Record<string, number> = {
  featureLabel: 5, // CHR_FEATURE_IDENTITY.FEATURE_LABEL
  permit: 50, // CHR_FEATURE_DETAIL.PERMIT_NUMBER
  otherdescription: 200, // CHR_FEATURE_TYPE_XREF.OTHER_DESCRIPTION
  locationOtherDescription: 200, // CHR_FEATURE_LOCATION_DETAIL.OTHER_DESCRIPTION
  otherStrategy: 200, // CHR_MGMT_STRATEGY_PLANNED.OTHER_STRATEGY
  otherActivities: 200, // CHR_MGMT_STRATEGY_USED.OTHER_STRATEGY
  ifotherpleasedescribe: 200, // CHR_FEAT_WINDTHR_TREAT_XREF.OTHER_DESCRIPTION
  ifotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause: 200,
  // ^ CHR_FEATURE_DAMAGE_AGENT_XREF.OTHER_DESCRIPTION
};

describe('single-line CHR field caps', () => {
  it('never lets the UI accept more than the column holds', () => {
    for (const [field, cap] of Object.entries(FEATURE_SINGLE_LINE_MAX)) {
      expect(DEPLOYED_WIDTH[field], `${field} has no recorded column width`).toBeDefined();
      expect(cap, `${field} cap exceeds its column`).toBeLessThanOrEqual(DEPLOYED_WIDTH[field]);
    }
  });

  it('covers every field the audit found unguarded', () => {
    // Guards against a field being dropped from the map while its input keeps the maxLength prop.
    expect(Object.keys(FEATURE_SINGLE_LINE_MAX).sort()).toEqual(Object.keys(DEPLOYED_WIDTH).sort());
  });

  it('keeps the byte-counted and character-capped sets disjoint', () => {
    // A field must use one mechanism or the other: `limit` counts bytes and blocks Save, while
    // `maxLength` truncates on characters. Both on one field would truncate before the counter
    // could report it.
    const overlap = Object.keys(FEATURE_SINGLE_LINE_MAX).filter((k) => k in FEATURE_TEXT_LIMITS);
    expect(overlap).toEqual([]);
  });
});

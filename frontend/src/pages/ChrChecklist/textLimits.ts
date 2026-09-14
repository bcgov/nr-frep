/**
 * Length limits for the CHR free-text fields, keyed by the draft field each form edits.
 *
 * <p>Every limit is a **byte** count — see `@/utils/textLimits` for why that matters and for the
 * helpers that measure and report it. Values are transcribed from nr-mof-db
 * (`scripts/THE/TABLES/`); the comment on each entry names the column so the two can be re-checked
 * against each other.
 *
 * <p>`featureComment`, `ratingRationale` and the Notes tab's `commentaires` were widened by
 * migration `V202608051100.*` (nr-mof-db). **These values are only correct once that migration has
 * been deployed to the target environment** — shipping them ahead of it lets the UI accept text the
 * insert will reject.
 */

/** CHR_FEATURE_DETAIL / CHR_FEATURE_IDENTITY free-text columns, keyed by `Feature` field. */
export const FEATURE_TEXT_LIMITS: Record<string, number> = {
  featureDescription: 1000, // CHR_FEATURE_DETAIL.DESCRIPTION
  descriptionofdamage: 1000, // CHR_FEATURE_DETAIL.DAMAGE_DESCRIPTION
  q4Description: 2000, // CHR_FEATURE_DETAIL.LIMITING_OPERATNL_FACTORS_DESC
  q5Description: 2000, // CHR_FEATURE_DETAIL.EFFECTIVE_STRATS_USED_DESC
  q6Description: 2000, // CHR_FEATURE_DETAIL.ALTERNATE_STRATS_AVAIL_DESC
  featureRatingRationale: 2000, // CHR_FEATURE_DETAIL.EVALUATION_RATING_RATIONALE
  featureComment: 2000, // CHR_FEATURE_IDENTITY.COMMENTS
};

/** CHR_CHECKLIST free-text columns, keyed by the opening-information draft field. */
export const OPENING_TEXT_LIMITS: Record<string, number> = {
  generalLocation: 200, // CHR_CHECKLIST.LOCATION_DESCRIPTION
};

/** CHR_CHECKLIST free-text columns, keyed by the block-summary draft field. */
export const BLOCK_TEXT_LIMITS: Record<string, number> = {
  q8Comments: 2000, // CHR_CHECKLIST.LIMITING_OPERATNL_FACTORS_DESC
  q9Comments: 2000, // CHR_CHECKLIST.EFFECTIVE_STRATS_USED_DESC
  q10Comments: 2000, // CHR_CHECKLIST.ALTERNATE_STRATS_AVAIL_DESC
  ratingRationale: 4000, // CHR_CHECKLIST.EVALUATION_RATING_RATIONALE
};

/**
 * The CHR Notes tab. Persisted through the block-summary save, so it writes the same column the
 * legacy CHR "Comments" tab did.
 */
export const NOTES_TEXT_LIMITS: Record<string, number> = {
  commentaires: 2000, // CHR_CHECKLIST.BLOCK_COMMENTS
};

/** The Photos tab's upload form — one description per uploaded image. */
export const ATTACHMENT_TEXT_LIMITS: Record<string, number> = {
  description: 2000, // CHR_CHECKLIST_ATTACHMENT.DESCRIPTION
};

/**
 * Single-line CHR text fields, keyed by the `Feature` draft field.
 *
 * <p>Applied as Carbon's `maxLength`, which stops typing and truncates paste at the given number of
 * **characters** — unlike the byte-counted `limit` used for the multi-line boxes above. The two are
 * deliberately different: a paragraph silently losing its tail is a real data loss, so long free
 * text gets a counter and a blocked Save instead; these fields are short and mostly codes or
 * identifiers, where a hard stop at the limit is what the user expects.
 *
 * <p>The consequence is that a value of accented or syllabic characters can still exceed the byte
 * limit at this character count. That is not silent: the insert raises ORA-12899, which
 * `ColumnOverflow` turns into a 400 naming the field. The cap removes the ordinary case; the
 * backend still covers the multi-byte one.
 *
 * <p>Widths are the deployed ones — base DDL in nr-mof-db `scripts/THE/TABLES/`, with no pending
 * widening migration against any of these columns (unlike BLOCK_COMMENTS / EVALUATION_RATING_
 * RATIONALE / CHR_FEATURE_IDENTITY.COMMENTS above, whose widenings are not yet merged to main).
 */
export const FEATURE_SINGLE_LINE_MAX: Record<string, number> = {
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

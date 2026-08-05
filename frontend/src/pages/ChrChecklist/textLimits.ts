/**
 * Length limits for the CHR free-text fields, keyed by the draft field each form edits.
 *
 * <p>Every limit is a **byte** count — see `@/utils/textLimits` for why that matters and for the
 * helpers that measure and report it. Values are transcribed from the legacy DDL
 * (`nr-frep-legacy/database/ddl/tab/`); the comment on each entry names the column so the two can
 * be re-checked against each other.
 */

/** CHR_FEATURE_DETAIL / CHR_FEATURE_IDENTITY free-text columns, keyed by `Feature` field. */
export const FEATURE_TEXT_LIMITS: Record<string, number> = {
  featureDescription: 1000, // CHR_FEATURE_DETAIL.DESCRIPTION
  descriptionofdamage: 1000, // CHR_FEATURE_DETAIL.DAMAGE_DESCRIPTION
  q4Description: 2000, // CHR_FEATURE_DETAIL.LIMITING_OPERATNL_FACTORS_DESC
  q5Description: 2000, // CHR_FEATURE_DETAIL.EFFECTIVE_STRATS_USED_DESC
  q6Description: 2000, // CHR_FEATURE_DETAIL.ALTERNATE_STRATS_AVAIL_DESC
  featureRatingRationale: 2000, // CHR_FEATURE_DETAIL.EVALUATION_RATING_RATIONALE
  featureComment: 500, // CHR_FEATURE_IDENTITY.COMMENTS
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
  ratingRationale: 2000, // CHR_CHECKLIST.EVALUATION_RATING_RATIONALE
};

/**
 * The CHR Notes tab. Persisted through the block-summary save, so it is the same column the legacy
 * CHR "Comments" tab wrote — noticeably tighter than the 2000-byte question boxes beside it.
 */
export const NOTES_TEXT_LIMITS: Record<string, number> = {
  commentaires: 500, // CHR_CHECKLIST.BLOCK_COMMENTS
};

/** The Photos tab's upload form — one description per uploaded image. */
export const ATTACHMENT_TEXT_LIMITS: Record<string, number> = {
  description: 2000, // CHR_CHECKLIST_ATTACHMENT.DESCRIPTION
};

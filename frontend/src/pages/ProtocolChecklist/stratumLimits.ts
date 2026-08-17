/**
 * Byte limits for the stratum's free-text fields, keyed by field — the same numbers the length
 * rules in BioStratumView enforce, shared with the counter so display and validation can't drift
 * apart.
 *
 * <p>Bytes, not characters: the columns are byte-semantic. `BIODIVERSITY_STRATUM.OTHER_CONSTRAINT`
 * is `VARCHAR2(50 BYTE)` and `OTHER_ECO_ANCHOR_DESC` is `VARCHAR2(30 BYTE)` (nr-mof-db
 * `scripts/THE/TABLES/V2.00406__BIODIVERSITY_STRATUM.sql`). Note the table has no `FREP_` prefix,
 * unlike the `FREP_BIODIVERSITY_*` PL/SQL packages that read and write it.
 *
 * <p>Lives in its own module rather than beside the component so the drift test can import it
 * without tripping the fast-refresh rule against non-component exports.
 */
export const STRATUM_TEXT_LIMITS: Record<string, number> = {
  otherConstraint: 50,
  otherEcoAnchorDesc: 30,
  patchGeneralComment: 2000,
};

/**
 * Character caps for the BEC search dialog's seven criteria.
 *
 * <p>Not write limits — these feed `FREP_52_BGC_SEARCH.mainline`, which matches each criterion with
 * `LIKE '%' || value || '%'` against `BIOGEOCLIMATIC_CATALOGUE` / `SITE_SERIES_CATALOGUE`. A term
 * longer than the column it searches simply cannot match a substring of it, so the search silently
 * returns nothing and the user is left guessing. The cap makes that unreachable.
 *
 * <p>Characters rather than bytes, and no counter: these are short code fields (1–4), the criteria
 * are ASCII codes, and nothing is persisted from this dialog — the picked row supplies the values
 * that get saved. Widths from nr-mof-db `V2.00408__BIOGEOCLIMATIC_CATALOGUE.sql` and
 * `V2.03073__SITE_SERIES_CATALOGUE.sql`.
 *
 * <p>Unlike the client lookup, an over-long term here is harmless: the criteria bind as scalar
 * IN OUT VARCHAR2 params, not through an object type, so there is no bind-time ORA-17072.
 */
export const BEC_SEARCH_MAX: Record<string, number> = {
  zone: 4, // BIOGEOCLIMATIC_CATALOGUE.BEC_ZONE_CODE
  subzone: 3, // BIOGEOCLIMATIC_CATALOGUE.SUBZONE
  variant: 1, // BIOGEOCLIMATIC_CATALOGUE.VARIANT
  phase: 1, // BIOGEOCLIMATIC_CATALOGUE.PHASE
  siteSeries: 4, // SITE_SERIES_CATALOGUE.SITE_SERIES
  siteSeriesPhase: 3, // SITE_SERIES_CATALOGUE.SITE_SERIES_PHASE
  seral: 4, // SITE_SERIES_CATALOGUE.SERAL
};

/**
 * Character caps for the stratum's short persisted text inputs, keyed by field.
 *
 * <p>These write straight into `BIODIVERSITY_STRATUM` through scalar proc params. The BGC/site-
 * series codes are normally filled by picking a row in the BEC search dialog, but every one of them
 * is a plain editable input, so they can also be typed — and at 1–4 characters wide there is very
 * little room. Widths from nr-mof-db `V2.00406__BIODIVERSITY_STRATUM.sql`.
 *
 * <p>Deliberately excludes `patchGeneralComment`: that field carries the byte counter via
 * STRATUM_TEXT_LIMITS, and a character `maxLength` alongside it would truncate pasted text before
 * the counter could report it being over. Each field gets one mechanism, never both.
 *
 * <p>`stratumNumber` also has a stricter format rule (letters-then-digits, ≤5); this is only the
 * column-width backstop.
 */
export const STRATUM_FIELD_MAX: Record<string, number> = {
  bgcZoneCode: 4, // BGC_ZONE_CODE
  bgcSubzoneCode: 3, // BGC_SUBZONE_CODE
  bgcVariant: 1, // BGC_VARIANT
  bgcPhase: 1, // BGC_PHASE
  becSiteSeriesCd: 4, // BEC_SITE_SERIES_CD
  siteSeriesPhaseCd: 3, // SITE_SERIES_PHASE_CD
  seral: 4, // SERAL
  otherWindthrowTreatment: 50, // OTHER_WINDTHROW_TREATMENT
  stratumNumber: 10, // STRATUM_NUMBER
};

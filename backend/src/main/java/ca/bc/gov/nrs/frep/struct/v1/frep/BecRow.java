package ca.bc.gov.nrs.frep.struct.v1.frep;

/**
 * One row of the BEC (biogeoclimatic) catalogue for the FREP211 BEC search. Mirrors the legacy
 * {@code FREP_52_BGC_SEARCH} result columns (BIOGEOCLIMATIC_CATALOGUE + SITE_SERIES_CATALOGUE).
 */
public record BecRow(
    String bgcZoneCode,
    String bgcSubzoneCode,
    String bgcVariant,
    String bgcPhase,
    String becSiteSeriesCd,
    String siteSeriesPhaseCd,
    String seral,
    String description
) {}

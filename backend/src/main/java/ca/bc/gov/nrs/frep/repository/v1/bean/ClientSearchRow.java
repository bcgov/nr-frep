package ca.bc.gov.nrs.frep.repository.v1.bean;

/**
 * One row returned by {@code FREP_410_CLIENT_SEARCH} (one row per client location).
 *
 * <p>Attribute order matches {@code FREP_CLIENT_SEARCH_VW_OBJECT} in legacy DDL.
 */
public record ClientSearchRow(
    String clientNumber,
    String clientAcronym,
    String displayClientNumber,
    String clientName,
    String clientLocnCode,
    String clientLocnName,
    String city,
    String clientStatusCode
) {}

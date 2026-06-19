package ca.bc.gov.nrs.frep.repository.v1.bean;

/**
 * Search criteria passed to {@code FREP_410_CLIENT_SEARCH} via
 * {@code FREP_CLIENT_SEARCH_VW_OBJECT}.
 */
public record ClientSearchCriteria(
    String clientNumber,
    String clientAcronym,
    String clientName,
    String legalFirstName,
    String legalMiddleName
) {}

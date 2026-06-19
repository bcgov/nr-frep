package ca.bc.gov.nrs.frep.struct.v1.frep;

/**
 * One row of the FREP410 client search results — one row per client location,
 * mirroring the legacy {@code frep410ClientSearch.jsp} results grid.
 *
 * @param clientAcronym  client acronym
 * @param clientNumber   display client number (8-digit)
 * @param clientLocnCode 2-char location code
 * @param clientName     legal name
 * @param clientLocnName location name
 * @param city           city
 * @param clientStatus   {@code ACT} active / {@code DAC} deactivated
 */
public record ClientSearchResult(
    String clientAcronym,
    String clientNumber,
    String clientLocnCode,
    String clientName,
    String clientLocnName,
    String city,
    String clientStatus
) {}

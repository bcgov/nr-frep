package ca.bc.gov.nrs.frep.dto;

/**
 * One row of the FREP410 client search results.
 *
 * @param clientNumber  8-digit client number
 * @param clientName    legal name
 * @param clientStatus  {@code ACT} active / {@code DAC} deactivated
 * @param locationCount number of distinct locations on file
 */
public record ClientSearchResult(
    String clientNumber,
    String clientName,
    String clientStatus,
    int locationCount
) {}

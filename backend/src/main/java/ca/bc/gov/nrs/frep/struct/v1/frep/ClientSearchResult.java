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
    /**
     * The real 8-character {@code FOREST_CLIENT.CLIENT_NUMBER} — what every downstream filter needs.
     * Never the acronym: the checklist search matches {@code client_number = LPAD(:clientNumber, 8,
     * '0')}, so an acronym here becomes '000ARDEW' and matches nothing.
     */
    String clientNumber,
    /**
     * What the legacy screens showed in the "client number" column: {@code NVL(acronym, number)}.
     * Kept separate from {@link #clientNumber} because it is a display value, not an identifier.
     */
    String displayClientNumber,
    String clientLocnCode,
    String clientName,
    String clientLocnName,
    String city,
    String clientStatus
) {}

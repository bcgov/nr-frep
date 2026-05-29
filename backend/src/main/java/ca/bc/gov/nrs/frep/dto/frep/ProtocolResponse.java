package ca.bc.gov.nrs.frep.dto.frep;

/**
 * FREP protocol (evaluation type) reference entry.
 *
 * <p>Mirrors {@code resource_value_type} values used by legacy filters
 * (Biodiversity, Riparian, Water Quality, Culture Heritage Resource).
 *
 * @param code  short code passed to the {@code /accepted-sites} filter
 * @param name  display name
 */
public record ProtocolResponse(String code, String name) {}

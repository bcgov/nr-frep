package ca.bc.gov.nrs.frep.dto.frep;

/**
 * FREP protocol (evaluation type) reference entry.
 *
 * <p>Mirrors rows from {@code FREP_CODE_LISTS.GET_RESOURCE_VALUE} /
 * {@code frep_resource_value_type_code} (e.g. {@code SLB}, {@code RIP},
 * {@code WTR}, {@code CHR}).
 *
 * @param code  short code passed to the {@code /accepted-sites} filter
 * @param name  display name
 */
public record ProtocolResponse(String code, String name) {}

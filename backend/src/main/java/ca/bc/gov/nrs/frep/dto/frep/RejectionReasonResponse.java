package ca.bc.gov.nrs.frep.dto.frep;

/**
 * A site-resource rejection reason for the FREP110 rejection-reason dropdown.
 *
 * <p>Legacy source: {@code FREP_CODE_LISTS.get_site_resource_reason_code} →
 * table {@code frep_site_resource_reason_code}.
 *
 * @param code        {@code frep_site_resource_reason_code}
 * @param description human-readable reason
 */
public record RejectionReasonResponse(String code, String description) {}

package ca.bc.gov.nrs.frep.dto.frep;

/**
 * One resource value row inside the FREP110 Site Details screen.
 *
 * <p>Legacy equivalent: {@code FREP_RESOURCE_OBJECT} returned in
 * {@code FREP_110_SITE_DETAILS.get(...).p_resource_array}.
 *
 * @param resourceType        e.g. {@code "BIO"}, {@code "RIP"}, {@code "WAT"}, {@code "CHR"}
 * @param resourceName        display name
 * @param statusCode          one of {@code ACC} / {@code REJ} / {@code TAR}
 * @param rejectionReasonCode FK to {@code frep_site_resource_reason_code}, only set when status = REJ
 * @param rationale           rationale text
 * @param otherComments       free-form notes
 * @param checklistId         present when an actual checklist has been generated
 * @param checklistStatusCode {@code RDY} / {@code SUB} / etc., or {@code null} if no checklist yet
 */
public record SiteResourceResponse(
    String resourceType,
    String resourceName,
    String statusCode,
    String rejectionReasonCode,
    String rationale,
    String otherComments,
    String checklistId,
    String checklistStatusCode
) {}

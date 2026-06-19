package ca.bc.gov.nrs.frep.struct.v1.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One resource-value evaluation submitted by the FREP110 Site Details screen. Mirrors the subset of
 * {@code FREP_RESOURCE_OBJECT} that {@code FREP_110_SITE_DETAILS.SAVE} reads: resource_id (2),
 * resource_type (4), stat_code (5), rejection_rationale (7), frep_site_resource_reason_code (8),
 * resource_comment (9), revision_count (10). Accepting/targeting (ACC/TAR) spawns a checklist;
 * rejecting (REJ) removes it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SiteResourceSaveRequest(
    String resourceValueId,
    String resourceType,
    String statusCode,
    String rejectionReasonCode,
    String rationale,
    String otherComments,
    String revisionCount
) {}

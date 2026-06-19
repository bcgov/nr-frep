package ca.bc.gov.nrs.frep.repository.v1.bean;

/**
 * One resource value row inside {@code FREP_110_SITE_DETAILS.GET}.
 *
 * <p>Attribute order matches {@code FREP_RESOURCE_OBJECT} in legacy DDL.
 */
public record SiteResourceRow(
    String resourceValueId,
    String siteCode,
    String resourceType,
    String resourceName,
    String statusCode,
    String checklistStatusCode,
    String rationale,
    String rejectionReasonCode,
    String otherComments,
    String revisionCount
) {}

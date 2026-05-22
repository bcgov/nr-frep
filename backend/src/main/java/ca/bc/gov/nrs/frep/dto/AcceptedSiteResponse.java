package ca.bc.gov.nrs.frep.dto;

/**
 * Read-only accepted site row for the Phase 1 portal.
 *
 * <p>Field names align with legacy {@code AcceptedSitesArrayBean} / dashboard columns.
 */
public record AcceptedSiteResponse(
    String checklistId,
    String checklistType,
    String sampleNumber,
    boolean targeted,
    String openingNumber,
    String openingId,
    String licenceId,
    String cuttingPermitId,
    String cutBlockId,
    String harvestCompleteDate,
    String checklistStatusCode,
    String checklistStatus,
    String protocolCode,
    String protocolName,
    String effectiveYear,
    String orgUnitNo
) {}

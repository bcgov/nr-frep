package ca.bc.gov.nrs.frep.repository.v1.bean;

/**
 * Row returned by {@code FREP_200_ACCEPTED_SITES.GET} via
 * {@code THE.FREP_ACC_SITES_OBJECT}.
 */
public record AcceptedSiteRow(
    String checklistId,
    String checklistType,
    String sampleNumber,
    String resourceValueStatCode,
    String checklistStatusCode,
    String openingNumber,
    String openingId,
    String licenceId,
    String cuttingPermitId,
    String cutBlockId,
    String harvestCompleteDate
) {}

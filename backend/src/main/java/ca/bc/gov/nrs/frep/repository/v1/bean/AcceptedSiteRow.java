package ca.bc.gov.nrs.frep.repository.v1.bean;

/**
 * One accepted/targeted site row from the consolidated FREP200 query (Biodiversity + Cultural
 * Heritage). Field names align with the legacy {@code THE.FREP_ACC_SITES_OBJECT} the proc returned.
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

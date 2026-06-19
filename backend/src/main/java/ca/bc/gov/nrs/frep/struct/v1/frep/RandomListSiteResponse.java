package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * One row of the FREP100 District Random List.
 *
 * <p>Legacy equivalent: {@code FREP_RANDOM_LIST_OBJECT} populated by
 * {@code FREP_100_DIST_RAND_LIST.get(...)}.
 *
 * @param frepSelectedSiteId  PK of {@code FREP_SELECTED_SITE} (used to link to FREP110)
 * @param underReview         {@code true} when at least one resource value exists for this site
 *                            (legacy {@code is_review = 'Y'})
 * @param orgUnitCode         district code, e.g. {@code "DCK"}
 * @param openingNumber       formatted opening, e.g. {@code "A12345"}
 * @param openingId           numeric opening id
 * @param licenceId           tenure / forest file id
 * @param cuttingPermitId     CP id
 * @param cutBlockId          cut block id
 * @param exhibitArea         exhibit-A area in hectares
 * @param grossArea           gross area in hectares
 * @param netArea             net area in hectares
 * @param disturbanceStartDate  start of harvest disturbance window (harvest-start date)
 * @param disturbanceEndDate    end of harvest disturbance window (harvest-complete date)
 * @param managementUnit      management unit (e.g. TSA/TFL designation)
 * @param existingChecklists  protocol codes already evaluated on this site, e.g. {@code ["BIO", "RIP"]}
 */
public record RandomListSiteResponse(
    String frepSelectedSiteId,
    boolean underReview,
    String orgUnitCode,
    String openingNumber,
    String openingId,
    String licenceId,
    String cuttingPermitId,
    String cutBlockId,
    Double exhibitArea,
    Double grossArea,
    Double netArea,
    String disturbanceStartDate,
    String disturbanceEndDate,
    String managementUnit,
    List<String> existingChecklists
) {}

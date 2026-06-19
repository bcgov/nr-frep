package ca.bc.gov.nrs.frep.struct.v1.frep;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Typed, editable biodiversity stratum (FREP screen 211). Field order matches the
 * {@code FREP_211_BIOSTRATUM.SAVE_STRATUM} parameters. Because that proc's UPDATE path rewrites
 * every column, the read populates and the save sends back ALL scalar fields (the UI may surface a
 * subset; unsurfaced fields are round-tripped) plus the windthrow-treatment sub-collection.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BioStratum(
    String stratumId,
    String checklistId,
    String strataTypeCode,
    String stratumNumber,
    String summaryDate,
    String assessorName,
    String plotCount,
    String size,
    String consistentMapInd,
    String estimatedSize,
    String patchLocationCode,
    String patchEstimatedOldestTreeAge,
    String patchGeneralComment,
    String patchWindthrowPct,
    String constraintIndicator,
    String wetlandPct,
    String harvestAreaCode,
    String riparianManagementZonePct,
    String riparianReserveZonePct,
    String rockOutcropPct,
    String nonCommercialBrushPct,
    String nonMerchTimberPct,
    String sensitiveSoilPct,
    String ungHoofAnimalWinteringPct,
    String wildlifeHabitatAreaPct,
    String oldGrowthManagementAreaPct,
    String visualsPct,
    String culturalHeritageFeaturePct,
    String recreationFeaturePct,
    String otherConstraint,
    String otherConstraintPct,
    String ecoIndicator,
    String bearDenCnt,
    String hibernaculumCnt,
    String vetTreeCnt,
    String mineralLickCnt,
    String largeStickNestCnt,
    String cavityNestCnt,
    String largeHallowTreeCnt,
    String largeWitchesBroomCnt,
    String karstFeatureInd,
    String largestTreeInd,
    String cwdHeavyConcentrationInd,
    String activeWildlifeTrailsInd,
    String activeWltCwdFeedingInd,
    String uncommonTreeSpeciesInd,
    String otherEcoAnchorCnt,
    String otherEcoAnchorDesc,
    String bgcZoneCode,
    String bgcSubzoneCode,
    String bgcVariant,
    String bgcPhase,
    String becSiteSeriesCd,
    String siteSeriesPhaseCd,
    String seral,
    String windthrowDistributionCode,
    String otherWindthrowTreatment,
    String constrainedTotal,
    String revisionCount,
    List<BioWindthrowTreatment> windthrowTreatments
) {

  /** Copy with the checklist id from the request path (authoritative for new strata). */
  public BioStratum withChecklist(String newChecklistId) {
    return new BioStratum(
        stratumId, newChecklistId, strataTypeCode, stratumNumber, summaryDate, assessorName,
        plotCount, size, consistentMapInd, estimatedSize, patchLocationCode,
        patchEstimatedOldestTreeAge, patchGeneralComment, patchWindthrowPct, constraintIndicator,
        wetlandPct, harvestAreaCode, riparianManagementZonePct, riparianReserveZonePct,
        rockOutcropPct, nonCommercialBrushPct, nonMerchTimberPct, sensitiveSoilPct,
        ungHoofAnimalWinteringPct, wildlifeHabitatAreaPct, oldGrowthManagementAreaPct, visualsPct,
        culturalHeritageFeaturePct, recreationFeaturePct, otherConstraint, otherConstraintPct,
        ecoIndicator, bearDenCnt, hibernaculumCnt, vetTreeCnt, mineralLickCnt, largeStickNestCnt,
        cavityNestCnt, largeHallowTreeCnt, largeWitchesBroomCnt, karstFeatureInd, largestTreeInd,
        cwdHeavyConcentrationInd, activeWildlifeTrailsInd, activeWltCwdFeedingInd,
        uncommonTreeSpeciesInd, otherEcoAnchorCnt, otherEcoAnchorDesc, bgcZoneCode, bgcSubzoneCode,
        bgcVariant, bgcPhase, becSiteSeriesCd, siteSeriesPhaseCd, seral, windthrowDistributionCode,
        otherWindthrowTreatment, constrainedTotal, revisionCount, windthrowTreatments
    );
  }

  /** Copy with the id/revision the SAVE proc echoes back. */
  public BioStratum withIdentity(String newStratumId, String newRevisionCount) {
    return new BioStratum(
        newStratumId, checklistId, strataTypeCode, stratumNumber, summaryDate, assessorName,
        plotCount, size, consistentMapInd, estimatedSize, patchLocationCode,
        patchEstimatedOldestTreeAge, patchGeneralComment, patchWindthrowPct, constraintIndicator,
        wetlandPct, harvestAreaCode, riparianManagementZonePct, riparianReserveZonePct,
        rockOutcropPct, nonCommercialBrushPct, nonMerchTimberPct, sensitiveSoilPct,
        ungHoofAnimalWinteringPct, wildlifeHabitatAreaPct, oldGrowthManagementAreaPct, visualsPct,
        culturalHeritageFeaturePct, recreationFeaturePct, otherConstraint, otherConstraintPct,
        ecoIndicator, bearDenCnt, hibernaculumCnt, vetTreeCnt, mineralLickCnt, largeStickNestCnt,
        cavityNestCnt, largeHallowTreeCnt, largeWitchesBroomCnt, karstFeatureInd, largestTreeInd,
        cwdHeavyConcentrationInd, activeWildlifeTrailsInd, activeWltCwdFeedingInd,
        uncommonTreeSpeciesInd, otherEcoAnchorCnt, otherEcoAnchorDesc, bgcZoneCode, bgcSubzoneCode,
        bgcVariant, bgcPhase, becSiteSeriesCd, siteSeriesPhaseCd, seral, windthrowDistributionCode,
        otherWindthrowTreatment, constrainedTotal, newRevisionCount, windthrowTreatments
    );
  }
}

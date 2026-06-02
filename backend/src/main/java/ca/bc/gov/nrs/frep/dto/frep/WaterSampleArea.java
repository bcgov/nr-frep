package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Typed, editable water sample-area screen (FREP 250). Field order mirrors the
 * {@code THE.FREP_WTR_CHKLST_OBJECT} attribute order (38 attrs — note the object type has no
 * evaluation_date), plus the disturbance and access-road child collections. Persisted via
 * {@code FREP_250_WATER_CHKLST_SAVE} (object + access-road VARRAY + disturbance VARRAY); the object
 * carries the optimistic-lock revisionCount.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaterSampleArea(
    String waterChecklistId,
    String frepResourceValueId,
    String statusCode,
    String siteAccessCode,
    String mainAccessRoadNumber,
    String mainWatershedDescription,
    String drinkingWaterAnswerCode,
    String waterIntakeComment,
    String intakeToCutblockDistance,
    String waterIntakeConnectivityCode,
    String intakeToCutblockComment,
    String specResourceAnswerCode,
    String specialResourceValueComment,
    String reportedDisturbanceInd,
    String fertilizerUseOnRoadInd,
    String fertilizerUseWithinBlckInd,
    String sensitiveSoilAnswerCode,
    String herbicideUseOnRoadInd,
    String herbicideUseWithinBlockInd,
    String pesticideUseOnRoadInd,
    String pesticideUseWithinBlockInd,
    String streamCrossingsInd,
    String roadsParallelToStreamInd,
    String unstableSlopesInd,
    String sensitiveSoilsInd,
    String adjacentHarvestingInd,
    String livestockConcernsInd,
    String otherActivityInd,
    String otherActivityDescription,
    String noteDescription,
    String blockAccessTime,
    String hoursOnBlock,
    String peopleOnBlock,
    String invasivePlantAnswerCode,
    String invasivePlantComment,
    String revisionCount,
    String entryUserid,
    String updateUserid,
    List<WtrDisturbanceRow> disturbances,
    List<WtrAccessRoadRow> accessRoads
) {

  /** Returns a copy with the checklist id/revision the save proc echoes back via the IN OUT object. */
  public WaterSampleArea withIdentity(String newChecklistId, String newRevisionCount) {
    return new WaterSampleArea(
        newChecklistId, frepResourceValueId, statusCode, siteAccessCode, mainAccessRoadNumber,
        mainWatershedDescription, drinkingWaterAnswerCode, waterIntakeComment,
        intakeToCutblockDistance, waterIntakeConnectivityCode, intakeToCutblockComment,
        specResourceAnswerCode, specialResourceValueComment, reportedDisturbanceInd,
        fertilizerUseOnRoadInd, fertilizerUseWithinBlckInd, sensitiveSoilAnswerCode,
        herbicideUseOnRoadInd, herbicideUseWithinBlockInd, pesticideUseOnRoadInd,
        pesticideUseWithinBlockInd, streamCrossingsInd, roadsParallelToStreamInd, unstableSlopesInd,
        sensitiveSoilsInd, adjacentHarvestingInd, livestockConcernsInd, otherActivityInd,
        otherActivityDescription, noteDescription, blockAccessTime, hoursOnBlock, peopleOnBlock,
        invasivePlantAnswerCode, invasivePlantComment, newRevisionCount, entryUserid,
        updateUserid, disturbances, accessRoads
    );
  }

  public WaterSampleArea withChecklist(String newChecklistId) {
    return withIdentity(newChecklistId, revisionCount);
  }
}

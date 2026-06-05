package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Typed, editable water sample-site screen (FREP 251). Field order mirrors the
 * {@code THE.FREP_WTR_SAMPLE_SITE_OBJECT} attribute order (28 attrs). {@code statusCode} (joined)
 * and {@code rangeImpactEvaluationInd} (computed) are read-only on the legacy side but are
 * round-tripped for completeness. Persisted via {@code FREP_251_SAMPLE_SITE_SAVE}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaterSampleSite(
    String waterSampleSiteId,
    String waterChecklistId,
    String statusCode,
    String waterSiteType,
    String waterStreamWidthCode,
    String evaluatorNameId,
    String domesticIntakeInd,
    String sampleSiteNumber,
    String utmSignal,
    String utmZone,
    String utmEasting,
    String utmNorthing,
    String roadTypeCode,
    String roadUseCode,
    String roadReference,
    String watershedReference,
    String communityWatershedInd,
    String rangeImpactEvaluationInd,
    String waterCompromisedInd,
    String otherObservedConditionInd,
    String otherObservedConditionDesc,
    String otherSolutionInd,
    String otherSolutionDescription,
    String assessmentComment,
    String rangeComment,
    String revisionCount,
    String entryUserid,
    String updateUserid
) {

  public WaterSampleSite withIdentity(String newSampleSiteId, String newRevisionCount) {
    return new WaterSampleSite(
        newSampleSiteId, waterChecklistId, statusCode, waterSiteType, waterStreamWidthCode,
        evaluatorNameId, domesticIntakeInd, sampleSiteNumber, utmSignal, utmZone, utmEasting,
        utmNorthing, roadTypeCode, roadUseCode, roadReference, watershedReference,
        communityWatershedInd, rangeImpactEvaluationInd, waterCompromisedInd,
        otherObservedConditionInd, otherObservedConditionDesc, otherSolutionInd,
        otherSolutionDescription, assessmentComment, rangeComment, newRevisionCount, entryUserid,
        updateUserid
    );
  }

  public WaterSampleSite withChecklist(String newChecklistId) {
    return new WaterSampleSite(
        waterSampleSiteId, newChecklistId, statusCode, waterSiteType, waterStreamWidthCode,
        evaluatorNameId, domesticIntakeInd, sampleSiteNumber, utmSignal, utmZone, utmEasting,
        utmNorthing, roadTypeCode, roadUseCode, roadReference, watershedReference,
        communityWatershedInd, rangeImpactEvaluationInd, waterCompromisedInd,
        otherObservedConditionInd, otherObservedConditionDesc, otherSolutionInd,
        otherSolutionDescription, assessmentComment, rangeComment, revisionCount, entryUserid,
        updateUserid
    );
  }
}

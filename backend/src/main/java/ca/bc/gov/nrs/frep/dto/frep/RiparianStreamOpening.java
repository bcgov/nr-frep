package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Typed, editable view of a riparian stream-opening screen (FREP screen 230). Scalar field order
 * mirrors the {@code FREP_230_STRM_OPEN.SAVE} parameters (the stream-edge VARRAY is param 9, carried
 * here as {@code streamEdge}). All values are Strings; {@code revisionCount} is the optimistic-lock
 * token. Every column round-trips so the SAVE (which rewrites all columns) never nulls a field.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RiparianStreamOpening(
    String checklistId,
    String sampleNumber,
    String rangeUsePlan,
    String pastureId,
    String streamName,
    String streamLocationInd,
    String plnRiparianStrmRmaCls,
    String actRiparianStrmRmaCls,
    String channelWidth,
    String channelGradientPct,
    String channelDepth,
    String reachLocationTo,
    String reachLocationFrom,
    String reachLocationUpsDsInd,
    String reachLocationFromDesc,
    String utmSignal,
    String utmAtReference,
    String utmZone,
    String utmEasting,
    String utmNorthing,
    String riparianChanMorphology,
    String rttnRmaDomsOnPlans,
    String rttnRmaDomsOnPlansInd,
    String rttnRmaDomsInField,
    String rttnRmaUndrstryOnPlans,
    String rttnRmaUndrstryOnPlnI,
    String rttnRmaUndrstryInField,
    String rttnRrzDomsOnPlans,
    String rttnRrzDomsOnPlansInd,
    String rttnRrzDomsInFieldPct,
    String rttnRrzDomsInField,
    String rttnRrzUndrstryOnPlans,
    String rttnRrzUndrstryOnPlnI,
    String rttnRrzUndrstryFldPct,
    String rttnRrzUndrstryInField,
    String rttnRmzDomsOnPlans,
    String rttnRmzDomsOnPlansInd,
    String rttnRmzDomsInField,
    String rttnRmzUndrstryOnPlans,
    String rttnRmzUndrstryOnPlnI,
    String rttnRmzUndrstryInField,
    String plnRiparianStrNaInd,
    String invasivePlantIndicator,
    String invasivePlantComment,
    String revisionCount,
    List<RipStreamEdgeRow> streamEdge
) {

  /** Returns a copy with the id/revision the SAVE proc echoes back. */
  public RiparianStreamOpening withIdentity(String newChecklistId, String newRevisionCount) {
    return new RiparianStreamOpening(
        newChecklistId, sampleNumber, rangeUsePlan, pastureId, streamName, streamLocationInd,
        plnRiparianStrmRmaCls, actRiparianStrmRmaCls, channelWidth, channelGradientPct, channelDepth,
        reachLocationTo, reachLocationFrom, reachLocationUpsDsInd, reachLocationFromDesc, utmSignal,
        utmAtReference, utmZone, utmEasting, utmNorthing, riparianChanMorphology, rttnRmaDomsOnPlans,
        rttnRmaDomsOnPlansInd, rttnRmaDomsInField, rttnRmaUndrstryOnPlans, rttnRmaUndrstryOnPlnI,
        rttnRmaUndrstryInField, rttnRrzDomsOnPlans, rttnRrzDomsOnPlansInd, rttnRrzDomsInFieldPct,
        rttnRrzDomsInField, rttnRrzUndrstryOnPlans, rttnRrzUndrstryOnPlnI, rttnRrzUndrstryFldPct,
        rttnRrzUndrstryInField, rttnRmzDomsOnPlans, rttnRmzDomsOnPlansInd, rttnRmzDomsInField,
        rttnRmzUndrstryOnPlans, rttnRmzUndrstryOnPlnI, rttnRmzUndrstryInField, plnRiparianStrNaInd,
        invasivePlantIndicator, invasivePlantComment, newRevisionCount, streamEdge
    );
  }

  /** Returns a copy bound to the given checklist id (the path is authoritative on save). */
  public RiparianStreamOpening withChecklist(String newChecklistId) {
    return withIdentity(newChecklistId, revisionCount);
  }
}

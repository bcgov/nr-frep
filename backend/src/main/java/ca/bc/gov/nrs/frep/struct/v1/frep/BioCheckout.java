package ca.bc.gov.nrs.frep.struct.v1.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The checkout state of a Stand Level Retention (SLR) checklist, returned by take-offline / release /
 * activate.
 *
 * <p>Deliberately small. The CHR equivalents return the whole {@code CheckList} because CHR is a
 * single aggregate; SLR is a graph, and take-offline is not where the client obtains it — the
 * snapshot read is. All the client needs back is the new status and, on take-offline, the token it
 * must present for every subsequent write while checked out, plus the evaluator: take-offline may
 * claim it, after the snapshot was read (see {@code ProtocolChecklistService.takeOffline}).
 *
 * @param checklistId            the checklist
 * @param statusCode             its status after the operation ({@code RDO} or {@code ACT})
 * @param deviceCheckoutGuid     the checkout token; null once released or activated
 * @param evaluatorId            take-offline only: the evaluator (team lead) userid
 * @param evaluatorName          take-offline only: the evaluator's FAM-resolved display name
 * @param evaluatorRevisionCount take-offline only: the evaluator row's revision
 */
public record BioCheckout(
    String checklistId,
    String statusCode,
    String deviceCheckoutGuid,
    // Omitted when null, so release / activate responses keep their existing shape.
    @JsonInclude(JsonInclude.Include.NON_NULL) String evaluatorId,
    @JsonInclude(JsonInclude.Include.NON_NULL) String evaluatorName,
    @JsonInclude(JsonInclude.Include.NON_NULL) String evaluatorRevisionCount
) {

  /** Release / activate: no evaluator to report. */
  public BioCheckout(String checklistId, String statusCode, String deviceCheckoutGuid) {
    this(checklistId, statusCode, deviceCheckoutGuid, null, null, null);
  }
}

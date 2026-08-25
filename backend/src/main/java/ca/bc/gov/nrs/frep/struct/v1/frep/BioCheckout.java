package ca.bc.gov.nrs.frep.struct.v1.frep;

/**
 * The checkout state of a Stand Level Retention (SLR) checklist, returned by take-offline / release /
 * activate.
 *
 * <p>Deliberately small. The CHR equivalents return the whole {@code CheckList} because CHR is a
 * single aggregate; SLR is a graph, and take-offline is not where the client obtains it — the
 * snapshot read is. All the client needs back is the new status and, on take-offline, the token it
 * must present for every subsequent write while checked out.
 *
 * @param checklistId        the checklist
 * @param statusCode         its status after the operation ({@code RDO} or {@code ACT})
 * @param deviceCheckoutGuid the checkout token; null once released or activated
 */
public record BioCheckout(String checklistId, String statusCode, String deviceCheckoutGuid) {}

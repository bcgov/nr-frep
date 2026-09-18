package ca.bc.gov.nrs.frep.struct.v1.frep;

/**
 * Whether a device still holds a checklist's checkout — the read an offline copy needs to know it is
 * still syncable.
 *
 * <p><b>Deliberately does not return the token.</b> The caller supplies its own and the server
 * compares; a device learns whether <em>it</em> is the holder, never who the holder is.
 *
 * <p>That is the difference from CHR, whose {@code getChecklist} returns {@code deviceCheckoutGuid}
 * to anyone with district access — and that token is exactly what the attachment and check-in guards
 * accept as proof of holding the checkout. Handing it out would give away the credential those guards
 * are built on.
 *
 * @param checklistId       the checklist
 * @param statusCode        its current status
 * @param heldByThisDevice  true only when the checklist is checked out AND the supplied token matches
 */
public record BioCheckoutState(String checklistId, String statusCode, boolean heldByThisDevice) {}

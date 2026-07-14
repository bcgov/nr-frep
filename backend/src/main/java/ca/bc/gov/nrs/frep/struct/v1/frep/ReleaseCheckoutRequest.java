package ca.bc.gov.nrs.frep.struct.v1.frep;

/**
 * Release an offline CHR checkout. The {@code deviceCheckoutGuid} is the token issued by "take
 * offline"; the release only succeeds when it matches the server's value (proof that the caller holds
 * the checkout), so an editor can release their own checkout without being able to touch another
 * device's. Sent in the body rather than the URL so the token isn't logged.
 */
public record ReleaseCheckoutRequest(String deviceCheckoutGuid) {}

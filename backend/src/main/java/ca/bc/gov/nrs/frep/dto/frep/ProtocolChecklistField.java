package ca.bc.gov.nrs.frep.dto.frep;

/**
 * One labelled field inside a protocol-checklist section.
 *
 * <p>Generic label/value pair so the frontend can render any of the legacy
 * BIO / RIP / WAT screens with a single component.
 *
 * @param label  human label (e.g. {@code "Stratum #"})
 * @param value  display value (already formatted on the server)
 * @param kind   hint for the renderer: {@code TEXT}, {@code NUMBER}, {@code DATE},
 *               {@code YES_NO}, {@code MULTILINE}
 */
public record ProtocolChecklistField(
    String label,
    String value,
    String kind
) {}

package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * Outcome of validating an opening for targeting ({@code FREP_200_ACCEPTED_SITES.ADD_TARGETED_SITE}).
 * When {@code valid} is true the client proceeds to create the targeted site detail for {@code openingId}
 * in {@code orgUnit}; otherwise {@code messages} holds the friendly reasons the opening can't be
 * targeted (wrong district, active harvesting blocks).
 */
public record TargetedSiteValidationResponse(
    boolean valid,
    List<String> messages,
    String openingId,
    String orgUnit
) {}

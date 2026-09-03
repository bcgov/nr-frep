package ca.bc.gov.nrs.frep.struct.v1.frep;

/**
 * Save one feature's own fields — what the feature editor holds.
 *
 * <p>Scoped deliberately to the feature itself. Its <b>relationships</b> are not in here and are not
 * touched by the write: associations have their own endpoint (they name two features, so the server
 * writes both directions), and composite membership is owned by the composite endpoints. The editor
 * offers neither, so a save from it can leave both alone — which also means this endpoint never
 * resolves a reference through a feature label.
 *
 * @param revisionCount the checklist token the client last saw
 * @param feature       the feature's fields, as the editor holds them
 */
public record FeatureSaveRequest(String revisionCount, Feature feature) {}

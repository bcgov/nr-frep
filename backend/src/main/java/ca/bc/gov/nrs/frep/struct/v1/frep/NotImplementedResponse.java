package ca.bc.gov.nrs.frep.struct.v1.frep;

/**
 * Body returned by endpoints that are wired in the UI but not yet implemented on the
 * backend (HTTP 501). These mark migrated-but-deferred peripheral features —
 * Export-to-Excel, Print, and Map/GIS view — so the frontend can show a friendly
 * "coming soon" message instead of a generic error.
 *
 * @param status  always {@code NOT_IMPLEMENTED}
 * @param feature short feature key, e.g. {@code export-checklists}
 * @param message human-readable explanation
 */
public record NotImplementedResponse(String status, String feature, String message) {

  public static NotImplementedResponse of(String feature, String message) {
    return new NotImplementedResponse("NOT_IMPLEMENTED", feature, message);
  }
}

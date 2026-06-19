package ca.bc.gov.nrs.frep.dto.frep;

/**
 * Response for the per-opening GIS "Map View" action (legacy {@code MapViewAction} /
 * {@code frep_map_bounding_values}). The backend composes the external map-viewer URL, scoped to the
 * opening's bounding box when spatial data exists, and the client opens it in a new tab.
 *
 * @param url the external map-viewer URL to open. Empty when no map viewer is configured
 *     ({@code MAP_VIEWER_URL} unset) — the client treats that as "Map View unavailable".
 */
public record MapViewResponse(String url) {}

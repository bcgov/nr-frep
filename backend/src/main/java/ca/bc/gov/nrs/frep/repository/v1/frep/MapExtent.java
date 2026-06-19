package ca.bc.gov.nrs.frep.repository.v1.frep;

import org.apache.commons.lang3.StringUtils;

/**
 * Bounding-box corners for an opening, returned by the legacy procedure
 * {@code frep_map_bounding_values} (out-params {@code p_max_x/p_max_y/p_min_x/p_min_y}, read from
 * {@code OPENING_MAP_IMAGE}). Coordinates are strings, mirroring the legacy {@code MapViewForm} which
 * concatenated them straight into the viewer URL.
 */
public record MapExtent(String maxX, String maxY, String minX, String minY) {

  /** True only when all four corners are present — the proc leaves them null if the opening has no spatial data. */
  public boolean isPresent() {
    return StringUtils.isNoneBlank(maxX, maxY, minX, minY);
  }
}

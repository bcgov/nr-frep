package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * One page of CHR photo metadata. Bytes are never included — each photo is fetched individually from
 * the content endpoint.
 *
 * @param photos     the page's photo metadata, in creation order
 * @param totalCount total photos on the checklist, for the pager
 */
public record PhotoPageResponse(List<Picture> photos, int totalCount) {}

package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * One page of checklist attachment metadata.
 *
 * @param attachments the page's rows, ordered by attachment id
 * @param totalCount  total attachments on the checklist, for the pager
 */
public record AttachmentPageResponse(List<AttachmentRow> attachments, int totalCount) {}

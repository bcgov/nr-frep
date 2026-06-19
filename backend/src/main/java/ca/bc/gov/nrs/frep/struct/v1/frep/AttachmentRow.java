package ca.bc.gov.nrs.frep.struct.v1.frep;

/**
 * Metadata for one checklist attachment (legacy Attachments tab / {@code FREP_CHECKLIST_ATTACHMENTS}
 * GET cursor). The file bytes are fetched separately via the content endpoint.
 */
public record AttachmentRow(
    String checklistAttachmentId,
    String fileName,
    String description,
    String mimeTypeCode,
    String fileSize
) {}

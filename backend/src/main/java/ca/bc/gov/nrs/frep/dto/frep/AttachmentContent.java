package ca.bc.gov.nrs.frep.dto.frep;

/**
 * The downloaded bytes of a checklist attachment (legacy {@code FREP_CHECKLIST_ATTACHMENTS.GET_BLOB}).
 *
 * @param fileName the original file name
 * @param mimeType the stored MIME type, used for the response Content-Type
 * @param data     the file contents
 */
public record AttachmentContent(String fileName, String mimeType, byte[] data) {}

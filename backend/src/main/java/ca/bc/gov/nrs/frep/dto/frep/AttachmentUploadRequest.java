package ca.bc.gov.nrs.frep.dto.frep;

/**
 * Upload payload for a checklist attachment. {@code data} is the file bytes — Jackson decodes the
 * JSON base64 string to {@code byte[]} (mirrors the CHR photo upload flow).
 */
public record AttachmentUploadRequest(
    String fileName,
    String description,
    String contentType,
    byte[] data
) {}

import type { Picture } from '@/types/chrChecklist';

/**
 * Turn a newly-captured photo into a `File` for the multipart photo endpoint.
 *
 * New photos are produced by the Photos tab as a data-URL `code` (the canvas downscale output), and
 * that is also how an offline copy stores them — so the same conversion serves the online upload and
 * the check-in flush. Returns null when the photo carries no new bytes (an already-saved row).
 */
export const pictureToFile = (picture: Picture): File | null => {
  const code = picture.code;
  if (!code) return null;
  const base64 = code.startsWith('data:') ? code.slice(code.indexOf(',') + 1) : code;
  const mime = picture.mimeTypeCode?.includes('image/')
    ? picture.mimeTypeCode
    : `image/${(picture.mimeTypeCode || 'jpeg').toLowerCase()}`;
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return new File([bytes], picture.fileName || 'photo.jpg', { type: mime });
};

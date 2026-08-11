/**
 * Client-side upload size limit, shared by Biodiversity attachments and CHR photos.
 *
 * <p>Mirrors `spring.servlet.multipart.max-file-size` (backend application.yml). That value is
 * sized by **heap**, not by the connector: the ClamAV scan and the storage write each hold the
 * whole file as a `byte[]` on a 400 MB heap running under `-XX:+ExitOnOutOfMemoryError`, so an
 * oversized upload kills the pod rather than failing one request.
 *
 * <p>Three other ceilings sit around it — keep them ordered, or the friendly message below is a
 * lie and the user gets an opaque failure from whichever layer actually cut in first:
 *
 * <pre>
 *   this / max-file-size        15 MB   ← the limit users are told about
 *   max-request-size            17 MB   headroom for the metadata parts
 *   Coraza SecRequestBodyLimit  17 MB   MUST stay >= max-request-size (frontend/coraza.conf)
 *   undertow max-http-post-size 50 MB
 * </pre>
 *
 * <p>Coraza previously sat at 12.5 MB — below this limit — so 13–15 MB files passed the check here
 * and were then rejected by the WAF mid-upload.
 */
export const MAX_UPLOAD_MB = 15;
export const MAX_UPLOAD_BYTES = MAX_UPLOAD_MB * 1024 * 1024;

/** One decimal place, for size messages: `formatMb(15_500_000)` -> `"14.8"`. */
export const formatMb = (bytes: number): string => (bytes / (1024 * 1024)).toFixed(1);

/**
 * Decoded byte length of a `data:` URL payload — what actually goes on the wire once the base64 is
 * turned back into a file, i.e. ~3/4 of the string's length, not the string's length itself.
 */
export const dataUrlByteLength = (dataUrl: string | null | undefined): number => {
  if (!dataUrl) return 0;
  const comma = dataUrl.indexOf(',');
  const base64 = comma < 0 ? dataUrl : dataUrl.slice(comma + 1);
  const padding = base64.endsWith('==') ? 2 : base64.endsWith('=') ? 1 : 0;
  return Math.max(0, Math.floor((base64.length * 3) / 4) - padding);
};

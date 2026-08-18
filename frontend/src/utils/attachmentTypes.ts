import { env } from '@/env';

/**
 * The file types offered in the Biodiversity attachment picker.
 *
 * Configuration, not code: read from `VITE_ATTACHMENT_TYPES`, which the deployment fills from the
 * same GitHub variable the backend reads as `ATTACHMENT_ALLOWED_TYPES`. One variable drives both, so
 * the picker and the server-side allow-list cannot drift. Adding a format is a variable edit plus a
 * redeploy: there is no database change, because FREP no longer validates the type against
 * `THE.MIME_TYPE_CODE`.
 *
 * The value is a comma-separated extension list — `BMP,CSV,PDF`.
 *
 * **There is no in-code default.** The variable is the single source of truth, and it is a required
 * parameter in openshift.deploy.yml, so a deployed environment cannot reach the app without it —
 * `oc process` fails first. If it is nevertheless empty (local dev without the variable set),
 * nothing may be attached: the client enforces the list rather than deferring to the server, which
 * mirrors the backend refusing to start on the same blank value.
 */

/**
 * Parse the configured list into lower-case extensions, sorted for a stable picker and help text.
 * Empty when unconfigured — see {@link isAllowedAttachmentExtension} for what that means.
 */
export const parseAttachmentTypes = (configured: string | undefined): string[] => {
  const parsed = (configured ?? '')
    .split(',')
    .map((entry) => entry.trim().toLowerCase())
    .filter(Boolean);
  return [...new Set(parsed)].sort((a, b) => a.localeCompare(b));
};

export const ALLOWED_ATTACHMENT_EXTENSIONS = parseAttachmentTypes(env.VITE_ATTACHMENT_TYPES);

/**
 * Whether `extension` may be attached. The configured list is enforced here, not merely used to
 * filter the picker — an unconfigured list therefore permits nothing, the same stance the backend
 * takes when the variable is blank. The server re-checks regardless; this exists so the user is
 * told immediately rather than after an upload that cannot succeed.
 */
export const isAllowedAttachmentExtension = (extension: string): boolean =>
  ALLOWED_ATTACHMENT_EXTENSIONS.includes(extension.toLowerCase());

/**
 * The `accept` attribute for the file input, e.g. `.bmp,.csv,.pdf`. `undefined` when unconfigured —
 * there is no way to express "accept nothing" in the attribute, and it is only a picker hint in any
 * case (drag-and-drop bypasses it). {@link isAllowedAttachmentExtension} is what enforces.
 */
export const ALLOWED_ATTACHMENT_ACCEPT =
  ALLOWED_ATTACHMENT_EXTENSIONS.length === 0
    ? undefined
    : ALLOWED_ATTACHMENT_EXTENSIONS.map((e) => `.${e}`).join(',');

/**
 * Extensions a browser can render in an `<img>`, i.e. what can show a thumbnail or open in the
 * preview modal. A rendering capability, NOT an allow-list: every configured type may be uploaded
 * to either protocol, this only decides what gets a picture instead of a type label.
 *
 * TIF/TIFF are deliberately absent though they upload fine — no mainstream browser decodes them, so
 * treating them as previewable meant downloading the whole file (the largest, least compressible
 * type on the list), base64-ing it, and silently falling back to the placeholder anyway. Mirrors
 * `isImage` in RipAttachmentsView, which has always worked this way.
 */
const PREVIEWABLE_EXTENSIONS = new Set(['bmp', 'gif', 'heic', 'jpeg', 'jpg', 'png', 'webp']);

/** Whether a file of this extension can be shown as an image rather than a type placeholder. */
export const isPreviewableExtension = (extension: string | undefined): boolean =>
  PREVIEWABLE_EXTENSIONS.has((extension ?? '').toLowerCase());

/** Whether `fileName`'s extension can be shown as an image. */
export const isPreviewableFile = (fileName: string | undefined): boolean =>
  isPreviewableExtension(fileName?.includes('.') ? fileName.split('.').pop() : undefined);

/**
 * Whether a stored item can be shown as an image, from whatever signals it carries.
 *
 * Tolerant on purpose, mirroring `isImage` in RipAttachmentsView: a record may have a media type, a
 * legacy `image/<code>` value, a file name, or only some of those — an offline copy predating
 * `mediaType`, for instance. Missing a thumbnail because one field was absent is a real bug we hit;
 * falling back to the type placeholder when nothing identifies the file is the safe end state.
 */
export const isPreviewableRecord = (record: {
  code?: string;
  mediaType?: string;
  mimeTypeCode?: string;
  fileName?: string;
}): boolean => {
  // A data: URL states its own type and is what a just-captured photo carries before it is saved —
  // it may have no other field set at all, so read it first.
  if (record.code?.startsWith('data:')) {
    const declaredInUrl = record.code.slice(5, record.code.indexOf(';')).toLowerCase();
    return declaredInUrl.startsWith('image/') && !declaredInUrl.includes('tif');
  }
  const declared = (record.mediaType || record.mimeTypeCode || '').toLowerCase();
  if (declared.includes('tif')) return false;
  if (declared.startsWith('image/')) {
    // A legacy mimeTypeCode is "image/" + the stored code, so "image/pdf" is possible for a
    // non-image saved before mediaType existed; the file name settles it when it can.
    return record.fileName ? isPreviewableFile(record.fileName) : true;
  }
  if (declared) return false;
  return isPreviewableFile(record.fileName);
};

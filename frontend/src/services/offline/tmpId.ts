/**
 * Local ids for rows created on a device before the server has seen them.
 *
 * Its own module, deliberately. Views need to recognise a locally-held row — the attachments tab
 * skips its thumbnail download cap for one, since the bytes are already on the device — and reaching
 * into `bioOfflineRepo` for that dragged the whole offline repository into every view that asked.
 * Two test files mock that module, and both broke on the import alone. This is a naming convention,
 * not repository behaviour, so it lives where anything may depend on it.
 */

export const TMP_ID_PREFIX = 'tmp:';

let tmpCounter = 0;

/** Mint a local id for a row created offline, so views have a stable key before the server sees it. */
export const mintTmpId = (): string => {
  tmpCounter += 1;
  return `${TMP_ID_PREFIX}${Date.now()}-${tmpCounter}`;
};

/**
 * Whether an id was minted here rather than by Oracle. Mirrors the server's own check.
 *
 * NOTE this is also true for a MISSING id. It answers "did the server assign this?", not "is this
 * row new" — asking it the second question is what duplicated every offline-created stratum and plot
 * on edit (see bioFacade.saveBioStratum).
 */
export const isTmpId = (id?: string): boolean => !id || id.startsWith(TMP_ID_PREFIX);

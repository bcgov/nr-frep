import type { FC } from 'react';

/**
 * Collapsed "what can I upload?" disclosure for the attachment/photo upload cards.
 *
 * Mirrors the "How is the MRVA rating determined?" disclosure on Block Summary — a native
 * `<details>`, closed by default, so the answer is one click away without pushing the fields the
 * user actually came for down the page.
 *
 * **Pass the same constants the upload path validates against.** The rules are enforced in
 * `rejectionReason` (Biodiversity) and the drop/size guards (CHR); duplicating them as prose here
 * would drift the moment a format is added, and the help text would then be confidently wrong —
 * which is worse than absent, because the user believes it.
 */
const UploadHelp: FC<{
  /** Per-file size cap in MB. */
  maxMb: number;
  /** Accepted extensions, without the dot — rendered uppercase. */
  formats: readonly string[];
}> = ({ maxMb, formats }) => (
  <details className="attach-help">
    <summary>What files can I upload?</summary>
    <p>
      Maximum <strong>{maxMb} MB</strong> per file.
    </p>
    <p>
      <span className="attach-help__label">Accepted formats:</span>{' '}
      {formats.map((f) => f.toUpperCase()).join(', ')}
    </p>
  </details>
);

export default UploadHelp;

import { WarningFilled } from '@carbon/icons-react';
import { Button } from '@carbon/react';

import type { FC } from 'react';

import './refusedFiles.scss';

export type RefusedFile = {
  /** Stable within this device's copy; identifies the file to discard. */
  key: string;
  fileName?: string;
  reason?: string;
};

type Props = {
  files: RefusedFile[];
  onDiscard: (file: RefusedFile) => void;
  busy?: boolean;
};

/**
 * Files the server refused during a sync, each with the reason and its own Discard.
 *
 * Shared by CHR and SLR: both park a refused file rather than dropping it, because the bytes may be
 * field evidence that cannot be re-collected, and both need the user to decide per file before the
 * sync can complete.
 *
 * **Deliberately not a Carbon `InlineNotification`.** That component refuses interactive children —
 * mounting a button inside one logs "component should have no interactive child nodes" and the click
 * silently does nothing. So this reproduces Carbon's warning notification (low-contrast fill,
 * full-height left accent, squared corners matching the app's own override) as one block that can
 * host the controls.
 */
const RefusedFiles: FC<Props> = ({ files, onDiscard, busy }) => {
  if (files.length === 0) return null;
  return (
    <section className="offline-refused" aria-labelledby="offline-refused-title">
      <WarningFilled size={20} className="offline-refused__icon" />
      <div>
        <p className="offline-refused__lead">
          <strong id="offline-refused-title">Some files were refused</strong> The server refused
          these files. Review them, then sync again.
        </p>
        {/* Named per file rather than "3 files failed": the user has to decide about each one. */}
        <ul className="offline-refused__list">
          {files.map((file) => (
            <li key={file.key} className="offline-refused__row">
              <strong>{file.fileName ?? 'File'}</strong>
              <span className="offline-refused__reason">{file.reason ?? 'refused'}</span>
              <Button
                kind="danger--tertiary"
                size="sm"
                onClick={() => onDiscard(file)}
                disabled={busy}
              >
                Discard
              </Button>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
};

export default RefusedFiles;

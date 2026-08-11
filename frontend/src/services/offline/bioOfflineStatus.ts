import { isStale, type StalenessVerdict } from '@/services/offline/staleness';

import type { BioSyncState } from '@/services/offline/bioDb';

/** Carbon `Tag` types used by the offline list. */
export type OfflineTagType = 'red' | 'magenta' | 'green' | 'blue' | 'cool-gray';

export type OfflineRowStatus = {
  label: string;
  tag: OfflineTagType;
  /** Longer explanation for the row, when the label alone would leave the user guessing. */
  detail?: string;
};

export type BioRowInputs = {
  syncState: BioSyncState;
  /** Undefined while the server probe is still in flight. */
  verdict?: StalenessVerdict;
  pendingAttachments: number;
  rejectedAttachments: number;
  conflictReason?: string;
};

/**
 * The Status cell for one offline SLR copy.
 *
 * Ordered by what the user must act on first, which is not the same as the order things happen in:
 *
 * 1. **Rejected files** outrank everything, including staleness. A rejected file is the only state
 *    holding bytes that exist nowhere else and that the user must consciously decide to discard.
 *    Burying it under "Out of date" is how field evidence gets thrown away by accident.
 * 2. **Server staleness** next — a superseded copy cannot be checked in at all, so its local sync
 *    state is moot.
 * 3. **Conflict**, then in-flight sync states, then local edits.
 *
 * CHR needs only dirty/clean here; SLR's check-in is a drainable queue plus a graph POST, so a row
 * can be legitimately mid-sync and the list has to say which part.
 */
export const bioRowStatus = ({
  syncState,
  verdict,
  pendingAttachments,
  rejectedAttachments,
  conflictReason,
}: BioRowInputs): OfflineRowStatus => {
  if (rejectedAttachments > 0) {
    return {
      label: rejectedAttachments === 1 ? '1 file rejected' : `${rejectedAttachments} files rejected`,
      tag: 'red',
      detail: 'The server refused these files. Review them before removing this copy.',
    };
  }
  if (verdict && isStale(verdict)) {
    return {
      label: 'Out of date',
      tag: 'red',
      detail: "This copy can't be checked in because the checklist changed on the server.",
    };
  }
  if (syncState === 'CONFLICT') {
    return { label: 'Needs attention', tag: 'red', detail: conflictReason };
  }
  if (verdict === 'UNVERIFIED') {
    return { label: 'Unverified', tag: 'cool-gray' };
  }
  if (syncState === 'FLUSHING_ATTACHMENTS') {
    return {
      label: pendingAttachments > 0 ? `Uploading files (${pendingAttachments} left)` : 'Uploading files',
      tag: 'blue',
    };
  }
  if (syncState === 'SYNCING_GRAPH') {
    return { label: 'Checking in', tag: 'blue' };
  }
  if (syncState === 'DIRTY' || pendingAttachments > 0) {
    // Pending attachments count as unsynced even on a CLEAN copy: a file captured offline is a local
    // change whether or not any field was edited, and its bytes are held nowhere else.
    return { label: 'Unsynced changes', tag: 'magenta' };
  }
  return { label: 'Synced', tag: 'green' };
};

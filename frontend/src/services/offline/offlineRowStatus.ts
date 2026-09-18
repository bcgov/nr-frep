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
/** Shown as the row's caveat when the server could not be reached to check staleness. */
const UNVERIFIED_OFFLINE = 'Not checked against the server — this device is offline.';

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
    //
    // Ranked ABOVE `UNVERIFIED`. Offline, every row is unverified, so leading with it turned the
    // whole column into one repeated word and buried the one thing the device does know for
    // certain: which copies hold work that has not reached the server. That is the actionable fact
    // in the field, where this list is actually read. Unverified stays on the row as a caveat.
    return {
      label: 'Unsynced changes',
      tag: 'magenta',
      detail: verdict === 'UNVERIFIED' ? UNVERIFIED_OFFLINE : undefined,
    };
  }
  // Nothing local to report, so "Unverified" is the honest headline rather than a green "Synced"
  // this device cannot actually stand behind.
  if (verdict === 'UNVERIFIED') return { label: 'Unverified', tag: 'cool-gray' };
  return { label: 'Synced', tag: 'green' };
};

export type ChrRowInputs = {
  /** True when there are local edits not yet uploaded. CHR's check-in is two calls, so one flag says it all. */
  dirty: boolean;
  /** Undefined while the server probe is still in flight. */
  verdict?: StalenessVerdict;
};

/**
 * The Status cell for one offline CHR copy.
 *
 * Same ranking as {@link bioRowStatus} minus the states CHR cannot reach: there is no attachment
 * queue to reject files from and no multi-step sync to be halfway through, so it collapses to
 * staleness → unverified → dirty. Kept beside its SLR sibling because the unified offline list
 * renders both through one `OfflineRowStatus`, and the two must stay legible as a pair.
 */
export const chrRowStatus = ({ dirty, verdict }: ChrRowInputs): OfflineRowStatus => {
  if (verdict && isStale(verdict)) {
    return {
      label: 'Out of date',
      tag: 'red',
      detail: "This copy can't be checked in because the checklist changed on the server.",
    };
  }
  // Same ranking as bioRowStatus: local work outranks "we could not check".
  if (dirty) {
    return {
      label: 'Unsynced changes',
      tag: 'magenta',
      detail: verdict === 'UNVERIFIED' ? UNVERIFIED_OFFLINE : undefined,
    };
  }
  if (verdict === 'UNVERIFIED') return { label: 'Unverified', tag: 'cool-gray' };
  return { label: 'Synced', tag: 'green' };
};

import API from '@/services/APIs';
import { BIO_SNAPSHOT_SCHEMA_VERSION, bioOfflineRepo } from '@/services/offline/bioOfflineRepo';

import type { BioAttachmentOp, OfflineBioChecklist } from '@/services/offline/bioDb';
import type { BioSnapshotUpload } from '@/types/protocolChecklist';

/**
 * Check an offline SLR copy back in.
 *
 * ```
 * drain attachment queue (while RDO, token-proved)  →  POST /snapshot (graph, RDO → ACT)
 * ```
 *
 * **Attachments first, and the graph last.** A failure in the queue then aborts with the local copy
 * intact and the checkout still held, rather than failing *after* the graph has been accepted and the
 * checkout released — at which point the device would be holding files it can never send. It also
 * means submit validation, which reads attachment rows, sees files that actually exist.
 *
 * This works only because the leaf endpoints accept `RDO` with a matching token: the flip to `ACT`
 * happens inside the graph POST, so every attachment call above it runs against a checked-out
 * checklist.
 */

export type CheckInProgress = {
  phase: 'attachments' | 'graph' | 'done';
  done?: number;
  total?: number;
};

export type CheckInOptions = {
  onProgress?: (progress: CheckInProgress) => void;
};

/** A file the server refused, surfaced per file rather than as one opaque failure. */
export type RejectedFile = { fileName?: string; reason: string };

export class CheckInBlockedError extends Error {
  readonly rejected: RejectedFile[];

  constructor(message: string, rejected: RejectedFile[] = []) {
    super(message);
    this.name = 'CheckInBlockedError';
    this.rejected = rejected;
  }
}

/**
 * Whether the server will refuse this forever, or might accept it on a retry.
 *
 * The distinction decides whether a file is parked for the user to deal with or simply left queued:
 * a virus hit, an oversize file or an unsupported type will fail identically next time, whereas a
 * dropped connection or a 5xx will not. Treating a transient failure as permanent would ask the user
 * to discard evidence over a flaky signal.
 */
const isPermanentRejection = (err: unknown): boolean => {
  const status = (err as { status?: number })?.status;
  if (status === undefined) return false; // network error — retryable
  if (status === 408 || status === 429) return false; // timeout / throttled — retryable
  return status >= 400 && status < 500;
};

const errorMessage = (err: unknown): string => {
  const body = (err as { body?: { message?: string } })?.body;
  if (body?.message) return body.message;
  return err instanceof Error ? err.message : 'Unknown error';
};

/** Send one queued op, marking it landed before the next one starts. */
const flushOne = async (
  record: OfflineBioChecklist,
  op: BioAttachmentOp,
): Promise<void> => {
  const guid = record.deviceCheckoutGuid;
  if (op.kind === 'ADD') {
    if (!op.blob) return; // nothing to send; treat as landed so the queue can drain
    const file = new File([op.blob], op.fileName ?? 'attachment', { type: op.blob.type });
    await API.protocolChecklist.uploadAttachment(
      'bio', record.checklistId, file, op.description, guid);
  } else if (op.attachmentId) {
    await API.protocolChecklist.deleteAttachment(
      'bio', record.checklistId, op.attachmentId, guid);
  }
  // Marked *before* the next op starts. The upload returns 204 with no id, so a resumed flush can
  // only tell what already went by this marker — without it a retry re-posts everything it had sent.
  if (op.id !== undefined) await bioOfflineRepo.markAttachmentSynced(op.id);
};

export const checkInBioChecklist = async (
  checklistId: string,
  options: CheckInOptions = {},
): Promise<void> => {
  const record = await bioOfflineRepo.load(checklistId);
  if (!record) throw new Error(`No offline checklist found for ${checklistId}.`);

  // ── 1. Drain the attachment queue ──────────────────────────────────
  await bioOfflineRepo.setSyncState(checklistId, 'FLUSHING_ATTACHMENTS');
  const pending = await bioOfflineRepo.pendingAttachmentOps(checklistId);
  options.onProgress?.({ phase: 'attachments', done: 0, total: pending.length });

  const rejected: RejectedFile[] = [];
  let done = 0;
  for (const op of pending) {
    try {
      await flushOne(record, op);
    } catch (err) {
      if (isPermanentRejection(err) && op.id !== undefined) {
        // Parked, never dropped: the bytes may be field evidence that cannot be re-collected, so
        // discarding is the user's decision. It leaves the pending queue without leaving the device.
        await bioOfflineRepo.markAttachmentRejected(op.id, errorMessage(err));
        rejected.push({ fileName: op.fileName, reason: errorMessage(err) });
        continue;
      }
      // Transient: leave it queued and stop. The checkout is still held and the copy is intact, so
      // the same loop resumes cleanly once the connection is back.
      await bioOfflineRepo.setSyncState(checklistId, 'CONFLICT', errorMessage(err));
      throw new CheckInBlockedError(
        `Could not upload ${op.fileName ?? 'a file'}: ${errorMessage(err)}`, rejected);
    }
    done += 1;
    options.onProgress?.({ phase: 'attachments', done, total: pending.length });
  }

  if (rejected.length > 0) {
    // Stop before the graph. Letting it through would release the checkout and drop the local copy
    // while those bytes are still only on this device — the user must decide to discard them first.
    await bioOfflineRepo.setSyncState(
      checklistId, 'CONFLICT',
      `${rejected.length} file(s) were refused by the server. Review them, then check in again.`);
    throw new CheckInBlockedError(
      'Some files could not be uploaded. Review them before checking in.', rejected);
  }

  // ── 2. Post the graph ──────────────────────────────────────────────
  await bioOfflineRepo.setSyncState(checklistId, 'SYNCING_GRAPH');
  options.onProgress?.({ phase: 'graph' });

  const current = await bioOfflineRepo.load(checklistId);
  if (!current) throw new Error(`No offline checklist found for ${checklistId}.`);

  const upload: BioSnapshotUpload = {
    schemaVersion: current.schemaVersion || BIO_SNAPSHOT_SCHEMA_VERSION,
    deviceCheckoutGuid: current.deviceCheckoutGuid,
    opening: current.snapshot.opening,
    notes: current.snapshot.notes,
    strata: current.snapshot.strata,
    tombstones: current.tombstones,
  };

  try {
    await API.protocolChecklist.uploadSnapshot(checklistId, upload);
  } catch (err) {
    await bioOfflineRepo.setSyncState(checklistId, 'CONFLICT', errorMessage(err));
    throw new CheckInBlockedError(errorMessage(err));
  }

  // ── 3. Done — drop the local copy ──────────────────────────────────
  // The check-in cleared the server's token, so this copy could never be checked in again. Keeping
  // it would leave a dead record that looks editable and, since the save response carries no graph,
  // one that appears to have lost its contents.
  await bioOfflineRepo.setSyncState(checklistId, 'SYNCED');
  await bioOfflineRepo.remove(checklistId);
  options.onProgress?.({ phase: 'done' });
};

/**
 * Copies that stopped mid-sync and can be resumed.
 *
 * Needed because a check-in can be interrupted by more than a crash: `ensureSessionFresh` hard-
 * redirects to the IDIR login when the refresh token has died, which it will have after hours
 * offline — taking the whole page with it. The queue and the sync state are in IndexedDB, so the
 * intent survives; resuming is just re-running the same loop, and the per-op markers mean nothing
 * already sent goes twice.
 */
export const resumableCheckIns = async (): Promise<OfflineBioChecklist[]> => {
  const records = await bioOfflineRepo.listOffline();
  return records.filter(
    (record) => record.syncState === 'FLUSHING_ATTACHMENTS' || record.syncState === 'SYNCING_GRAPH');
};

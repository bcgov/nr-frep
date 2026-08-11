import {
  bioDb,
  type BioAttachmentOp,
  type BioSyncState,
  type OfflineBioChecklist,
} from '@/services/offline/bioDb';

import type { BioSnapshot, BioTombstone } from '@/types/protocolChecklist';

/**
 * Local-first persistence for SLR checklists.
 *
 * Deliberately storage-only. The network halves — downloading attachment bytes at take-offline, and
 * draining the queue then POSTing the graph at check-in — land in FE-6/FE-7; this module owns what
 * the device holds and how it moves through the sync states.
 *
 * The rule this file exists to enforce: **the local record is the only copy of anything captured
 * offline until check-in sends it.** CHR lost every offline-captured photo to a save path that
 * stripped them, so nothing here accepts a "partial" write that could drop a field.
 */

/** The snapshot shape this build writes and can read back. Must track the backend's. */
export const BIO_SNAPSHOT_SCHEMA_VERSION = '1';

/** Local ids for rows created offline. Oracle assigns the real ones at check-in. */
export const TMP_ID_PREFIX = 'tmp:';

let tmpCounter = 0;

/** Mint a local id for a row created offline, so views have a stable key before the server sees it. */
export const mintTmpId = (): string => {
  tmpCounter += 1;
  return `${TMP_ID_PREFIX}${Date.now()}-${tmpCounter}`;
};

/** Whether an id was minted here rather than by Oracle. Mirrors the server's own check. */
export const isTmpId = (id?: string): boolean => !id || id.startsWith(TMP_ID_PREFIX);

/** Union of tombstones, so an earlier save's deletions survive a later one. */
const mergeTombstones = (
  existing: BioTombstone[] = [],
  added: BioTombstone[] = [],
): BioTombstone[] => {
  const byKey = new Map<string, BioTombstone>();
  for (const tombstone of [...existing, ...added]) {
    // A tmp: row has no server id to delete — it is simply dropped from the graph client-side.
    if (isTmpId(tombstone.id)) continue;
    byKey.set(`${tombstone.entity}:${tombstone.id}`, tombstone);
  }
  return [...byKey.values()];
};

export const bioOfflineRepo = {
  load(checklistId: string): Promise<OfflineBioChecklist | undefined> {
    return bioDb.bioChecklists.get(checklistId);
  },

  listOffline(): Promise<OfflineBioChecklist[]> {
    return bioDb.bioChecklists.orderBy('updatedAt').reverse().toArray();
  },

  /**
   * Store a freshly pulled snapshot and its checkout token.
   *
   * Called *after* both the snapshot read and the `/offline` POST have succeeded — reads first,
   * checkout last, so an abandoned download leaves nothing behind on the device or the server.
   */
  async store(
    snapshot: BioSnapshot,
    deviceCheckoutGuid?: string,
    appBuildId?: string,
  ): Promise<OfflineBioChecklist> {
    const record: OfflineBioChecklist = {
      checklistId: snapshot.checklistId,
      snapshot,
      syncState: 'CLEAN',
      schemaVersion: snapshot.schemaVersion ?? BIO_SNAPSHOT_SCHEMA_VERSION,
      appBuildId,
      deviceCheckoutGuid,
      tombstones: [],
      updatedAt: Date.now(),
    };
    await bioDb.bioChecklists.put(record);
    return record;
  },

  /**
   * Persist local edits. Keeps the checkout token and accumulates tombstones.
   *
   * Takes the **whole** snapshot, never a subset: the local record is the only copy of anything
   * captured offline, so a caller that passed a trimmed graph would destroy the rest.
   */
  async saveLocal(
    checklistId: string,
    snapshot: BioSnapshot,
    tombstones: BioTombstone[] = [],
  ): Promise<OfflineBioChecklist> {
    const existing = await bioDb.bioChecklists.get(checklistId);
    if (!existing) {
      throw new Error(`No offline checklist found for ${checklistId}.`);
    }
    const updated: OfflineBioChecklist = {
      ...existing,
      snapshot,
      // Editing a copy that had stopped in CONFLICT does not clear the conflict — only a successful
      // sync or an explicit discard does.
      syncState: existing.syncState === 'CONFLICT' ? 'CONFLICT' : 'DIRTY',
      tombstones: mergeTombstones(existing.tombstones, tombstones),
      updatedAt: Date.now(),
    };
    await bioDb.bioChecklists.put(updated);
    return updated;
  },

  /** Move a copy through the sync states. `CONFLICT` carries the reason for the offline list. */
  async setSyncState(
    checklistId: string,
    syncState: BioSyncState,
    conflictReason?: string,
  ): Promise<void> {
    const existing = await bioDb.bioChecklists.get(checklistId);
    if (!existing) return;
    await bioDb.bioChecklists.put({
      ...existing,
      syncState,
      conflictReason: syncState === 'CONFLICT' ? conflictReason : undefined,
      updatedAt: Date.now(),
    });
  },

  /** Clear the tombstone list — only after the server has accepted them. */
  async clearTombstones(checklistId: string): Promise<void> {
    const existing = await bioDb.bioChecklists.get(checklistId);
    if (!existing) return;
    await bioDb.bioChecklists.put({ ...existing, tombstones: [], updatedAt: Date.now() });
  },

  // ── Attachment queue ─────────────────────────────────────────────────

  /** Queue a file captured offline. The blob here is the only copy until it is flushed. */
  async queueAttachmentAdd(
    checklistId: string,
    blob: Blob,
    fileName: string,
    description?: string,
  ): Promise<number> {
    return bioDb.bioAttachmentQueue.add({
      checklistId,
      kind: 'ADD',
      blob,
      fileName,
      description,
      queuedAt: Date.now(),
    });
  },

  /**
   * Queue removal of an attachment that **exists on the server** — it has an id, so the deletion has
   * to be replayed.
   *
   * Removing a file that was captured offline and not yet flushed is a different operation: it has
   * no server id to delete, so the caller drops its queued ADD via {@link discardAttachmentOp}
   * instead, and the pair cancels locally rather than becoming an upload followed by a delete.
   */
  async queueAttachmentDelete(checklistId: string, attachmentId: string): Promise<void> {
    await bioDb.bioAttachmentQueue.add({
      checklistId,
      kind: 'DELETE',
      attachmentId,
      queuedAt: Date.now(),
    });
  },

  /** Ops still to send, oldest first. Excludes anything already flushed or parked as rejected. */
  async pendingAttachmentOps(checklistId: string): Promise<BioAttachmentOp[]> {
    const ops = await bioDb.bioAttachmentQueue.where('checklistId').equals(checklistId).toArray();
    return ops
      .filter((op) => !op.syncedAt && !op.rejectedReason)
      .sort((a, b) => a.queuedAt - b.queuedAt);
  },

  /**
   * Mark an op as landed.
   *
   * Written *before* the next op starts: the upload returns 204 with no id, so a resumed flush can
   * only tell what already went by this marker. Without it a retry re-posts everything it had
   * already sent.
   */
  async markAttachmentSynced(opId: number): Promise<void> {
    await bioDb.bioAttachmentQueue.update(opId, { syncedAt: Date.now() });
  },

  /**
   * Park a file the server refused (virus, size, type, status guard).
   *
   * It stays in the queue and stays retrievable — never silently dropped. The bytes may be field
   * evidence that cannot be re-collected, so discarding is the user's decision, not ours.
   */
  async markAttachmentRejected(opId: number, reason: string): Promise<void> {
    await bioDb.bioAttachmentQueue.update(opId, { rejectedReason: reason });
  },

  /** Files the server refused, for the per-file outcome list after a sync. */
  async rejectedAttachmentOps(checklistId: string): Promise<BioAttachmentOp[]> {
    const ops = await bioDb.bioAttachmentQueue.where('checklistId').equals(checklistId).toArray();
    return ops.filter((op) => !!op.rejectedReason);
  },

  /** Discard one rejected file, on the user's explicit say-so. */
  async discardAttachmentOp(opId: number): Promise<void> {
    await bioDb.bioAttachmentQueue.delete(opId);
  },

  // ── Removal ──────────────────────────────────────────────────────────

  /** Downloaded bytes for an existing attachment, so it opens with no connectivity. */
  async putAttachmentFile(
    checklistId: string,
    attachmentId: string,
    blob: Blob,
    fileName?: string,
    mimeTypeCode?: string,
  ): Promise<void> {
    await bioDb.bioAttachmentFiles.put({ attachmentId, checklistId, blob, fileName, mimeTypeCode });
  },

  async attachmentFile(attachmentId: string): Promise<Blob | undefined> {
    return (await bioDb.bioAttachmentFiles.get(attachmentId))?.blob;
  },

  /**
   * Drop the local copy and everything held against it.
   *
   * All three tables, always: orphaned queue rows would make a later checkout of the same checklist
   * flush files from a previous trip, and orphaned blobs would hold megabytes of a checklist the
   * device no longer has.
   */
  async remove(checklistId: string): Promise<void> {
    await bioDb.bioChecklists.delete(checklistId);
    const orphanedOps = await bioDb.bioAttachmentQueue
      .where('checklistId')
      .equals(checklistId)
      .primaryKeys();
    await bioDb.bioAttachmentQueue.bulkDelete(orphanedOps);
    const orphanedFiles = await bioDb.bioAttachmentFiles
      .where('checklistId')
      .equals(checklistId)
      .primaryKeys();
    await bioDb.bioAttachmentFiles.bulkDelete(orphanedFiles);
  },
};

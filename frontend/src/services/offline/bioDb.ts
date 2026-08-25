import Dexie, { type Table } from 'dexie';

import type { BecRow, CodeOption } from '@/types/configuration';
import type { BioSnapshot, BioTombstone } from '@/types/protocolChecklist';

/**
 * Where an offline SLR copy is in the check-in sequence.
 *
 * CHR gets away with a `dirty` boolean because its check-in is two calls. SLR's is a queue of
 * attachment uploads followed by a graph POST, and the queue can be partially drained across app
 * restarts — so "has local edits" cannot express where a resumed sync should pick up.
 *
 * ```
 * CLEAN → DIRTY → FLUSHING_ATTACHMENTS → SYNCING_GRAPH → SYNCED → (removable)
 *                      │                       │
 *                      └──── CONFLICT ◄────────┘   (rejected files park here too)
 * ```
 *
 * - `CLEAN` — taken offline, not yet edited.
 * - `DIRTY` — local edits not yet checked in.
 * - `FLUSHING_ATTACHMENTS` — attachment queue draining. A crash here resumes the same loop; the
 *   per-op `syncedAt` markers record what already landed.
 * - `SYNCING_GRAPH` — attachments are all up; the graph POST is in flight.
 * - `SYNCED` — the server has everything; the copy is removable.
 * - `CONFLICT` — the server moved under us, or a file was permanently rejected. Needs the user.
 */
export type BioSyncState =
  | 'CLEAN'
  | 'DIRTY'
  | 'FLUSHING_ATTACHMENTS'
  | 'SYNCING_GRAPH'
  | 'SYNCED'
  | 'CONFLICT';

/** An SLR checklist held on this device for offline editing. */
export type OfflineBioChecklist = {
  checklistId: string;
  /** The graph, as read from the snapshot endpoint and then edited locally. */
  snapshot: BioSnapshot;
  syncState: BioSyncState;
  /**
   * The shape `snapshot` was written in. Stamped separately from the snapshot's own field so a
   * record written by an older build is detectable even if that build's payload is unreadable.
   * A breaking change blocks the copy rather than migrating it — never silently reinterpret a graph.
   */
  schemaVersion: string;
  /** The build that wrote this record, for diagnosing a field report. */
  appBuildId?: string;
  /** Server token from take-offline; every write while checked out must present it. */
  deviceCheckoutGuid?: string;
  /**
   * Rows deleted locally that exist on the server. Accumulates across saves and is cleared only on a
   * successful check-in — the same union-on-save rule CHR uses for `deletedPhotoIds`, generalised.
   *
   * A row created *and* deleted offline never lands here: it has no server id to delete.
   */
  tombstones: BioTombstone[];
  /** Set when the sync stopped and needs the user — shown on the offline list, not just the page. */
  conflictReason?: string;
  updatedAt: number;
};

/** What a queued attachment op is trying to do. */
export type BioAttachmentOpKind = 'ADD' | 'DELETE';

/**
 * One queued attachment operation.
 *
 * Attachments never ride inside the graph payload — files are up to 15 MB with no per-checklist cap.
 * They are flushed as independent, individually retryable multipart calls *before* the graph POST,
 * while the checklist is still checked out.
 */
export type BioAttachmentOp = {
  /** Local id; auto-incremented by Dexie. */
  id?: number;
  checklistId: string;
  kind: BioAttachmentOpKind;
  /** Present for ADD. The only copy of these bytes until the op is flushed. */
  blob?: Blob;
  fileName?: string;
  description?: string;
  /** Present for DELETE: the server attachment id. */
  attachmentId?: string;
  /**
   * When this op landed on the server. The upload endpoint returns 204 with no id, so resumption
   * cannot key on a returned value — the marker is written before the next op starts, and a marked
   * op is skipped rather than retried.
   */
  syncedAt?: number;
  /** Set when the server refused this file (virus, size, type, status). Parked, never dropped. */
  rejectedReason?: string;
  queuedAt: number;
};

/** Which cached reference list a row holds. */
export type BioReferenceKey =
  | 'species'
  | 'wildlifeTreeDecay'
  | 'cwdDecay'
  | 'strataTypes'
  | 'bec';

/**
 * A cached reference list.
 *
 * **Stored app-wide, not per checkout.** These are static code tables keyed to nothing; with several
 * checklists on the device, a per-checklist copy would store the BEC catalogue N times for no
 * benefit — and it is by far the largest item (~2 MB of JSON, roughly 10k rows).
 */
export type BioReferenceList = {
  key: BioReferenceKey;
  /** `CodeOption[]` for the four code lists, `BecRow[]` for the BEC catalogue. */
  rows: CodeOption[] | BecRow[];
  refreshedAt: number;
};

/**
 * An attachment's bytes, downloaded at take-offline so the file can be opened in the field.
 *
 * Separate from {@link BioAttachmentOp}: those are *outgoing* work with a lifecycle (queued →
 * synced/rejected), these are a read-through cache of what the server already has. Conflating them
 * would make "pending ops" include files that were never going anywhere.
 */
export type BioAttachmentFile = {
  /** Server attachment id. */
  attachmentId: string;
  checklistId: string;
  blob: Blob;
  fileName?: string;
  mimeTypeCode?: string;
};

class BioDatabase extends Dexie {
  bioChecklists!: Table<OfflineBioChecklist, string>;
  bioAttachmentQueue!: Table<BioAttachmentOp, number>;
  bioReference!: Table<BioReferenceList, string>;
  bioAttachmentFiles!: Table<BioAttachmentFile, string>;

  constructor() {
    super('frep-bio');
    this.version(1).stores({
      // primary key = checklistId; secondary indexes for the offline list
      bioChecklists: 'checklistId, syncState, updatedAt',
      // auto-increment pk; indexed by checklist so a check-in can take just its own ops in order
      bioAttachmentQueue: '++id, checklistId, syncedAt, queuedAt',
      // Keyed by list name; app-wide, so it survives removing any one offline copy.
      bioReference: 'key, refreshedAt',
      // Downloaded bytes for existing attachments, indexed by checklist so removal can cascade.
      bioAttachmentFiles: 'attachmentId, checklistId',
    });
  }
}

export const bioDb = new BioDatabase();

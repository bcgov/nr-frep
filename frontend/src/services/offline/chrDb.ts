import Dexie, { type Table } from 'dexie';

import type { CheckList } from '@/types/chrChecklist';

/** A CHR checklist persisted locally for offline editing. */
export type OfflineChecklist = {
  checklistId: string;
  checkList: CheckList;
  /** True when there are local edits not yet uploaded to the server. */
  dirty: boolean;
  /** Server-assigned token from "take offline"; validated on upload (optimistic lock). */
  deviceCheckoutGuid?: string;
  revisionCount?: string;
  /**
   * Server ids of photos deleted while offline, queued for the check-in flush.
   *
   * Needed because photos are no longer reconciled by the checklist save: the server used to infer
   * a deletion from the photo's absence in the uploaded document, and now requires an explicit
   * DELETE per photo. Without this list an offline deletion would simply never reach the server.
   */
  deletedPhotoIds?: string[];
  updatedAt: number;
};

class ChrDatabase extends Dexie {
  chrChecklists!: Table<OfflineChecklist, string>;

  constructor() {
    super('frep-chr');
    this.version(1).stores({
      // primary key = checklistId; secondary indexes for listing/filtering
      chrChecklists: 'checklistId, dirty, updatedAt',
    });
    // v2 adds deletedPhotoIds. No index and no migration function needed — the field is optional and
    // absent on existing records, which read back as undefined and are treated as "nothing queued".
    this.version(2).stores({
      chrChecklists: 'checklistId, dirty, updatedAt',
    });
  }
}

export const chrDb = new ChrDatabase();

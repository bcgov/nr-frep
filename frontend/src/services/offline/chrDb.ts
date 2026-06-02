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
  }
}

export const chrDb = new ChrDatabase();

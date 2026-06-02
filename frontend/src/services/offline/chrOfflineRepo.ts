import type { CheckList } from '@/types/chrChecklist';

import API from '@/services/APIs';
import { chrDb, type OfflineChecklist } from '@/services/offline/chrDb';

/**
 * Local-first persistence for CHR checklists, mirroring the legacy "take offline → edit → upload"
 * flow. Local edits live in IndexedDB (Dexie) and are reconciled with the server on upload using
 * the backend's deviceCheckoutGuid + revisionCount optimistic-lock checks.
 */
export const chrOfflineRepo = {
  load(checklistId: string): Promise<OfflineChecklist | undefined> {
    return chrDb.chrChecklists.get(checklistId);
  },

  listOffline(): Promise<OfflineChecklist[]> {
    return chrDb.chrChecklists.orderBy('updatedAt').reverse().toArray();
  },

  /** Pull from the server, mark it checked-out (RDO + deviceCheckoutGuid), and store locally. */
  async takeOffline(checklistId: string): Promise<OfflineChecklist> {
    const checkList = await API.chrChecklist.takeOffline(checklistId);
    const record: OfflineChecklist = {
      checklistId,
      checkList,
      dirty: false,
      deviceCheckoutGuid: checkList.deviceCheckoutGuid,
      revisionCount: checkList.revisionCount,
      updatedAt: Date.now(),
    };
    await chrDb.chrChecklists.put(record);
    return record;
  },

  /** Persist local edits; keeps the checkout token from the original take-offline. */
  async saveLocal(checkList: CheckList): Promise<void> {
    const checklistId = checkList.checklistID;
    if (!checklistId) {
      throw new Error('Cannot save a checklist without a checklistID locally.');
    }
    const existing = await chrDb.chrChecklists.get(checklistId);
    await chrDb.chrChecklists.put({
      checklistId,
      checkList,
      dirty: true,
      deviceCheckoutGuid: existing?.deviceCheckoutGuid ?? checkList.deviceCheckoutGuid,
      revisionCount: checkList.revisionCount ?? existing?.revisionCount,
      updatedAt: Date.now(),
    });
  },

  /**
   * Upload local edits to the server. The backend validates the deviceCheckoutGuid and
   * revisionCount; a mismatch surfaces as an ApiError (caller shows a conflict message and re-pulls).
   * On success the local copy is replaced with the server response and marked clean.
   */
  async upload(checklistId: string): Promise<CheckList> {
    const record = await chrDb.chrChecklists.get(checklistId);
    if (!record) {
      throw new Error(`No offline checklist found for ${checklistId}.`);
    }
    const payload: CheckList = {
      ...record.checkList,
      deviceCheckoutGuid: record.deviceCheckoutGuid,
      revisionCount: record.revisionCount,
    };
    const saved = await API.chrChecklist.save(payload);
    await chrDb.chrChecklists.put({
      ...record,
      checkList: saved,
      dirty: false,
      revisionCount: saved.revisionCount ?? record.revisionCount,
      updatedAt: Date.now(),
    });
    return saved;
  },

  remove(checklistId: string): Promise<void> {
    return chrDb.chrChecklists.delete(checklistId);
  },
};

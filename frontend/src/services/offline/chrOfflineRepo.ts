import type { CheckList, Picture } from '@/types/chrChecklist';

import API from '@/services/APIs';
import { chrDb, type OfflineChecklist } from '@/services/offline/chrDb';
import { mintTmpId } from '@/services/offline/tmpId';
import { pictureToFile } from '@/utils/pictureFile';

/**
 * Local-first persistence for CHR checklists, mirroring the legacy "take offline → edit → upload"
 * flow. Local edits live in IndexedDB (Dexie) and are reconciled with the server on upload using
 * the backend's deviceCheckoutGuid + revisionCount optimistic-lock checks.
 */
/**
 * Push photos captured or removed offline through the per-photo endpoints.
 *
 * Idempotent by construction: a photo is uploaded only if it has no server id, and each upload
 * clears its local `code` and records the id, so a retry after a partial failure doesn't re-upload
 * what already landed.
 */
/** Photos are pulled a page at a time so one request never carries the whole set. */
const OFFLINE_PHOTO_PAGE_SIZE = 25;

/**
 * Download every photo's bytes and inline them as base64, which is how an offline copy stores them
 * (see the offline decision in the migration plan) and what the check-in flush converts back to
 * multipart. Sequential on purpose: a field device on a poor connection fares better with one
 * request at a time than with N in flight.
 */
const downloadPhotos = async (
  checklistId: string,
  firstPage: Picture[],
  totalCount: number,
): Promise<Picture[]> => {
  const metadata = [...firstPage];
  for (let from = metadata.length; from < totalCount; from += OFFLINE_PHOTO_PAGE_SIZE) {
    const next = await API.chrChecklist.getPhotos(
      checklistId,
      Math.floor(from / OFFLINE_PHOTO_PAGE_SIZE),
      OFFLINE_PHOTO_PAGE_SIZE,
    );
    metadata.push(...next.photos);
  }

  const withBytes: Picture[] = [];
  for (const picture of metadata) {
    if (!picture.id) continue;
    const blob = await API.chrChecklist.getPhotoContent(checklistId, picture.id);
    withBytes.push({ ...picture, code: await blobToBase64(blob) });
  }
  return withBytes;
};

/** Raw base64 (no data: prefix) — the shape `pictureToFile` reads back at check-in. */
const blobToBase64 = (blob: Blob): Promise<string> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result);
      resolve(result.slice(result.indexOf(',') + 1));
    };
    reader.onerror = () => reject(reader.error ?? new Error('Could not read photo'));
    reader.readAsDataURL(blob);
  });

/**
 * Send the photos this device holds, marking each one the moment it lands.
 *
 * **The marking is the point.** `addPhoto` returns 204 with no id, so nothing about a sent photo
 * distinguishes it from an unsent one — and the loop skips only `picture.id || !picture.code`. A
 * flush that failed part-way (one photo refused by the virus scanner, say) therefore left the copy
 * on the device with every already-sent photo still looking unsent, and the next Sync uploaded them
 * again. One duplicate per photo per retry, silently, on the server. SLR's queue solved this with a
 * per-op `syncedAt` marker; this is the same idea written into the local record.
 *
 * The marker is a local `tmp:` id. It never reaches the server — `upload` strips `pictures` from the
 * document payload — and it makes the existing skip guard do the right thing on a retry.
 */
const flushPhotos = async (checklistId: string, record: OfflineChecklist): Promise<void> => {
  // The checklist is still RDO at this point — the RDO → ACT flip happens in the document save
  // below — so every photo call must present the checkout token to prove it owns the checkout.
  const guid = record.deviceCheckoutGuid;
  for (const photoId of record.deletedPhotoIds ?? []) {
    await API.chrChecklist.deletePhoto(checklistId, photoId, guid);
    await chrDb.chrChecklists.update(checklistId, {
      deletedPhotoIds: ((await chrDb.chrChecklists.get(checklistId))?.deletedPhotoIds ?? []).filter(
        (id) => id !== photoId,
      ),
    });
  }

  const pictures = record.checkList.pictures ?? [];
  for (const picture of pictures) {
    if (picture.id || !picture.code) continue; // already on the server, or nothing to send
    const file = pictureToFile(picture);
    if (!file) continue;
    await API.chrChecklist.addPhoto(
      checklistId,
      file,
      picture.description ?? '',
      picture.date,
      guid,
      picture.featureId,
    );
    // Marked before the next photo starts, for the same reason SLR marks its queue ops there: if the
    // next one throws, what already landed has to be recorded, or the retry re-sends it.
    picture.id = mintTmpId();
    await chrDb.chrChecklists.update(checklistId, { checkList: record.checkList });
  }
};

export const chrOfflineRepo = {
  load(checklistId: string): Promise<OfflineChecklist | undefined> {
    return chrDb.chrChecklists.get(checklistId);
  },

  listOffline(): Promise<OfflineChecklist[]> {
    return chrDb.chrChecklists.orderBy('updatedAt').reverse().toArray();
  },

  /**
   * Pull a checklist down for offline use.
   *
   * **Reads first, checkout last.** The checklist GET no longer embeds photo bytes, so each photo is
   * downloaded individually — and all of that happens while the checklist is still `ACT`, before
   * `takeOffline` is called. That ordering matters: the server commits the `ACT → RDO` flip and mints
   * the checkout token *before* its response reaches the client, so anything that fails after that
   * point leaves the checklist checked out with no local copy — and the client never received the
   * token, so only an admin can clear it. Downloading first means a failed download costs nothing.
   *
   * The one thing traded away is a small race: another user could add a photo between the download
   * and the checkout, and this copy would miss it. Harmless — photos are independent resources, so
   * it is simply still there at check-in.
   */
  async takeOffline(checklistId: string): Promise<OfflineChecklist> {
    // 1. Everything readable, while still ACT and uncommitted.
    const photoPage = await API.chrChecklist.getPhotos(checklistId, 0, OFFLINE_PHOTO_PAGE_SIZE);
    const photos = await downloadPhotos(checklistId, photoPage.photos, photoPage.totalCount);

    // 2. Only now take the checkout.
    const checkList = await API.chrChecklist.takeOffline(checklistId);
    checkList.pictures = photos;
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

  /**
   * Persist local edits; keeps the checkout token from the original take-offline.
   *
   * `deletedPhotoIds` accumulates across saves — photos removed offline are only really deleted at
   * check-in, and each save must not drop what earlier ones queued.
   */
  async saveLocal(checkList: CheckList, deletedPhotoIds: string[] = []): Promise<void> {
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
      deletedPhotoIds: [...new Set([...(existing?.deletedPhotoIds ?? []), ...deletedPhotoIds])],
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
    // Photos first, document second. They are separate resources now: the checklist save neither
    // creates nor deletes them, so anything captured or removed offline has to be flushed through
    // the photo endpoints before the document lands. Doing it in this order also means a photo
    // failure aborts the check-in with the local copy still intact, rather than after the document
    // has already been accepted and the checkout released.
    await flushPhotos(checklistId, record);

    const payload: CheckList = {
      ...record.checkList,
      // Stripped here, at the wire, and nowhere earlier. The stored copy must keep its photos until
      // flushPhotos above has sent them — the local base64 is the only copy of an offline capture.
      // The save ignores `pictures` anyway, so shipping them would only re-upload every photo's
      // base64 alongside the document.
      pictures: [],
      deviceCheckoutGuid: record.deviceCheckoutGuid,
      revisionCount: record.revisionCount,
    };
    const saved = await API.chrChecklist.save(payload);
    await chrDb.chrChecklists.put({
      ...record,
      checkList: saved,
      dirty: false,
      revisionCount: saved.revisionCount ?? record.revisionCount,
      deletedPhotoIds: [],
      updatedAt: Date.now(),
    });
    return saved;
  },

  remove(checklistId: string): Promise<void> {
    return chrDb.chrChecklists.delete(checklistId);
  },
};

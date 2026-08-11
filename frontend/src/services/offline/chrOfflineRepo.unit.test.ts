import { beforeEach, describe, expect, it, vi } from 'vitest';

import API from '@/services/APIs';
import { chrDb } from '@/services/offline/chrDb';
import { chrOfflineRepo } from '@/services/offline/chrOfflineRepo';

vi.mock('@/services/APIs', () => ({
  default: {
    chrChecklist: {
      takeOffline: vi.fn(),
      save: vi.fn(),
      addPhoto: vi.fn(),
      deletePhoto: vi.fn(),
      getPhotos: vi.fn(),
      getPhotoContent: vi.fn(),
    },
  },
}));

vi.mock('@/services/offline/chrDb', () => ({
  chrDb: {
    chrChecklists: {
      get: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
    },
  },
}));

const table = chrDb.chrChecklists as unknown as {
  get: ReturnType<typeof vi.fn>;
  put: ReturnType<typeof vi.fn>;
  delete: ReturnType<typeof vi.fn>;
};
const api = API.chrChecklist as unknown as {
  takeOffline: ReturnType<typeof vi.fn>;
  save: ReturnType<typeof vi.fn>;
  addPhoto: ReturnType<typeof vi.fn>;
  deletePhoto: ReturnType<typeof vi.fn>;
  getPhotos: ReturnType<typeof vi.fn>;
  getPhotoContent: ReturnType<typeof vi.fn>;
};

/** A 1x1 PNG as the canvas downscale would produce it: a data URL in `code`, no server id yet. */
const newPhoto = (description: string) => ({
  description,
  mimeTypeCode: 'image/png',
  fileName: 'site.png',
  code: 'data:image/png;base64,iVBORw0KGgo=',
});

describe('chrOfflineRepo', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default: a checklist with no photos, so tests that don't care about them stay short.
    api.getPhotos.mockResolvedValue({ photos: [], totalCount: 0 });
  });

  it('takeOffline pulls from the API and stores a clean record with the checkout token', async () => {
    api.takeOffline.mockResolvedValue({
      checklistID: '1001',
      deviceCheckoutGuid: 'guid-1',
      revisionCount: '3',
    });

    const record = await chrOfflineRepo.takeOffline('1001');

    expect(api.takeOffline).toHaveBeenCalledWith('1001');
    expect(record.dirty).toBe(false);
    expect(record.deviceCheckoutGuid).toBe('guid-1');
    expect(table.put).toHaveBeenCalledWith(
      expect.objectContaining({ checklistId: '1001', dirty: false, deviceCheckoutGuid: 'guid-1' }),
    );
  });

  it('saveLocal marks the record dirty and keeps the existing checkout token', async () => {
    table.get.mockResolvedValue({ checklistId: '1001', deviceCheckoutGuid: 'guid-1' });

    await chrOfflineRepo.saveLocal({ checklistID: '1001', firstNationName: 'Edited' });

    expect(table.put).toHaveBeenCalledWith(
      expect.objectContaining({ checklistId: '1001', dirty: true, deviceCheckoutGuid: 'guid-1' }),
    );
  });

  it('upload posts the stored checkout token + revision and clears the dirty flag', async () => {
    table.get.mockResolvedValue({
      checklistId: '1001',
      checkList: { checklistID: '1001', firstNationName: 'Edited' },
      dirty: true,
      deviceCheckoutGuid: 'guid-1',
      revisionCount: '3',
    });
    api.save.mockResolvedValue({ checklistID: '1001', revisionCount: '4' });

    const saved = await chrOfflineRepo.upload('1001');

    expect(api.save).toHaveBeenCalledWith(
      expect.objectContaining({ deviceCheckoutGuid: 'guid-1', revisionCount: '3' }),
    );
    expect(saved.revisionCount).toBe('4');
    expect(table.put).toHaveBeenCalledWith(expect.objectContaining({ dirty: false }));
  });

  it('upload throws when there is no local copy', async () => {
    table.get.mockResolvedValue(undefined);
    await expect(chrOfflineRepo.upload('nope')).rejects.toThrow();
  });

  // Photos are separate resources now: a checklist save neither creates nor deletes them, so
  // anything captured or removed offline has to be flushed through the photo endpoints at check-in
  // or it never reaches the server at all.
  describe('check-in photo flush', () => {
    it('uploads photos captured offline before saving the document', async () => {
      const order: string[] = [];
      api.addPhoto.mockImplementation(() => {
        order.push('addPhoto');
        return Promise.resolve();
      });
      api.save.mockImplementation(() => {
        order.push('save');
        return Promise.resolve({ checklistID: '1', revisionCount: '3' });
      });
      table.get.mockResolvedValue({
        checklistId: '1',
        checkList: { checklistID: '1', pictures: [newPhoto('A new field photo')] },
        dirty: true,
        deviceCheckoutGuid: 'guid',
        revisionCount: '2',
      });

      await chrOfflineRepo.upload('1');

      expect(api.addPhoto).toHaveBeenCalledTimes(1);
      const [checklistId, file, description] = api.addPhoto.mock.calls[0];
      expect(checklistId).toBe('1');
      expect(file).toBeInstanceOf(File);
      expect(description).toBe('A new field photo');
      // The checklist is still RDO during the flush — the RDO → ACT flip happens in the document
      // save that follows — so every photo call must carry the checkout token or the server refuses it.
      expect(api.addPhoto.mock.calls[0][4]).toBe('guid');
      // Order matters: a photo failure must abort before the document lands and the checkout is released.
      expect(order).toEqual(['addPhoto', 'save']);
    });

    it('strips photos from the document payload but only after flushing them', async () => {
      // Both halves matter. The bytes must reach the photo endpoints, and they must NOT ride along
      // in the document save (which ignores `pictures` and would just re-upload every base64).
      const order: string[] = [];
      api.addPhoto.mockImplementation(() => {
        order.push('addPhoto');
        return Promise.resolve();
      });
      api.save.mockImplementation((payload: { pictures?: unknown[] }) => {
        order.push('save');
        expect(payload.pictures).toEqual([]);
        return Promise.resolve({ checklistID: '1', revisionCount: '3' });
      });
      table.get.mockResolvedValue({
        checklistId: '1',
        checkList: { checklistID: '1', pictures: [newPhoto('Captured offline')] },
        dirty: true,
        deviceCheckoutGuid: 'guid',
        revisionCount: '2',
      });

      await chrOfflineRepo.upload('1');

      expect(order).toEqual(['addPhoto', 'save']);
      expect(api.addPhoto).toHaveBeenCalledTimes(1);
    });

    it('carries the feature a photo documents through the flush', async () => {
      // The association is set at upload and never edited, so if the flush drops it there is no
      // later write that could restore it — an offline photo would lose its feature permanently.
      api.save.mockResolvedValue({ checklistID: '1', revisionCount: '3' });
      table.get.mockResolvedValue({
        checklistId: '1',
        checkList: {
          checklistID: '1',
          pictures: [{ ...newPhoto('Photo of feature 3'), featureId: '5001' }],
        },
        dirty: true,
        deviceCheckoutGuid: 'guid',
        revisionCount: '2',
      });

      await chrOfflineRepo.upload('1');

      expect(api.addPhoto.mock.calls[0][5]).toBe('5001');
    });

    it('issues a DELETE for each photo removed offline', async () => {
      api.save.mockResolvedValue({ checklistID: '1', revisionCount: '3' });
      table.get.mockResolvedValue({
        checklistId: '1',
        checkList: { checklistID: '1', pictures: [] },
        dirty: true,
        deletedPhotoIds: ['7', '9'],
        deviceCheckoutGuid: 'guid',
        revisionCount: '2',
      });

      await chrOfflineRepo.upload('1');

      expect(api.deletePhoto.mock.calls.map((c) => c[1])).toEqual(['7', '9']);
      expect(api.deletePhoto.mock.calls.map((c) => c[2])).toEqual(['guid', 'guid']);
    });

    it('does not re-upload photos that already have a server id', async () => {
      // Idempotency for a retry after a partial check-in failure.
      api.save.mockResolvedValue({ checklistID: '1', revisionCount: '3' });
      table.get.mockResolvedValue({
        checklistId: '1',
        checkList: {
          checklistID: '1',
          pictures: [{ ...newPhoto('Already uploaded'), id: '42' }, newPhoto('Still pending')],
        },
        dirty: true,
        revisionCount: '2',
      });

      await chrOfflineRepo.upload('1');

      expect(api.addPhoto).toHaveBeenCalledTimes(1);
      expect(api.addPhoto.mock.calls[0][2]).toBe('Still pending');
    });

    it('clears the queued deletions once the check-in succeeds', async () => {
      api.save.mockResolvedValue({ checklistID: '1', revisionCount: '3' });
      table.get.mockResolvedValue({
        checklistId: '1',
        checkList: { checklistID: '1', pictures: [] },
        dirty: true,
        deletedPhotoIds: ['7'],
        revisionCount: '2',
      });

      await chrOfflineRepo.upload('1');

      expect(table.put.mock.calls[0][0].deletedPhotoIds).toEqual([]);
    });

    it('accumulates deletions across successive offline saves', async () => {
      table.get.mockResolvedValue({
        checklistId: '1',
        checkList: { checklistID: '1' },
        dirty: true,
        deletedPhotoIds: ['7'],
      });

      await chrOfflineRepo.saveLocal({ checklistID: '1' } as never, ['9']);

      expect(table.put.mock.calls[0][0].deletedPhotoIds).toEqual(['7', '9']);
    });
  });

  // Take-offline downloads everything BEFORE taking the checkout. The server commits the ACT → RDO
  // flip and mints the token before its response reaches the client, so a failure after that point
  // strands the checklist: checked out, no local copy, and the client never saw the token — only an
  // admin can clear it. Downloading first means a failed download costs nothing.
  describe('takeOffline ordering', () => {
    it('downloads photos before taking the checkout', async () => {
      const order: string[] = [];
      api.getPhotos.mockImplementation(() => {
        order.push('getPhotos');
        return Promise.resolve({
          photos: [{ id: '7', description: 'A photo', mimeTypeCode: 'image/png' }],
          totalCount: 1,
        });
      });
      api.getPhotoContent.mockImplementation(() => {
        order.push('getPhotoContent');
        return Promise.resolve(new Blob(['x'], { type: 'image/png' }));
      });
      api.takeOffline.mockImplementation(() => {
        order.push('takeOffline');
        return Promise.resolve({ checklistID: '1', deviceCheckoutGuid: 'g', revisionCount: '1' });
      });

      await chrOfflineRepo.takeOffline('1');

      expect(order).toEqual(['getPhotos', 'getPhotoContent', 'takeOffline']);
    });

    it('stores each photo inline as base64, the shape check-in converts back to multipart', async () => {
      api.getPhotos.mockResolvedValue({
        photos: [{ id: '7', description: 'A photo', mimeTypeCode: 'image/png' }],
        totalCount: 1,
      });
      api.getPhotoContent.mockResolvedValue(new Blob(['hello'], { type: 'image/png' }));
      api.takeOffline.mockResolvedValue({
        checklistID: '1',
        deviceCheckoutGuid: 'g',
        revisionCount: '1',
      });

      const record = await chrOfflineRepo.takeOffline('1');

      const stored = record.checkList.pictures ?? [];
      expect(stored).toHaveLength(1);
      expect(stored[0].code).toBe(btoa('hello')); // raw base64, no data: prefix
    });

    it('never takes the checkout when a photo download fails', async () => {
      api.getPhotos.mockResolvedValue({
        photos: [{ id: '7', description: 'A photo', mimeTypeCode: 'image/png' }],
        totalCount: 1,
      });
      api.getPhotoContent.mockRejectedValue(new Error('network died'));

      await expect(chrOfflineRepo.takeOffline('1')).rejects.toThrow('network died');

      expect(api.takeOffline).not.toHaveBeenCalled();
      expect(table.put).not.toHaveBeenCalled();
    });
  });
});

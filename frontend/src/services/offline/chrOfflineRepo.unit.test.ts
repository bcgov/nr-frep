import { beforeEach, describe, expect, it, vi } from 'vitest';

import API from '@/services/APIs';
import { chrDb } from '@/services/offline/chrDb';
import { chrOfflineRepo } from '@/services/offline/chrOfflineRepo';

vi.mock('@/services/APIs', () => ({
  default: {
    chrChecklist: {
      takeOffline: vi.fn(),
      save: vi.fn(),
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
};

describe('chrOfflineRepo', () => {
  beforeEach(() => {
    vi.clearAllMocks();
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
});

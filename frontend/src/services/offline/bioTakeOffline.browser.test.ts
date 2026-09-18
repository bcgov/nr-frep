import { beforeEach, describe, expect, it, vi } from 'vitest';

import API from '@/services/APIs';
import { bioDb } from '@/services/offline/bioDb';
import { bioOfflineRepo } from '@/services/offline/bioOfflineRepo';
import {
  estimateAttachmentBytes,
  TakeOfflineCancelled,
  takeBioChecklistOffline,
  type TakeOfflineProgress,
} from '@/services/offline/bioTakeOffline';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      getSnapshot: vi.fn(),
      getAttachmentContent: vi.fn(),
      takeOffline: vi.fn(),
    },
    configuration: {
      getSpecies: vi.fn(() => Promise.resolve([])),
      getWildlifeTreeDecay: vi.fn(() => Promise.resolve([])),
      getCwdDecay: vi.fn(() => Promise.resolve([])),
      getStrataTypes: vi.fn(() => Promise.resolve([])),
      searchBec: vi.fn(() => Promise.resolve([])),
    },
  },
}));

const api = API.protocolChecklist as unknown as {
  getSnapshot: ReturnType<typeof vi.fn>;
  getAttachmentContent: ReturnType<typeof vi.fn>;
  takeOffline: ReturnType<typeof vi.fn>;
};

const snapshot = (attachments: unknown[] = []) => ({
  schemaVersion: '1',
  checklistId: '9001',
  resourceType: 'SLR',
  statusCode: 'ACT',
  opening: { checklistId: '9001' },
  strata: [],
  attachments,
});

const attachment = (id: string, fileSize = '1000') => ({
  checklistAttachmentId: id,
  fileName: `f${id}.pdf`,
  description: 'a file',
  mimeTypeCode: 'PDF',
  fileSize,
});

describe('takeBioChecklistOffline', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    await bioDb.bioChecklists.clear();
    await bioDb.bioAttachmentFiles.clear();
    await bioDb.bioReference.clear();
    api.getSnapshot.mockResolvedValue(snapshot());
    api.takeOffline.mockResolvedValue({ statusCode: 'RDO', deviceCheckoutGuid: 'guid-1' });
    api.getAttachmentContent.mockResolvedValue({
      fileName: 'f.pdf', mimeType: 'application/pdf', data: btoa('hello'),
    });
  });

  it('claims the checkout only after every read has succeeded', async () => {
    // The ordering guarantee the whole design rests on: an interrupted download must cost nothing.
    const order: string[] = [];
    api.getSnapshot.mockImplementation(() => {
      order.push('snapshot');
      return Promise.resolve(snapshot([attachment('77')]));
    });
    api.getAttachmentContent.mockImplementation(() => {
      order.push('attachment');
      return Promise.resolve({ mimeType: 'application/pdf', data: btoa('x') });
    });
    api.takeOffline.mockImplementation(() => {
      order.push('checkout');
      return Promise.resolve({ deviceCheckoutGuid: 'guid-1' });
    });

    await takeBioChecklistOffline('9001');

    expect(order).toEqual(['snapshot', 'attachment', 'checkout']);
  });

  it('takes no checkout and stores nothing when the download fails', async () => {
    api.getSnapshot.mockResolvedValue(snapshot([attachment('77')]));
    api.getAttachmentContent.mockRejectedValue(new Error('network dropped'));

    await expect(takeBioChecklistOffline('9001')).rejects.toThrow('network dropped');

    expect(api.takeOffline).not.toHaveBeenCalled();
    expect(await bioOfflineRepo.load('9001')).toBeUndefined();
  });

  it('stores the graph and the checkout token once everything lands', async () => {
    const record = await takeBioChecklistOffline('9001');

    expect(record.deviceCheckoutGuid).toBe('guid-1');
    expect(record.syncState).toBe('CLEAN');
    expect((await bioOfflineRepo.load('9001'))?.snapshot.checklistId).toBe('9001');
  });

  it('refreshes the reference cache, which the Bio views cannot work without', async () => {
    await takeBioChecklistOffline('9001');

    // CHR needs none of this — its code lists are hardcoded. SLR's dropdowns come from the server.
    expect(await bioDb.bioReference.count()).toBe(5);
  });

  it('downloads attachment bytes and keeps their real media type', async () => {
    api.getSnapshot.mockResolvedValue(snapshot([attachment('77')]));

    await takeBioChecklistOffline('9001');

    const blob = await bioOfflineRepo.attachmentFile('77');
    expect(await blob?.text()).toBe('hello');
    // A PDF must open as a PDF in the field; the envelope spells this `mimeType`, not `mimeTypeCode`.
    expect(blob?.type).toBe('application/pdf');
  });

  it('reports progress per file so a long download is visible', async () => {
    api.getSnapshot.mockResolvedValue(snapshot([attachment('77'), attachment('78')]));
    const seen: TakeOfflineProgress[] = [];

    await takeBioChecklistOffline('9001', { onProgress: (p) => seen.push(p) });

    expect(seen.map((p) => p.phase)).toContain('reference');
    expect(seen.filter((p) => p.phase === 'attachments').at(-1)).toEqual({
      phase: 'attachments', done: 2, total: 2,
    });
    expect(seen.at(-1)?.phase).toBe('checkout');
  });

  it('refuses an oversized download by default rather than filling the device', async () => {
    // Files run to 15 MB with no per-checklist cap, so this is where a device runs out of room.
    vi.spyOn(navigator.storage, 'estimate').mockResolvedValue({ quota: 1000, usage: 0 });
    api.getSnapshot.mockResolvedValue(snapshot([attachment('77', '900')]));

    await expect(takeBioChecklistOffline('9001')).rejects.toBeInstanceOf(TakeOfflineCancelled);

    expect(api.takeOffline).not.toHaveBeenCalled();
    vi.restoreAllMocks();
  });

  it('continues an oversized download when the user accepts the warning', async () => {
    vi.spyOn(navigator.storage, 'estimate').mockResolvedValue({ quota: 1000, usage: 0 });
    api.getSnapshot.mockResolvedValue(snapshot([attachment('77', '900')]));

    const record = await takeBioChecklistOffline('9001', {
      onQuotaWarning: async () => true,
    });

    expect(record.deviceCheckoutGuid).toBe('guid-1');
    vi.restoreAllMocks();
  });

  it('will not start twice for the same checklist', async () => {
    // A double-tap would otherwise download everything twice and claim two checkouts.
    let release: () => void = () => {};
    api.getSnapshot.mockReturnValue(new Promise((resolve) => {
      release = () => resolve(snapshot());
    }));

    const first = takeBioChecklistOffline('9001');
    await expect(takeBioChecklistOffline('9001')).rejects.toThrow(/already being taken offline/i);

    release();
    await first;
  });
});

describe('estimateAttachmentBytes', () => {
  it('sums the real sizes', () => {
    expect(estimateAttachmentBytes([attachment('1', '100'), attachment('2', '250')])).toBe(350);
  });

  it('treats a missing or unparseable size as zero rather than NaN', () => {
    // fileSize read `0.00` for Biodiversity until PR 3a, and a NaN would poison the whole sum —
    // silently disabling the guardrail rather than tripping it.
    expect(estimateAttachmentBytes([
      { checklistAttachmentId: '1', fileName: 'a.pdf' },
      { checklistAttachmentId: '2', fileName: 'b.pdf', fileSize: 'unknown' },
      { checklistAttachmentId: '3', fileName: 'c.pdf', fileSize: '200' },
    ])).toBe(200);
  });
});

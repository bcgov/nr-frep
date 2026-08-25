import { beforeEach, describe, expect, it, vi } from 'vitest';

import API from '@/services/APIs';
import { bioDb } from '@/services/offline/bioDb';
import {
  checkInBioChecklist,
  CheckInBlockedError,
  resumableCheckIns,
} from '@/services/offline/bioCheckIn';
import { bioOfflineRepo } from '@/services/offline/bioOfflineRepo';

import type { BioSnapshot } from '@/types/protocolChecklist';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      uploadAttachment: vi.fn(),
      deleteAttachment: vi.fn(),
      uploadSnapshot: vi.fn(),
    },
  },
}));

const api = API.protocolChecklist as unknown as {
  uploadAttachment: ReturnType<typeof vi.fn>;
  deleteAttachment: ReturnType<typeof vi.fn>;
  uploadSnapshot: ReturnType<typeof vi.fn>;
};

/** An error shaped like the request layer's, so status-based branching is exercised. */
const httpError = (status: number, message = 'refused') =>
  Object.assign(new Error(message), { status, body: { message } });

const snapshot = (): BioSnapshot => ({
  schemaVersion: '1',
  checklistId: '9001',
  resourceType: 'SLR',
  statusCode: 'RDO',
  opening: { checklistId: '9001', locationDescription: 'km 16' },
  notes: { checklistId: '9001', noteDescription: 'note', revisionCount: '3' },
  strata: [],
  attachments: [],
});

const givenCheckedOut = async () => bioOfflineRepo.store(snapshot(), 'guid-1');

describe('checkInBioChecklist', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    await bioDb.bioChecklists.clear();
    await bioDb.bioAttachmentQueue.clear();
    await bioDb.bioAttachmentFiles.clear();
    api.uploadAttachment.mockResolvedValue(undefined);
    api.deleteAttachment.mockResolvedValue(undefined);
    api.uploadSnapshot.mockResolvedValue({ statusCode: 'ACT' });
  });

  it('drains the attachment queue before posting the graph', async () => {
    // The ordering the whole design rests on: a file failure must abort while the checkout is still
    // held, not after the graph has landed and the checkout has been released.
    await givenCheckedOut();
    await bioOfflineRepo.queueAttachmentAdd('9001', new Blob(['x']), 'map.pdf');
    const order: string[] = [];
    api.uploadAttachment.mockImplementation(() => { order.push('attachment'); return Promise.resolve(); });
    api.uploadSnapshot.mockImplementation(() => { order.push('graph'); return Promise.resolve({}); });

    await checkInBioChecklist('9001');

    expect(order).toEqual(['attachment', 'graph']);
  });

  it('sends the checkout token with every attachment call', async () => {
    // The leaf endpoints accept RDO only with a matching token; without it every upload 403s.
    await givenCheckedOut();
    await bioOfflineRepo.queueAttachmentAdd('9001', new Blob(['x']), 'map.pdf', 'a map');
    await bioOfflineRepo.queueAttachmentDelete('9001', '77');

    await checkInBioChecklist('9001');

    expect(api.uploadAttachment).toHaveBeenCalledWith(
      'bio', '9001', expect.any(File), 'a map', 'guid-1');
    expect(api.deleteAttachment).toHaveBeenCalledWith('bio', '9001', '77', 'guid-1');
  });

  it('posts the graph with the tombstones and schema version', async () => {
    await givenCheckedOut();
    await bioOfflineRepo.saveLocal('9001', snapshot(), [{ entity: 'PLOT', id: '7001', revisionCount: '1' }]);

    await checkInBioChecklist('9001');

    expect(api.uploadSnapshot).toHaveBeenCalledWith('9001', expect.objectContaining({
      schemaVersion: '1',
      deviceCheckoutGuid: 'guid-1',
      tombstones: [{ entity: 'PLOT', id: '7001', revisionCount: '1' }],
    }));
  });

  it('drops the local copy once the graph lands', async () => {
    // The check-in cleared the server token, so this copy could never be checked in again — keeping
    // it would leave a dead record that still looks editable.
    await givenCheckedOut();
    await bioOfflineRepo.queueAttachmentAdd('9001', new Blob(['x']), 'map.pdf');

    await checkInBioChecklist('9001');

    expect(await bioOfflineRepo.load('9001')).toBeUndefined();
    expect(await bioDb.bioAttachmentQueue.count()).toBe(0);
  });

  it('does not re-send a file that already landed when a check-in is resumed', async () => {
    // Idempotency: the upload returns 204 with no id, so the per-op marker is the only record of
    // what went. Without it a resumed flush re-posts everything it had already sent.
    await givenCheckedOut();
    const sent = await bioOfflineRepo.queueAttachmentAdd('9001', new Blob(['a']), 'sent.pdf');
    await bioOfflineRepo.queueAttachmentAdd('9001', new Blob(['b']), 'pending.pdf');
    await bioOfflineRepo.markAttachmentSynced(sent);

    await checkInBioChecklist('9001');

    expect(api.uploadAttachment).toHaveBeenCalledTimes(1);
    expect(api.uploadAttachment.mock.calls[0][2].name).toBe('pending.pdf');
  });

  describe('when a file is permanently refused', () => {
    beforeEach(() => {
      api.uploadAttachment.mockRejectedValue(httpError(422, 'Virus detected'));
    });

    it('parks the file instead of dropping it', async () => {
      await givenCheckedOut();
      await bioOfflineRepo.queueAttachmentAdd('9001', new Blob(['x']), 'virus.pdf');

      await expect(checkInBioChecklist('9001')).rejects.toBeInstanceOf(CheckInBlockedError);

      const rejected = await bioOfflineRepo.rejectedAttachmentOps('9001');
      expect(rejected).toHaveLength(1);
      expect(rejected[0].rejectedReason).toBe('Virus detected');
      // The bytes are still here — they may be field evidence that cannot be re-collected.
      expect(await rejected[0].blob?.text()).toBe('x');
    });

    it('stops before the graph and keeps the copy and the checkout', async () => {
      // Letting the graph through would release the checkout and drop the local copy while those
      // bytes exist only on this device.
      await givenCheckedOut();
      await bioOfflineRepo.queueAttachmentAdd('9001', new Blob(['x']), 'virus.pdf');

      await expect(checkInBioChecklist('9001')).rejects.toThrow(/review them/i);

      expect(api.uploadSnapshot).not.toHaveBeenCalled();
      const record = await bioOfflineRepo.load('9001');
      expect(record?.syncState).toBe('CONFLICT');
      expect(record?.deviceCheckoutGuid).toBe('guid-1');
    });

    it('names the refused files so the user sees which, not just how many', async () => {
      await givenCheckedOut();
      await bioOfflineRepo.queueAttachmentAdd('9001', new Blob(['x']), 'virus.pdf');

      const err = await checkInBioChecklist('9001').then(
        () => null,
        (e: unknown) => e as CheckInBlockedError,
      );

      expect(err?.rejected).toEqual([{ fileName: 'virus.pdf', reason: 'Virus detected' }]);
    });

    it('checks in cleanly once the user discards it', async () => {
      // The escape hatch: a permanently refused file would otherwise block check-in forever.
      await givenCheckedOut();
      await bioOfflineRepo.queueAttachmentAdd('9001', new Blob(['x']), 'virus.pdf');
      await checkInBioChecklist('9001').catch(() => undefined);

      const [rejected] = await bioOfflineRepo.rejectedAttachmentOps('9001');
      await bioOfflineRepo.discardAttachmentOp(rejected.id as number);
      await checkInBioChecklist('9001');

      expect(api.uploadSnapshot).toHaveBeenCalled();
      expect(await bioOfflineRepo.load('9001')).toBeUndefined();
    });
  });

  it('leaves a file queued when the failure is transient', async () => {
    // A dropped connection must not ask the user to discard evidence — it retries.
    await givenCheckedOut();
    await bioOfflineRepo.queueAttachmentAdd('9001', new Blob(['x']), 'map.pdf');
    api.uploadAttachment.mockRejectedValue(new Error('network down'));

    await expect(checkInBioChecklist('9001')).rejects.toBeInstanceOf(CheckInBlockedError);

    expect(await bioOfflineRepo.rejectedAttachmentOps('9001')).toHaveLength(0);
    expect(await bioOfflineRepo.pendingAttachmentOps('9001')).toHaveLength(1);
    expect(api.uploadSnapshot).not.toHaveBeenCalled();
  });

  it('treats a 5xx as transient and a 4xx as permanent', async () => {
    await givenCheckedOut();
    await bioOfflineRepo.queueAttachmentAdd('9001', new Blob(['x']), 'a.pdf');
    api.uploadAttachment.mockRejectedValue(httpError(503, 'unavailable'));

    await expect(checkInBioChecklist('9001')).rejects.toBeInstanceOf(CheckInBlockedError);
    expect(await bioOfflineRepo.rejectedAttachmentOps('9001')).toHaveLength(0);
  });

  it('keeps the copy when the graph POST conflicts', async () => {
    await givenCheckedOut();
    api.uploadSnapshot.mockRejectedValue(httpError(409, 'Re-pull the checklist and try again.'));

    await expect(checkInBioChecklist('9001')).rejects.toThrow(/re-pull/i);

    const record = await bioOfflineRepo.load('9001');
    expect(record?.syncState).toBe('CONFLICT');
    expect(record?.conflictReason).toMatch(/re-pull/i);
  });
});

describe('resumableCheckIns', () => {
  beforeEach(async () => {
    await bioDb.bioChecklists.clear();
  });

  it('finds copies stopped mid-sync', async () => {
    // A check-in can be cut short by the IDIR re-login redirect, which takes the whole page with it.
    // The state is in IndexedDB, so the intent survives and the same loop can resume.
    await bioOfflineRepo.store(snapshot(), 'guid-1');
    await bioOfflineRepo.setSyncState('9001', 'FLUSHING_ATTACHMENTS');

    expect((await resumableCheckIns()).map((r) => r.checklistId)).toEqual(['9001']);
  });

  it('ignores copies that are merely edited or already stuck in conflict', async () => {
    await bioOfflineRepo.store(snapshot(), 'guid-1');
    await bioOfflineRepo.setSyncState('9001', 'DIRTY');

    expect(await resumableCheckIns()).toHaveLength(0);

    await bioOfflineRepo.setSyncState('9001', 'CONFLICT', 'needs the user');
    expect(await resumableCheckIns()).toHaveLength(0);
  });
});

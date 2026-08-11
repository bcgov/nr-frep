import { beforeEach, describe, expect, it } from 'vitest';

import { bioDb } from '@/services/offline/bioDb';
import { bioOfflineRepo, isTmpId, mintTmpId } from '@/services/offline/bioOfflineRepo';

import type { BioSnapshot } from '@/types/protocolChecklist';

/**
 * Runs in real Chromium against real IndexedDB rather than mocking the Dexie table.
 *
 * Deliberate: this module *is* its storage semantics — the tombstone union, the queue filtering, the
 * two-table cascade on remove. Mocking `where().equals().toArray()` would assert the mock, not the
 * behaviour, and every offline defect found in CHR so far has been in exactly this kind of seam.
 */

const snapshot = (checklistId = '9001'): BioSnapshot => ({
  schemaVersion: '1',
  checklistId,
  resourceType: 'SLR',
  statusCode: 'ACT',
  opening: { checklistId, locationDescription: 'original' },
  notes: { checklistId, noteDescription: 'note', revisionCount: '3' },
  strata: [],
  attachments: [],
});

describe('bioOfflineRepo', () => {
  beforeEach(async () => {
    await bioDb.bioChecklists.clear();
    await bioDb.bioAttachmentQueue.clear();
  });

  it('stores a pulled snapshot as CLEAN with its checkout token', async () => {
    const record = await bioOfflineRepo.store(snapshot(), 'guid-1', 'build-abc');

    expect(record.syncState).toBe('CLEAN');
    expect(record.deviceCheckoutGuid).toBe('guid-1');
    expect(record.schemaVersion).toBe('1');
    expect(record.appBuildId).toBe('build-abc');
    expect((await bioOfflineRepo.load('9001'))?.snapshot.opening.locationDescription)
      .toBe('original');
  });

  it('a local edit moves CLEAN to DIRTY and keeps the token', async () => {
    await bioOfflineRepo.store(snapshot(), 'guid-1');

    const edited = { ...snapshot(), opening: { checklistId: '9001', locationDescription: 'edited' } };
    const saved = await bioOfflineRepo.saveLocal('9001', edited);

    expect(saved.syncState).toBe('DIRTY');
    expect(saved.deviceCheckoutGuid).toBe('guid-1');
    expect(saved.snapshot.opening.locationDescription).toBe('edited');
  });

  it('accumulates tombstones across saves instead of replacing them', async () => {
    // The CHR analogue (deletedPhotoIds) had to union for the same reason: each save must not drop
    // what an earlier one queued, or a deletion made in the field never reaches the server.
    await bioOfflineRepo.store(snapshot(), 'guid-1');

    await bioOfflineRepo.saveLocal('9001', snapshot(), [
      { entity: 'PLOT', id: '7001', revisionCount: '1' },
    ]);
    const second = await bioOfflineRepo.saveLocal('9001', snapshot(), [
      { entity: 'STRATUM', id: '5001', revisionCount: '2' },
    ]);

    expect(second.tombstones).toHaveLength(2);
    expect(second.tombstones.map((t) => t.id).sort()).toEqual(['5001', '7001']);
  });

  it('never tombstones a row that was created offline', async () => {
    // A tmp: row has no server id to delete — it is dropped from the graph client-side. Sending one
    // would make the check-in fail on a row the server has never heard of.
    await bioOfflineRepo.store(snapshot(), 'guid-1');

    const saved = await bioOfflineRepo.saveLocal('9001', snapshot(), [
      { entity: 'STRATUM', id: mintTmpId(), revisionCount: undefined },
      { entity: 'PLOT', id: '7001', revisionCount: '1' },
    ]);

    expect(saved.tombstones).toEqual([{ entity: 'PLOT', id: '7001', revisionCount: '1' }]);
  });

  it('deduplicates a row tombstoned twice', async () => {
    await bioOfflineRepo.store(snapshot(), 'guid-1');
    await bioOfflineRepo.saveLocal('9001', snapshot(), [{ entity: 'PLOT', id: '7001' }]);
    const again = await bioOfflineRepo.saveLocal('9001', snapshot(), [
      { entity: 'PLOT', id: '7001' },
    ]);

    expect(again.tombstones).toHaveLength(1);
  });

  it('editing a copy stuck in CONFLICT does not clear the conflict', async () => {
    // Only a successful sync or an explicit discard resolves it; otherwise a user could edit their
    // way out of a warning that the server has moved under them.
    await bioOfflineRepo.store(snapshot(), 'guid-1');
    await bioOfflineRepo.setSyncState('9001', 'CONFLICT', 'Reclaimed on the server');

    const saved = await bioOfflineRepo.saveLocal('9001', snapshot());

    expect(saved.syncState).toBe('CONFLICT');
  });

  it('records a conflict reason and clears it on leaving CONFLICT', async () => {
    await bioOfflineRepo.store(snapshot(), 'guid-1');

    await bioOfflineRepo.setSyncState('9001', 'CONFLICT', 'Submitted elsewhere');
    expect((await bioOfflineRepo.load('9001'))?.conflictReason).toBe('Submitted elsewhere');

    await bioOfflineRepo.setSyncState('9001', 'SYNCING_GRAPH');
    expect((await bioOfflineRepo.load('9001'))?.conflictReason).toBeUndefined();
  });

  describe('attachment queue', () => {
    it('queues a captured file and returns it as pending', async () => {
      await bioOfflineRepo.store(snapshot(), 'guid-1');
      await bioOfflineRepo.queueAttachmentAdd(
        '9001', new Blob(['x'], { type: 'application/pdf' }), 'map.pdf', 'a map');

      const pending = await bioOfflineRepo.pendingAttachmentOps('9001');
      expect(pending).toHaveLength(1);
      expect(pending[0].kind).toBe('ADD');
      expect(pending[0].fileName).toBe('map.pdf');
      expect(await pending[0].blob?.text()).toBe('x');
    });

    it('a synced op is no longer pending', async () => {
      // The upload returns 204 with no id, so a resumed flush can only tell what already went by
      // this marker. Without it a retry re-posts everything it had already sent.
      await bioOfflineRepo.store(snapshot(), 'guid-1');
      const opId = await bioOfflineRepo.queueAttachmentAdd(
        '9001', new Blob(['x']), 'map.pdf');

      await bioOfflineRepo.markAttachmentSynced(opId);

      expect(await bioOfflineRepo.pendingAttachmentOps('9001')).toHaveLength(0);
    });

    it('a rejected file is parked, not dropped, and stays retrievable', async () => {
      // The bytes may be field evidence that cannot be re-collected, so discarding is the user's
      // decision. It must leave the pending queue without leaving the device.
      await bioOfflineRepo.store(snapshot(), 'guid-1');
      const opId = await bioOfflineRepo.queueAttachmentAdd(
        '9001', new Blob(['x']), 'virus.pdf');

      await bioOfflineRepo.markAttachmentRejected(opId, 'Virus detected');

      expect(await bioOfflineRepo.pendingAttachmentOps('9001')).toHaveLength(0);
      const rejected = await bioOfflineRepo.rejectedAttachmentOps('9001');
      expect(rejected).toHaveLength(1);
      expect(rejected[0].rejectedReason).toBe('Virus detected');
      expect(await rejected[0].blob?.text()).toBe('x');
    });

    it('only discards a rejected file when told to', async () => {
      await bioOfflineRepo.store(snapshot(), 'guid-1');
      const opId = await bioOfflineRepo.queueAttachmentAdd('9001', new Blob(['x']), 'v.pdf');
      await bioOfflineRepo.markAttachmentRejected(opId, 'Too large');

      await bioOfflineRepo.discardAttachmentOp(opId);

      expect(await bioOfflineRepo.rejectedAttachmentOps('9001')).toHaveLength(0);
    });

    it('queues a DELETE for a file that exists on the server', async () => {
      await bioOfflineRepo.store(snapshot(), 'guid-1');
      await bioOfflineRepo.queueAttachmentDelete('9001', '77');

      const pending = await bioOfflineRepo.pendingAttachmentOps('9001');
      expect(pending).toHaveLength(1);
      expect(pending[0].kind).toBe('DELETE');
      expect(pending[0].attachmentId).toBe('77');
    });

    it('cancels a not-yet-flushed capture locally instead of queueing a DELETE', async () => {
      // A file added and removed offline has no server id, so there is nothing to replay — dropping
      // its queued ADD is the whole operation. Queueing a DELETE for it would fail at check-in
      // against an attachment the server has never heard of.
      await bioOfflineRepo.store(snapshot(), 'guid-1');
      const opId = await bioOfflineRepo.queueAttachmentAdd('9001', new Blob(['x']), 'oops.pdf');

      await bioOfflineRepo.discardAttachmentOp(opId);

      expect(await bioOfflineRepo.pendingAttachmentOps('9001')).toHaveLength(0);
      expect(await bioDb.bioAttachmentQueue.toArray()).toHaveLength(0);
    });

    it('returns pending ops oldest first', async () => {
      await bioOfflineRepo.store(snapshot(), 'guid-1');
      await bioOfflineRepo.queueAttachmentAdd('9001', new Blob(['a']), 'a.pdf');
      await bioOfflineRepo.queueAttachmentAdd('9001', new Blob(['b']), 'b.pdf');

      const pending = await bioOfflineRepo.pendingAttachmentOps('9001');
      expect(pending.map((op) => op.fileName)).toEqual(['a.pdf', 'b.pdf']);
    });

    it("does not return another checklist's queued ops", async () => {
      await bioOfflineRepo.store(snapshot('9001'), 'guid-1');
      await bioOfflineRepo.store(snapshot('9002'), 'guid-2');
      await bioOfflineRepo.queueAttachmentAdd('9002', new Blob(['x']), 'other.pdf');

      expect(await bioOfflineRepo.pendingAttachmentOps('9001')).toHaveLength(0);
    });
  });

  it('removing a copy also removes everything queued against it', async () => {
    // Orphaned queue rows would make a later checkout of the same checklist flush files from a
    // previous trip — uploading evidence to a checklist the user never attached it to.
    await bioOfflineRepo.store(snapshot(), 'guid-1');
    await bioOfflineRepo.queueAttachmentAdd('9001', new Blob(['x']), 'map.pdf');

    await bioOfflineRepo.remove('9001');

    expect(await bioOfflineRepo.load('9001')).toBeUndefined();
    expect(await bioDb.bioAttachmentQueue.toArray()).toHaveLength(0);
  });

  it("removing one copy leaves another checklist's queue alone", async () => {
    await bioOfflineRepo.store(snapshot('9001'), 'guid-1');
    await bioOfflineRepo.store(snapshot('9002'), 'guid-2');
    await bioOfflineRepo.queueAttachmentAdd('9002', new Blob(['x']), 'keep.pdf');

    await bioOfflineRepo.remove('9001');

    expect(await bioOfflineRepo.load('9002')).toBeDefined();
    expect(await bioOfflineRepo.pendingAttachmentOps('9002')).toHaveLength(1);
  });

  it('mints distinct temporary ids and recognises them', async () => {
    const first = mintTmpId();
    const second = mintTmpId();

    expect(first).not.toBe(second);
    expect(isTmpId(first)).toBe(true);
    expect(isTmpId('5001')).toBe(false);
    expect(isTmpId(undefined)).toBe(true);
  });

  it('refuses to save against a checklist that is not held offline', async () => {
    await expect(bioOfflineRepo.saveLocal('nope', snapshot())).rejects.toThrow();
  });
});

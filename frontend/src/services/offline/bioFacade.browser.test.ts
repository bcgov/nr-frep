import { beforeEach, describe, expect, it, vi } from 'vitest';

import { bioDb } from '@/services/offline/bioDb';
import { deriveStratumComputed, withBioOffline } from '@/services/offline/bioFacade';
import { bioOfflineRepo, isTmpId } from '@/services/offline/bioOfflineRepo';

import type { ProtocolChecklistService } from '@/services/protocolChecklist.service';
import type { BioSnapshot } from '@/types/protocolChecklist';

/**
 * Real IndexedDB again: the facade's entire job is deciding online-vs-offline from stored state, so
 * mocking the store would mock the decision under test.
 */

const snapshot = (): BioSnapshot => ({
  schemaVersion: '1',
  checklistId: '9001',
  resourceType: 'SLR',
  statusCode: 'RDO',
  opening: { checklistId: '9001', netArea: '42.5', locationDescription: 'km 16' },
  notes: { checklistId: '9001', noteDescription: 'original note', revisionCount: '3' },
  strata: [
    {
      stratum: { stratumId: '5001', checklistId: '9001', stratumNumber: 'A1', revisionCount: '1' },
      plots: [{ plotId: '7001', stratumId: '5001', plotNumber: 'P1', revisionCount: '1' }],
    },
  ],
  attachments: [
    {
      checklistAttachmentId: '77',
      fileName: 'server.pdf',
      description: 'already uploaded',
      mimeTypeCode: 'PDF',
      fileSize: '100',
    },
  ],
});

/** A stand-in client that records what reached the network. */
const stubClient = () =>
  ({
    getBiodiversityOpening: vi.fn().mockResolvedValue({ checklistId: 'online' }),
    saveBiodiversityOpening: vi.fn().mockResolvedValue({ checklistId: 'online' }),
    getNotes: vi.fn().mockResolvedValue({ checklistId: 'online' }),
    saveNotes: vi.fn().mockResolvedValue({ checklistId: 'online' }),
    listBioStrata: vi.fn().mockResolvedValue([]),
    getBioStratum: vi.fn().mockResolvedValue({ stratumId: 'online' }),
    saveBioStratum: vi.fn().mockResolvedValue({ stratumId: 'online' }),
    deleteBioStratum: vi.fn().mockResolvedValue(undefined),
    listBioPlots: vi.fn().mockResolvedValue([]),
    getBioPlot: vi.fn().mockResolvedValue({ plotId: 'online' }),
    saveBioPlot: vi.fn().mockResolvedValue({ plotId: 'online' }),
    deleteBioPlot: vi.fn().mockResolvedValue(undefined),
    getStratumComputed: vi.fn().mockResolvedValue({ nar: 'online' }),
    getAttachments: vi.fn().mockResolvedValue({ attachments: [], totalCount: 0 }),
    getAttachmentContent: vi.fn().mockResolvedValue({ data: '' }),
    uploadAttachment: vi.fn().mockResolvedValue(undefined),
    deleteAttachment: vi.fn().mockResolvedValue(undefined),
    getNewStratumComputed: vi.fn().mockResolvedValue({ nar: 'online' }),
    submit: vi.fn().mockResolvedValue(undefined),
  }) as unknown as ProtocolChecklistService;

describe('withBioOffline', () => {
  let client: ProtocolChecklistService;
  let api: ProtocolChecklistService;

  beforeEach(async () => {
    await bioDb.bioChecklists.clear();
    await bioDb.bioAttachmentQueue.clear();
    client = stubClient();
    api = withBioOffline(client);
  });

  describe('with no offline copy', () => {
    it('passes reads straight through to the real client', async () => {
      await api.getBiodiversityOpening('9001');
      await api.listBioStrata('9001');
      await api.getStratumComputed('5001');

      expect(client.getBiodiversityOpening).toHaveBeenCalledWith('9001');
      expect(client.listBioStrata).toHaveBeenCalledWith('9001');
      expect(client.getStratumComputed).toHaveBeenCalledWith('5001');
    });

    it('passes writes straight through', async () => {
      await api.saveBioStratum('9001', { stratumNumber: 'A2' });
      await api.deleteBioPlot('7001', '1');

      expect(client.saveBioStratum).toHaveBeenCalled();
      expect(client.deleteBioPlot).toHaveBeenCalledWith('7001', '1');
    });
  });

  describe('with a checked-out copy', () => {
    beforeEach(async () => {
      await bioOfflineRepo.store(snapshot(), 'guid-1');
    });

    it('serves the opening and notes from the local copy without touching the network', async () => {
      expect((await api.getBiodiversityOpening('9001')).locationDescription).toBe('km 16');
      expect((await api.getNotes('bio', '9001')).noteDescription).toBe('original note');

      expect(client.getBiodiversityOpening).not.toHaveBeenCalled();
      expect(client.getNotes).not.toHaveBeenCalled();
    });

    it('writes the opening back to the local copy', async () => {
      await api.saveBiodiversityOpening('9001', { checklistId: '9001', locationDescription: 'edited' });

      const record = await bioOfflineRepo.load('9001');
      expect(record?.snapshot.opening.locationDescription).toBe('edited');
      expect(record?.syncState).toBe('DIRTY');
      expect(client.saveBiodiversityOpening).not.toHaveBeenCalled();
    });

    it('lists strata from the copy, with a live plot count', async () => {
      const rows = await api.listBioStrata('9001');

      expect(rows).toHaveLength(1);
      expect(rows[0].stratumId).toBe('5001');
      expect(rows[0].plotCount).toBe('1');
    });

    it('resolves a stratum by id even though the caller passes no checklist', async () => {
      // Most of the SLR API is keyed by stratum/plot id, so the facade has to find the owning copy.
      expect((await api.getBioStratum('5001')).stratumNumber).toBe('A1');
      expect((await api.getBioPlot('7001')).plotNumber).toBe('P1');
      expect(client.getBioStratum).not.toHaveBeenCalled();
    });

    it('mints a tmp id for a stratum created offline', async () => {
      const saved = await api.saveBioStratum('9001', { stratumNumber: 'A2' });

      expect(isTmpId(saved.stratumId)).toBe(true);
      const record = await bioOfflineRepo.load('9001');
      expect(record?.snapshot.strata).toHaveLength(2);
    });

    it('keeps a real id when editing an existing stratum', async () => {
      const saved = await api.saveBioStratum('9001', {
        stratumId: '5001', stratumNumber: 'A1-edited', revisionCount: '1',
      });

      expect(saved.stratumId).toBe('5001');
      const record = await bioOfflineRepo.load('9001');
      expect(record?.snapshot.strata).toHaveLength(1);
      expect(record?.snapshot.strata[0].stratum.stratumNumber).toBe('A1-edited');
    });

    it('adds a plot under its stratum and keeps the others', async () => {
      const saved = await api.saveBioPlot('5001', { plotNumber: 'P2' });

      expect(isTmpId(saved.plotId)).toBe(true);
      const record = await bioOfflineRepo.load('9001');
      expect(record?.snapshot.strata[0].plots).toHaveLength(2);
    });

    it('deleting a plot records a tombstone and drops it locally', async () => {
      await api.deleteBioPlot('7001', '1');

      const record = await bioOfflineRepo.load('9001');
      expect(record?.snapshot.strata[0].plots).toHaveLength(0);
      expect(record?.tombstones).toEqual([{ entity: 'PLOT', id: '7001', revisionCount: '1' }]);
      expect(client.deleteBioPlot).not.toHaveBeenCalled();
    });

    it('deleting a stratum also tombstones its plots', async () => {
      // The server refuses to remove a stratum while a plot still references it, so the deletion is
      // compound: without the plot tombstone the check-in would abort on childexists.
      await api.deleteBioStratum('5001', '1');

      const record = await bioOfflineRepo.load('9001');
      expect(record?.snapshot.strata).toHaveLength(0);
      expect(record?.tombstones).toEqual([
        { entity: 'PLOT', id: '7001', revisionCount: '1' },
        { entity: 'STRATUM', id: '5001', revisionCount: '1' },
      ]);
    });

    it('never tombstones a stratum that was created offline', async () => {
      const created = await api.saveBioStratum('9001', { stratumNumber: 'A2' });

      await api.deleteBioStratum(created.stratumId as string, '');

      const record = await bioOfflineRepo.load('9001');
      // Dropped from the graph, and nothing queued — the server has never heard of it.
      expect(record?.snapshot.strata).toHaveLength(1);
      expect(record?.tombstones).toEqual([]);
    });

    it('derives the stratum summary instead of calling the server', async () => {
      const computed = await api.getStratumComputed('5001');

      expect(computed).toEqual({ nar: '42.5', plotsCompleted: '1' });
      expect(client.getStratumComputed).not.toHaveBeenCalled();
    });

    it('the derived plot count follows a plot added offline', async () => {
      // The reason to derive rather than snapshot the response: a cached one goes stale the moment
      // the evaluator adds a plot.
      await api.saveBioPlot('5001', { plotNumber: 'P2' });

      expect((await api.getStratumComputed('5001')).plotsCompleted).toBe('2');
    });

    it('a new stratum computes zero plots but keeps the net area', async () => {
      expect(await api.getNewStratumComputed('9001')).toEqual({ nar: '42.5', plotsCompleted: '0' });
    });

    // ── Attachments (UI-5) ─────────────────────────────────────────
    //
    // Routed through the facade so the attachments view needs no offline branch of its own.

    it('queues a captured file instead of uploading it', async () => {
      const file = new File(['x'], 'map.pdf', { type: 'application/pdf' });

      await api.uploadAttachment('bio', '9001', file, 'a map');

      const pending = await bioOfflineRepo.pendingAttachmentOps('9001');
      expect(pending).toHaveLength(1);
      expect(pending[0].fileName).toBe('map.pdf');
      expect(client.uploadAttachment).not.toHaveBeenCalled();
    });

    it('lists a captured file alongside the ones already on the server', async () => {
      await api.uploadAttachment('bio', '9001', new File(['x'], 'new.pdf'), 'captured');

      const page = await api.getAttachments('bio', '9001', 0, 10);

      expect(page.totalCount).toBe(2);
      expect(page.attachments.map((r) => r.fileName)).toEqual(['server.pdf', 'new.pdf']);
    });

    it('hides a server file that was deleted offline', async () => {
      // Queued for deletion but still on the server — the view must show what the user will end up
      // with, not what the server currently holds.
      await api.deleteAttachment('bio', '9001', '77');

      const page = await api.getAttachments('bio', '9001', 0, 10);

      expect(page.totalCount).toBe(0);
      expect(client.deleteAttachment).not.toHaveBeenCalled();
    });

    it('reads a captured file back for preview without a network call', async () => {
      await api.uploadAttachment('bio', '9001', new File(['hello'], 'new.pdf', { type: 'text/plain' }));
      const [row] = (await api.getAttachments('bio', '9001', 0, 10)).attachments.slice(-1);

      const content = await api.getAttachmentContent('bio', '9001', row.checklistAttachmentId!);

      expect(atob(content.data ?? '')).toBe('hello');
      expect(client.getAttachmentContent).not.toHaveBeenCalled();
    });

    it('reads a downloaded server file from the device', async () => {
      await bioOfflineRepo.putAttachmentFile(
        '9001', '77', new Blob(['downloaded'], { type: 'application/pdf' }), 'server.pdf');

      const content = await api.getAttachmentContent('bio', '9001', '77');

      expect(atob(content.data ?? '')).toBe('downloaded');
    });

    it('cancels a capture locally instead of queueing a doomed delete', async () => {
      // A file added and removed offline has no server id; queueing a DELETE would fail at check-in
      // against an attachment the server has never heard of.
      await api.uploadAttachment('bio', '9001', new File(['x'], 'oops.pdf'));
      const [row] = (await api.getAttachments('bio', '9001', 0, 10)).attachments.slice(-1);

      await api.deleteAttachment('bio', '9001', row.checklistAttachmentId!);

      const ops = await bioOfflineRepo.pendingAttachmentOps('9001');
      expect(ops).toHaveLength(0);
      expect((await api.getAttachments('bio', '9001', 0, 10)).totalCount).toBe(1);
    });

    it('raises rather than silently showing nothing for a file not on the device', async () => {
      await expect(api.getAttachmentContent('bio', '9001', '999'))
        .rejects.toThrow(/not available on this device/i);
    });

    it('refuses to submit an offline copy', async () => {
      // CHR parity: submit validation lives in the proc, so a queued offline submit could be
      // rejected days later by rules the device never had.
      await expect(api.submit('bio', '9001')).rejects.toThrow(/check this checklist in/i);
      expect(client.submit).not.toHaveBeenCalled();
    });
  });

  it('only routes the checklist that is actually checked out', async () => {
    await bioOfflineRepo.store(snapshot(), 'guid-1');

    await api.getBiodiversityOpening('9002');

    expect(client.getBiodiversityOpening).toHaveBeenCalledWith('9002');
  });
});

describe('deriveStratumComputed', () => {
  it('counts only the named stratum plots', () => {
    const withTwo: BioSnapshot = {
      ...snapshot(),
      strata: [
        ...snapshot().strata,
        {
          stratum: { stratumId: '5002', checklistId: '9001' },
          plots: [{ plotId: '7002' }, { plotId: '7003' }],
        },
      ],
    };

    expect(deriveStratumComputed(withTwo, '5002').plotsCompleted).toBe('2');
    expect(deriveStratumComputed(withTwo, '5001').plotsCompleted).toBe('1');
  });

  it('reports zero for an unknown stratum rather than throwing', () => {
    expect(deriveStratumComputed(snapshot(), 'nope').plotsCompleted).toBe('0');
  });
});

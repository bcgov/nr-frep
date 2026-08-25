import {
  bioOfflineRepo,
  isTmpId,
  mintTmpId,
  TMP_ID_PREFIX,
} from '@/services/offline/bioOfflineRepo';

import type { OfflineBioChecklist } from '@/services/offline/bioDb';
import type { ProtocolChecklistService } from '@/services/protocolChecklist.service';
import type {
  AttachmentContent,
  AttachmentRow,
  BioPlot,
  BioPlotRow,
  BioSnapshot,
  BioStratum,
  BioStratumRow,
  BiodiversityOpening,
  RiparianNotes,
  StratumComputed,
} from '@/types/protocolChecklist';

/**
 * Offline routing for the Stand Level Retention API.
 *
 * Every Bio view already calls `API.protocolChecklist.*`, so the cheapest place to make them work
 * offline is one layer *below* the views: a facade with the identical interface that serves reads and
 * writes from the local copy when the checklist is checked out to this device, and delegates to the
 * real client otherwise. CHR branches on `isOfflineCopy` inline in its page, which is affordable for
 * one aggregate and one screen; SLR has five views over a graph, so the branch is pushed down here
 * and the views stay unchanged.
 *
 * <b>The facade never talks to the network on the offline path.</b> A field device has no
 * connectivity, so anything it can't answer locally has to be answered from the stored snapshot or
 * derived — see {@link deriveStratumComputed}.
 */

/** The subset of the real client the facade wraps. Everything else is delegated untouched. */
type Client = ProtocolChecklistService;

/** Find the offline copy that owns a stratum, if any. */
const recordForStratum = async (stratumId: string): Promise<OfflineBioChecklist | undefined> => {
  const records = await bioOfflineRepo.listOffline();
  return records.find((record) =>
    record.snapshot.strata.some((entry) => entry.stratum.stratumId === stratumId));
};

/** Find the offline copy that owns a plot, if any. */
const recordForPlot = async (plotId: string): Promise<OfflineBioChecklist | undefined> => {
  const records = await bioOfflineRepo.listOffline();
  return records.find((record) =>
    record.snapshot.strata.some((entry) => entry.plots.some((plot) => plot.plotId === plotId)));
};

const stratumRow = (stratum: BioStratum, plotCount: number): BioStratumRow => ({
  stratumId: stratum.stratumId,
  stratumNumber: stratum.stratumNumber,
  strataTypeCode: stratum.strataTypeCode,
  summaryDate: stratum.summaryDate,
  plotCount: String(plotCount),
  size: stratum.size,
  revisionCount: stratum.revisionCount,
});

const plotRow = (plot: BioPlot): BioPlotRow => ({
  plotId: plot.plotId,
  plotNumber: plot.plotNumber,
  assessorName: plot.assessorName,
  revisionCount: plot.revisionCount,
});

/**
 * The FREP211 Stratum Summary header, derived rather than fetched.
 *
 * Online this is a server round-trip. It is only two values — `nar` rides on the opening and is
 * already in the snapshot, and `plotsCompleted` is a count of the stratum's plots, which the offline
 * copy holds. Deriving beats snapshotting the response: a plot added offline updates the count
 * immediately, whereas a cached response would go stale the moment the evaluator adds one.
 */
export const deriveStratumComputed = (
  snapshot: BioSnapshot,
  stratumId?: string,
): StratumComputed => {
  const entry = stratumId
    ? snapshot.strata.find((candidate) => candidate.stratum.stratumId === stratumId)
    : undefined;
  return {
    nar: snapshot.opening?.netArea,
    plotsCompleted: String(entry?.plots.length ?? 0),
  };
};

/** Replace one stratum's entry in the snapshot, or append it when it is new. */
const upsertStratum = (
  snapshot: BioSnapshot,
  stratum: BioStratum,
  plots?: BioPlot[],
): BioSnapshot => {
  const existing = snapshot.strata.find((e) => e.stratum.stratumId === stratum.stratumId);
  const strata = existing
    ? snapshot.strata.map((e) =>
        e.stratum.stratumId === stratum.stratumId ? { stratum, plots: plots ?? e.plots } : e)
    : [...snapshot.strata, { stratum, plots: plots ?? [] }];
  return { ...snapshot, strata };
};

/**
 * Wrap the real client so the Bio views work unchanged whether or not the checklist is checked out.
 *
 * Only the methods with offline behaviour are overridden; the rest are delegated by spreading the
 * client, so a method added to the service later keeps working online without touching this file.
 */
export const withBioOffline = (client: Client): Client => {
  const facade = {
    // ── Opening ──────────────────────────────────────────────────────
    async getBiodiversityOpening(checklistId: string): Promise<BiodiversityOpening> {
      const record = await bioOfflineRepo.load(checklistId);
      return record ? record.snapshot.opening : client.getBiodiversityOpening(checklistId);
    },

    async saveBiodiversityOpening(
      checklistId: string,
      opening: BiodiversityOpening,
    ): Promise<BiodiversityOpening> {
      const record = await bioOfflineRepo.load(checklistId);
      if (!record) return client.saveBiodiversityOpening(checklistId, opening);
      await bioOfflineRepo.saveLocal(checklistId, { ...record.snapshot, opening });
      return opening;
    },

    // ── Notes ────────────────────────────────────────────────────────
    async getNotes(protocol: string, checklistId: string): Promise<RiparianNotes> {
      const record = await bioOfflineRepo.load(checklistId);
      return record
        ? (record.snapshot.notes ?? { checklistId })
        : client.getNotes(protocol, checklistId);
    },

    async saveNotes(
      protocol: string,
      checklistId: string,
      notes: RiparianNotes,
    ): Promise<RiparianNotes> {
      const record = await bioOfflineRepo.load(checklistId);
      if (!record) return client.saveNotes(protocol, checklistId, notes);
      await bioOfflineRepo.saveLocal(checklistId, { ...record.snapshot, notes });
      return notes;
    },

    // ── Strata ───────────────────────────────────────────────────────
    async listBioStrata(checklistId: string): Promise<BioStratumRow[]> {
      const record = await bioOfflineRepo.load(checklistId);
      if (!record) return client.listBioStrata(checklistId);
      return record.snapshot.strata.map((e) => stratumRow(e.stratum, e.plots.length));
    },

    async getBioStratum(stratumId: string): Promise<BioStratum> {
      const record = await recordForStratum(stratumId);
      if (!record) return client.getBioStratum(stratumId);
      const entry = record.snapshot.strata.find((e) => e.stratum.stratumId === stratumId);
      if (!entry) throw new Error(`Stratum ${stratumId} is not in the offline copy.`);
      return entry.stratum;
    },

    async saveBioStratum(checklistId: string, stratum: BioStratum): Promise<BioStratum> {
      const record = await bioOfflineRepo.load(checklistId);
      if (!record) return client.saveBioStratum(checklistId, stratum);
      // A new stratum gets a local id so the view has a stable key before Oracle assigns the real
      // one at check-in. The orchestrator clears anything tmp: and remaps its plots.
      const saved: BioStratum = isTmpId(stratum.stratumId)
        ? { ...stratum, stratumId: mintTmpId(), checklistId }
        : { ...stratum, checklistId };
      await bioOfflineRepo.saveLocal(checklistId, upsertStratum(record.snapshot, saved));
      return saved;
    },

    async deleteBioStratum(stratumId: string, revisionCount: string): Promise<void> {
      const record = await recordForStratum(stratumId);
      if (!record) return client.deleteBioStratum(stratumId, revisionCount);
      const entry = record.snapshot.strata.find((e) => e.stratum.stratumId === stratumId);
      const snapshot: BioSnapshot = {
        ...record.snapshot,
        strata: record.snapshot.strata.filter((e) => e.stratum.stratumId !== stratumId),
      };
      // Deleting a stratum is a *compound* operation: the server refuses to remove one while any
      // plot still references it, so its plots are tombstoned too, and the check-in applies plots
      // first. Rows created offline carry no server id and are simply dropped (saveLocal filters
      // tmp: ids out of the tombstone list).
      await bioOfflineRepo.saveLocal(record.checklistId, snapshot, [
        ...(entry?.plots ?? []).map((plot) => ({
          entity: 'PLOT' as const,
          id: plot.plotId ?? '',
          revisionCount: plot.revisionCount,
        })),
        { entity: 'STRATUM' as const, id: stratumId, revisionCount },
      ]);
    },

    // ── Plots ────────────────────────────────────────────────────────
    async listBioPlots(stratumId: string): Promise<BioPlotRow[]> {
      const record = await recordForStratum(stratumId);
      if (!record) return client.listBioPlots(stratumId);
      const entry = record.snapshot.strata.find((e) => e.stratum.stratumId === stratumId);
      return (entry?.plots ?? []).map(plotRow);
    },

    async getBioPlot(plotId: string): Promise<BioPlot> {
      const record = await recordForPlot(plotId);
      if (!record) return client.getBioPlot(plotId);
      const plot = record.snapshot.strata
        .flatMap((e) => e.plots)
        .find((candidate) => candidate.plotId === plotId);
      if (!plot) throw new Error(`Plot ${plotId} is not in the offline copy.`);
      return plot;
    },

    async saveBioPlot(stratumId: string, plot: BioPlot): Promise<BioPlot> {
      const record = await recordForStratum(stratumId);
      if (!record) return client.saveBioPlot(stratumId, plot);
      const saved: BioPlot = isTmpId(plot.plotId)
        ? { ...plot, plotId: mintTmpId(), stratumId }
        : { ...plot, stratumId };
      const entry = record.snapshot.strata.find((e) => e.stratum.stratumId === stratumId);
      const plots = entry?.plots.some((p) => p.plotId === saved.plotId)
        ? entry.plots.map((p) => (p.plotId === saved.plotId ? saved : p))
        : [...(entry?.plots ?? []), saved];
      await bioOfflineRepo.saveLocal(
        record.checklistId,
        upsertStratum(record.snapshot, entry!.stratum, plots),
      );
      return saved;
    },

    async deleteBioPlot(plotId: string, revisionCount: string): Promise<void> {
      const record = await recordForPlot(plotId);
      if (!record) return client.deleteBioPlot(plotId, revisionCount);
      const snapshot: BioSnapshot = {
        ...record.snapshot,
        strata: record.snapshot.strata.map((e) => ({
          ...e,
          plots: e.plots.filter((plot) => plot.plotId !== plotId),
        })),
      };
      await bioOfflineRepo.saveLocal(record.checklistId, snapshot, [
        { entity: 'PLOT', id: plotId, revisionCount },
      ]);
    },

    // ── Computed ─────────────────────────────────────────────────────
    async getStratumComputed(stratumId: string): Promise<StratumComputed> {
      const record = await recordForStratum(stratumId);
      return record
        ? deriveStratumComputed(record.snapshot, stratumId)
        : client.getStratumComputed(stratumId);
    },

    async getNewStratumComputed(checklistId: string): Promise<StratumComputed> {
      const record = await bioOfflineRepo.load(checklistId);
      // A stratum that doesn't exist yet has no plots, so only `nar` is meaningful.
      return record
        ? deriveStratumComputed(record.snapshot)
        : client.getNewStratumComputed(checklistId);
    },

    // ── Attachments ──────────────────────────────────────────────────
    //
    // Routed here rather than in the view for the same reason as everything above: the attachments
    // view already calls `API.protocolChecklist.*`, so it needs no offline branch of its own.
    //
    // A file captured offline has no server id, so it is listed under a synthetic `tmp:` id derived
    // from its queue row. That id is what the view then passes back to preview or delete it, which is
    // how those operations find the queued op rather than a server attachment that does not exist.

    async getAttachments(
      protocol: string,
      checklistId: string,
      page: number,
      size: number,
    ): Promise<{ attachments: AttachmentRow[]; totalCount: number }> {
      const record = await bioOfflineRepo.load(checklistId);
      if (!record) return client.getAttachments(protocol, checklistId, page, size);

      const queued = await bioOfflineRepo.pendingAttachmentOps(checklistId);
      const deletedIds = new Set(
        queued.filter((op) => op.kind === 'DELETE').map((op) => op.attachmentId));
      // What the server holds, minus anything deleted locally, plus anything captured locally.
      const serverRows = (record.snapshot.attachments ?? [])
        .filter((row) => !deletedIds.has(row.checklistAttachmentId));
      const capturedRows: AttachmentRow[] = queued
        .filter((op) => op.kind === 'ADD')
        .map((op) => ({
          checklistAttachmentId: `${TMP_ID_PREFIX}${op.id}`,
          fileName: op.fileName,
          description: op.description,
          mimeTypeCode: op.blob?.type,
          fileSize: String(op.blob?.size ?? 0),
        }));

      const all = [...serverRows, ...capturedRows];
      const from = page * size;
      return { attachments: all.slice(from, from + size), totalCount: all.length };
    },

    async getAttachmentContent(
      protocol: string,
      checklistId: string,
      attachmentId: string,
    ): Promise<AttachmentContent> {
      const record = await bioOfflineRepo.load(checklistId);
      if (!record) return client.getAttachmentContent(protocol, checklistId, attachmentId);

      // A locally captured file is still only in the queue; a server one was downloaded at
      // take-offline. Either way the bytes are on this device — the network is not an option here.
      const blob = isTmpId(attachmentId)
        ? (await bioOfflineRepo.pendingAttachmentOps(checklistId))
            .find((op) => `${TMP_ID_PREFIX}${op.id}` === attachmentId)?.blob
        : await bioOfflineRepo.attachmentFile(attachmentId);
      if (!blob) {
        throw new Error('That file is not available on this device.');
      }
      return { fileName: attachmentId, mimeType: blob.type, data: await toBase64(blob) };
    },

    async uploadAttachment(
      protocol: string,
      checklistId: string,
      file: File,
      description?: string,
      deviceCheckoutGuid?: string,
    ): Promise<void> {
      const record = await bioOfflineRepo.load(checklistId);
      if (!record) {
        return client.uploadAttachment(protocol, checklistId, file, description, deviceCheckoutGuid);
      }
      // Queued, not sent: the device may have no connectivity, and these bytes are the only copy
      // until the check-in flush uploads them.
      await bioOfflineRepo.queueAttachmentAdd(checklistId, file, file.name, description);
    },

    async deleteAttachment(
      protocol: string,
      checklistId: string,
      attachmentId: string,
      deviceCheckoutGuid?: string,
    ): Promise<void> {
      const record = await bioOfflineRepo.load(checklistId);
      if (!record) {
        return client.deleteAttachment(protocol, checklistId, attachmentId, deviceCheckoutGuid);
      }
      if (isTmpId(attachmentId)) {
        // Captured and removed before ever being sent — drop the queued upload. Queueing a DELETE
        // would fail at check-in against an attachment the server has never heard of.
        const opId = Number(attachmentId.slice(TMP_ID_PREFIX.length));
        if (Number.isFinite(opId)) await bioOfflineRepo.discardAttachmentOp(opId);
        return;
      }
      await bioOfflineRepo.queueAttachmentDelete(checklistId, attachmentId);
    },

    // ── Submit ───────────────────────────────────────────────────────
    async submit(protocol: string, checklistId: string): Promise<void> {
      const record = await bioOfflineRepo.load(checklistId);
      if (record) {
        // CHR parity: submit is online-only. SLR's submit validation lives in the proc, so a queued
        // offline submit could be rejected days later by rules the device never had. The UI hides
        // the action; this is the backstop.
        throw new Error('Check this checklist in before submitting it.');
      }
      return client.submit(protocol as never, checklistId);
    },
  };

  return { ...client, ...facade } as Client;
};

/** Blob → base64, matching the envelope the online content endpoint returns. */
const toBase64 = (blob: Blob): Promise<string> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result);
      resolve(result.slice(result.indexOf(',') + 1));
    };
    reader.onerror = () => reject(reader.error ?? new Error('Could not read the file'));
    reader.readAsDataURL(blob);
  });

import API from '@/services/APIs';
import { bioOfflineRepo } from '@/services/offline/bioOfflineRepo';
import { bioReferenceCache } from '@/services/offline/bioReferenceCache';

import type { OfflineBioChecklist } from '@/services/offline/bioDb';
import type { AttachmentContent, AttachmentRow, BioSnapshot } from '@/types/protocolChecklist';

/**
 * Take an SLR checklist offline.
 *
 * **Reads first, checkout last.** Everything readable is pulled while the checklist is still `ACT`,
 * and only then is the checkout claimed. That ordering is the whole reason an interrupted download
 * costs nothing: no checkout was taken, the checklist stays editable online, and there is nothing to
 * release. Ported from `chrOfflineRepo.takeOffline`, which shipped this shape and had the
 * mid-download network kill verified by hand.
 *
 * The one thing traded away is a small race — someone could add an attachment between the read and
 * the checkout, and this copy would miss it. Harmless: attachments are independent resources, so it
 * is simply still there at check-in.
 */

/** Progress for the UI. Attachments dominate the wall-clock, so they are reported per file. */
export type TakeOfflineProgress = {
  phase: 'snapshot' | 'reference' | 'attachments' | 'checkout';
  /** Files downloaded so far, during `attachments`. */
  done?: number;
  total?: number;
};

export type TakeOfflineOptions = {
  onProgress?: (progress: TakeOfflineProgress) => void;
  /**
   * Called when the download looks too large for the device. Return true to continue anyway.
   * Defaults to refusing, so a caller that forgets to handle it fails safe.
   */
  onQuotaWarning?: (estimateBytes: number, availableBytes: number) => Promise<boolean>;
};

/** Raised when the user declined an oversized download, so the caller can stay quiet. */
export class TakeOfflineCancelled extends Error {
  constructor() {
    super('Take offline was cancelled.');
    this.name = 'TakeOfflineCancelled';
  }
}

/** Bytes the attachments will add, from the sizes PR 3a made real (they used to be `0.00`). */
export const estimateAttachmentBytes = (attachments: AttachmentRow[]): number =>
  attachments.reduce((total, row) => total + (Number(row.fileSize) || 0), 0);

/**
 * Headroom check before a download starts.
 *
 * This is decision 3's guardrail, and it is *not* durability: it stops a download that cannot fit,
 * it does not stop the browser evicting one that already did. Durability was deliberately left at
 * CHR parity (no `persist()`), so this is the only storage protection SLR has — worth keeping even
 * though it is the weaker half.
 *
 * A browser that won't answer `estimate()` is treated as "enough room" rather than blocking the
 * feature on a diagnostic.
 */
const hasHeadroom = async (needBytes: number): Promise<{ ok: boolean; available: number }> => {
  if (typeof navigator === 'undefined' || !navigator.storage?.estimate) {
    return { ok: true, available: Number.POSITIVE_INFINITY };
  }
  const { quota = 0, usage = 0 } = await navigator.storage.estimate();
  const available = Math.max(0, quota - usage);
  // Two-times headroom: IndexedDB overheads, the graph itself, and the reference cache all land on
  // top of the raw attachment bytes, and running the device to zero would break far more than this.
  return { ok: available === 0 || available > needBytes * 2, available };
};

/** Guards against a double-tap starting two downloads for the same checklist. */
const inFlight = new Set<string>();

export const takeBioChecklistOffline = async (
  checklistId: string,
  options: TakeOfflineOptions = {},
): Promise<OfflineBioChecklist> => {
  if (inFlight.has(checklistId)) {
    throw new Error('This checklist is already being taken offline.');
  }
  inFlight.add(checklistId);
  try {
    const { onProgress } = options;

    // 1. The graph, while still ACT. No checkout is claimed by this read.
    onProgress?.({ phase: 'snapshot' });
    const snapshot: BioSnapshot = await API.protocolChecklist.getSnapshot(checklistId);

    // 2. Reference data — the Bio views are unusable without it, and CHR needed none of this
    //    because its code lists are hardcoded. Refreshed here, where the device is definitely online.
    onProgress?.({ phase: 'reference' });
    await bioReferenceCache.refresh(API.configuration);

    // 3. Attachment bytes. Checked for headroom first: files run to 15 MB with no per-checklist cap,
    //    so this is where a device runs out of room, not on the graph.
    const attachments = snapshot.attachments ?? [];
    const needBytes = estimateAttachmentBytes(attachments);
    const { ok, available } = await hasHeadroom(needBytes);
    if (!ok) {
      const proceed = (await options.onQuotaWarning?.(needBytes, available)) ?? false;
      if (!proceed) throw new TakeOfflineCancelled();
    }

    onProgress?.({ phase: 'attachments', done: 0, total: attachments.length });
    let done = 0;
    for (const row of attachments) {
      if (!row.checklistAttachmentId) continue;
      // Sequential on purpose: a field device on a poor connection fares better with one request at
      // a time than with N in flight, and a failure part-way leaves the earlier files already saved.
      const content = await API.protocolChecklist.getAttachmentContent(
        'bio', checklistId, row.checklistAttachmentId);
      await bioOfflineRepo.putAttachmentFile(
        checklistId,
        row.checklistAttachmentId,
        toBlob(content),
        row.fileName,
        row.mimeTypeCode,
      );
      done += 1;
      onProgress?.({ phase: 'attachments', done, total: attachments.length });
    }

    // 4. Only now claim the checkout. Everything above is repeatable at no cost; this is not.
    onProgress?.({ phase: 'checkout' });
    const checkout = await API.protocolChecklist.takeOffline(checklistId);

    return await bioOfflineRepo.store(snapshot, checkout.deviceCheckoutGuid ?? undefined);
  } finally {
    inFlight.delete(checklistId);
  }
};

/**
 * The content endpoint returns base64 in an `AttachmentContent` envelope rather than a binary body
 * (unlike CHR's photo content, which is a Blob). Decode once here so everything downstream — the
 * viewer, the queue — deals in Blobs.
 */
const toBlob = (content: AttachmentContent): Blob => {
  const base64 = content.data ?? '';
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  // `mimeType`, not `mimeTypeCode` — the envelope and the list row spell it differently, and a
  // structural param type would have accepted the wrong one while silently yielding octet-stream,
  // so a PDF taken offline would not open as a PDF.
  return new Blob([bytes], { type: content.mimeType || 'application/octet-stream' });
};

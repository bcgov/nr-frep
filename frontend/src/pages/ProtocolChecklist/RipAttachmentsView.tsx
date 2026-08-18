import { Download, TrashCan, Upload } from '@carbon/icons-react';
import { Button, Pagination, SkeletonText, TextArea } from '@carbon/react';
import { useCallback, useEffect, useRef, useState, type FC } from 'react';

import ImagePreviewModal from '@/components/core/ImagePreviewModal';
import UploadHelp from '@/components/core/UploadHelp';
import { requiredLabel } from '@/utils/requiredLabel';

import type { AttachmentRow } from '@/types/protocolChecklist';

import { useConfirm } from '@/context/confirm/useConfirm';
import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';
import { apiErrorMessage } from '@/utils/apiError';
import {
  ALLOWED_ATTACHMENT_ACCEPT,
  ALLOWED_ATTACHMENT_EXTENSIONS,
  isAllowedAttachmentExtension,
} from '@/utils/attachmentTypes';
import { byteLength, overLimitError } from '@/utils/textLimits';
import { MAX_UPLOAD_BYTES, MAX_UPLOAD_MB, formatMb } from '@/utils/uploadLimits';

/**
 * Byte limit of the attachment description column.
 *
 * <p>`frep_checklist_attachments.save` inserts into a different table per protocol — only
 * Biodiversity reaches this app (`normalizeProtocolType` accepts BIO/SLB only), so the column is
 * `biodiversity_chklst_attach.description` (FREP_CHECKLIST_ATTACHMENTS.pks:1191-1201). Note the
 * package's shared cursor record borrows its types from `riparian_checklist_attach` regardless of
 * protocol — that record is not the target table.
 *
 * <p>The base column is `VARCHAR2(120 BYTE)` — `BIODIVERSITY_CHKLST_ATTACH.DESCRIPTION` in nr-mof-db
 * `scripts/THE/TABLES/V2.00400__BIODIVERSITY_CHKLST_ATTACH.sql`. That 120 matched the legacy UI,
 * which capped this input at `maxlength="120"` (`checklistAttachment.jsp:62`) on the one screen it
 * used for every protocol. Note the table carries no `FREP_` prefix, unlike the
 * `FREP_CHECKLIST_ATTACHMENTS` package that writes it — looking for the prefixed name finds nothing
 * and reads as "the DDL isn't in the repo".
 *
 * <p>Migration `V202608051100.3` widens it to 2000, which is what this constant assumes.
 * **The value is only correct once that migration has been deployed to the target environment** —
 * shipping it ahead of the DDL lets the UI accept descriptions the insert will reject with an
 * ORA-12899. If that migration is still pending anywhere this deploys, drop this back to 120.
 */
const DESCRIPTION_LIMIT = 2000;

/**
 * Checklist Attachments tab (legacy {@code checklistAttachment} / FREP_CHECKLIST_ATTACHMENTS) —
 * list, download, upload, and delete file attachments. Uploads are `multipart/form-data`: the raw
 * `File` goes on the wire, so there's no base64 inflation and the server can spool it to disk
 * instead of holding it in heap. Auth + CSRF still ride on the standard API client.
 *
 * Downloads are still base64 JSON — the read path is a separate piece of work.
 */

type Props = {
  protocol: string;
  checklistId: string;
  canEdit: boolean;
  submitted: boolean;
};

/**
 * Extensions that get the "trim or compress" hint when they exceed the size cap.
 *
 * The 15 MB cap is deliberate for video: an untrimmed phone clip is roughly ten seconds of 1080p
 * (two of 4K), so the limit is expected to bite and the intent is to push users to compress before
 * attaching — not to be raised. Raising it isn't a config change anyway: the scan, the
 * object-storage write and the BLOB path each hold the whole file as a byte[] on a 400 MB heap.
 */
const VIDEO_EXTENSIONS = new Set(['mp4']);

// Shared with CHR photos so the two screens can't drift; see utils/uploadLimits for how this
// relates to max-file-size, max-request-size and the Coraza body limit.
const MAX_ATTACHMENT_MB = MAX_UPLOAD_MB;
const MAX_ATTACHMENT_BYTES = MAX_UPLOAD_BYTES;

const PAGE_SIZES = [10, 25, 50];

/**
 * Above this, an image is listed with a placeholder instead of a thumbnail.
 *
 * Thumbnails are built by downloading the *whole* file — there is no thumbnail endpoint — so a page
 * of large images would pull tens of MB through the browser and the server's heap just to render
 * previews. New uploads are bounded by the 15 MB cap, but attachments predating it are not.
 */
const MAX_THUMBNAIL_BYTES = 2 * 1024 * 1024;

// True when an attachment is an image we can preview as a thumbnail.
//
// TIFF is deliberately excluded even though it's an allowed attachment type: no mainstream browser
// renders it in an <img>, so treating it as previewable meant downloading the whole file (TIFFs are
// the largest, least compressible type here), base64-ing it into a data URL, and silently falling
// back to the placeholder when it failed to decode.
function isImage(row: AttachmentRow): boolean {
  const mime = (row.mimeTypeCode || '').toLowerCase();
  if (mime.includes('tif')) return false;
  if (mime.includes('image')) return true;
  return /\.(jpe?g|png|gif|bmp|webp)$/.test((row.fileName || '').toLowerCase());
}

const RipAttachmentsView: FC<Props> = ({ protocol, checklistId, canEdit, submitted }) => {
  const { display } = useNotification();
  const confirm = useConfirm();
  const [rows, setRows] = useState<AttachmentRow[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(PAGE_SIZES[0]);
  // attachmentId -> data-URL thumbnail, fetched lazily for image attachments.
  const [thumbs, setThumbs] = useState<Record<string, string>>({});
  const [preview, setPreview] = useState<{ src: string; alt: string } | null>(null);
  const [description, setDescription] = useState('');
  const [descInvalid, setDescInvalid] = useState(false);
  // Checked here rather than left to the database: the column is byte-limited and nothing else
  // enforces it, so an over-long description used to surface only as a failed upload.
  const descLimitError = overLimitError(description, DESCRIPTION_LIMIT);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const reportError = useCallback(
    (title: string, err: unknown) =>
      display({
        kind: 'error',
        title,
        subtitle: apiErrorMessage(err),
        timeout: 9000,
      }),
    [display],
  );

  // Upload and delete respond 204, so the list is re-read after every mutation rather than being
  // patched from a response body — one source of truth for what the server holds.
  const refreshRows = useCallback(
    async (targetPage = page, targetSize = pageSize) => {
      let landedPage = targetPage;
      let result = await API.protocolChecklist.getAttachments(
        protocol,
        checklistId,
        landedPage,
        targetSize,
      );
      // Deleting the last row on the last page leaves the client asking for a page that no longer
      // exists: the response is empty while totalCount is still non-zero. Re-read the last page
      // that does exist, rather than showing an empty table under a pager insisting there are
      // items. Bounded to one extra request — the recomputed page is always in range.
      if (result.attachments.length === 0 && result.totalCount > 0 && landedPage > 0) {
        landedPage = Math.max(0, Math.ceil(result.totalCount / targetSize) - 1);
        result = await API.protocolChecklist.getAttachments(
          protocol,
          checklistId,
          landedPage,
          targetSize,
        );
      }
      setRows(result.attachments);
      setTotalCount(result.totalCount);
      setPage(landedPage);
      setPageSize(targetSize);
    },
    [protocol, checklistId, page, pageSize],
  );

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    API.protocolChecklist
      .getAttachments(protocol, checklistId, 0, PAGE_SIZES[0])
      .then((result) => {
        if (cancelled) return;
        setRows(result.attachments);
        setTotalCount(result.totalCount);
      })
      .catch((err: unknown) => {
        if (!cancelled) reportError("We couldn't load the attachments", err);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [protocol, checklistId, reportError]);

  // Lazily fetch thumbnails for image attachments (the list endpoint returns metadata only).
  // Failures are silent — a missing thumbnail simply falls back to the placeholder.
  useEffect(() => {
    let cancelled = false;
    const pending = rows.filter(
      (r) =>
        r.checklistAttachmentId &&
        isImage(r) &&
        !thumbs[r.checklistAttachmentId] &&
        // Size comes from object storage (the DB column is derived from an empty BLOB and always
        // reads 0). Unknown size is treated as too large — better a placeholder than an unbounded
        // download.
        Number(r.fileSize) > 0 &&
        Number(r.fileSize) <= MAX_THUMBNAIL_BYTES,
    );
    if (pending.length === 0) return;
    void Promise.all(
      pending.map(async (r) => {
        try {
          const content = await API.protocolChecklist.getAttachmentContent(
            protocol,
            checklistId,
            r.checklistAttachmentId as string,
          );
          if (!content.data) return null;
          const mime = content.mimeType || r.mimeTypeCode || 'image/jpeg';
          return [r.checklistAttachmentId as string, `data:${mime};base64,${content.data}`] as [
            string,
            string,
          ];
        } catch {
          return null;
        }
      }),
    ).then((entries) => {
      if (cancelled) return;
      const loaded = entries.filter((e): e is [string, string] => e !== null);
      if (loaded.length > 0) setThumbs((prev) => ({ ...prev, ...Object.fromEntries(loaded) }));
    });
    return () => {
      cancelled = true;
    };
  }, [rows, thumbs, protocol, checklistId]);

  /**
   * Reject a file before it is sent, or return null if it's fine. The server re-checks all three
   * (413 / 400) — these exist so the user isn't left waiting for an upload that can't succeed.
   */
  const rejectionReason = (file: File): string | null => {
    const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : '';
    // An extensionless file is refused whatever the configured list says — the extension IS the
    // stored MIME_TYPE_CODE, and the column is NOT NULL, so it could never be saved.
    if (!ext) return 'has no file extension';
    if (!isAllowedAttachmentExtension(ext)) {
      return `".${ext}" is not a supported type`;
    }
    if (file.size === 0) return 'is empty';
    if (file.size > MAX_ATTACHMENT_BYTES) {
      const over = `is ${formatMb(file.size)} MB (max ${MAX_ATTACHMENT_MB} MB)`;
      // Video is the one type where the cap is routinely hit and the fix isn't obvious: an
      // untouched phone clip is ~10s of 1080p at this size, so "too big" alone reads as a defect.
      // The cap is deliberate — it exists to push compression rather than to be raised — so say
      // what to do about it. Every other type is either already small or obviously trimmable.
      return VIDEO_EXTENSIONS.has(ext) ? `${over}. Trim or compress the video and try again` : over;
    }
    return null;
  };

  /**
   * Upload one or more files under the description entered above.
   *
   * The description is shared across the batch, mirroring CHR photos. Uploads run **sequentially**:
   * each one holds its bytes in server heap for the scan and the store, so several large files in
   * flight at once is exactly the pressure the 15 MB cap was sized against. Sequential also means a
   * failure part-way through doesn't cost the files that already landed — they are reported, kept,
   * and only the failures are named.
   */
  const handleUpload = async (files: File[]) => {
    if (files.length === 0) return;
    // Legacy FREP303 requires a description before an attachment can be saved — surface it as
    // inline field validation rather than blocking the Browse button.
    if (!description.trim()) {
      setDescInvalid(true);
      return;
    }
    // Over-length is reported by the field's own counter; just don't start an upload that the
    // database would reject at the end of it.
    if (descLimitError) return;

    const rejected = files
      .map((file) => ({ file, reason: rejectionReason(file) }))
      .filter((r): r is { file: File; reason: string } => r.reason !== null);
    const accepted = files.filter((file) => rejectionReason(file) === null);

    if (rejected.length > 0) {
      display({
        kind: 'error',
        title:
          rejected.length === 1 ? "Can't upload that file" : `Skipped ${rejected.length} files`,
        subtitle: rejected.map((r) => `"${r.file.name}" ${r.reason}`).join('; '),
        timeout: 9000,
      });
    }
    if (accepted.length === 0) return;

    setBusy(true);
    const failed: string[] = [];
    let uploaded = 0;
    try {
      for (const file of accepted) {
        try {
          await API.protocolChecklist.uploadAttachment(
            protocol,
            checklistId,
            file,
            description.trim(),
          );
          uploaded += 1;
        } catch {
          failed.push(file.name);
        }
      }
      // One refresh for the whole batch, not one per file.
      await refreshRows();
      if (uploaded > 0) setDescription('');

      if (failed.length === 0) {
        display({
          kind: 'success',
          title: uploaded === 1 ? 'Attachment uploaded' : `${uploaded} attachments uploaded`,
          timeout: 4000,
        });
      } else if (uploaded > 0) {
        display({
          kind: 'warning',
          title: `Uploaded ${uploaded}, failed ${failed.length}`,
          subtitle: `Could not upload: ${failed.join(', ')}. The others were saved.`,
          timeout: 9000,
        });
      } else {
        display({
          kind: 'error',
          title: 'Upload failed',
          subtitle: `Could not upload: ${failed.join(', ')}.`,
          timeout: 9000,
        });
      }
    } catch (err) {
      reportError('Upload failed', err);
    } finally {
      setBusy(false);
    }
  };

  const handleDownload = async (row: AttachmentRow) => {
    if (!row.checklistAttachmentId) return;
    setBusy(true);
    try {
      const content = await API.protocolChecklist.getAttachmentContent(
        protocol,
        checklistId,
        row.checklistAttachmentId,
      );
      const bytes = Uint8Array.from(atob(content.data ?? ''), (c) => c.charCodeAt(0));
      const blob = new Blob([bytes], { type: content.mimeType || 'application/octet-stream' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = content.fileName || row.fileName || 'file';
      link.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      reportError('Download failed', err);
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async (row: AttachmentRow) => {
    if (!row.checklistAttachmentId) return;
    if (
      !(await confirm({
        title: 'Delete attachment?',
        message: `Delete ${row.fileName || 'this attachment'}? This can't be undone.`,
      }))
    )
      return;
    setBusy(true);
    try {
      await API.protocolChecklist.deleteAttachment(
        protocol,
        checklistId,
        row.checklistAttachmentId,
      );
      await refreshRows();
      display({ kind: 'success', title: 'Attachment removed', timeout: 4000 });
    } catch (err) {
      reportError('Delete failed', err);
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return <SkeletonText paragraph lineCount={4} />;
  }

  const canManage = canEdit && !submitted;

  return (
    <div className="rip-form">
      {/* Upload card sits ABOVE the list: the tab is entered to add a file far more often than
          to browse existing ones, and with a full page of attachments the upload controls were
          below the fold. Keep the order — the list and its pager follow. */}
      {canManage && (
        <div className="attach-card">
          <div className="attach-card__header">Upload files</div>
          <div className="attach-card__body">
            <div className="frep-field attach-card__desc">
              <TextArea
                id="attach-description"
                labelText={requiredLabel('Description', true)}
                rows={3}
                required
                invalid={descInvalid || Boolean(descLimitError)}
                invalidText={descLimitError || 'The description field must be entered.'}
                value={description}
                disabled={busy}
                onChange={(e) => {
                  setDescription(e.target.value);
                  if (e.target.value.trim()) setDescInvalid(false);
                }}
              />
              <div className="frep-field__footer">
                <span
                  className={
                    descLimitError
                      ? 'frep-field__counter frep-field__counter--over'
                      : 'frep-field__counter'
                  }
                  aria-live="polite"
                >
                  {byteLength(description)} / {DESCRIPTION_LIMIT}
                </span>
              </div>
            </div>
            <div
              className={`attach-drop${dragOver ? ' attach-drop--over' : ''}`}
              onDragOver={(e) => {
                e.preventDefault();
                if (!busy) setDragOver(true);
              }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(e) => {
                e.preventDefault();
                setDragOver(false);
                const files = Array.from(e.dataTransfer.files ?? []);
                if (files.length > 0 && !busy) void handleUpload(files);
              }}
            >
              <span className="attach-drop__icon">
                <Upload size={24} />
              </span>
              <div className="attach-drop__copy">
                <p className="attach-drop__text">
                  Select or drag and drop files to upload. The description above applies to every
                  file in the batch.
                </p>
                {/* Driven by the same constants rejectionReason validates against, so the help
                    can't claim a format or size the uploader would then refuse. */}
                <UploadHelp maxMb={MAX_ATTACHMENT_MB} formats={ALLOWED_ATTACHMENT_EXTENSIONS} />
              </div>
              <Button
                kind="primary"
                size="lg"
                disabled={busy}
                onClick={() => fileInputRef.current?.click()}
              >
                Browse files
              </Button>
              <input
                ref={fileInputRef}
                type="file"
                accept={ALLOWED_ATTACHMENT_ACCEPT}
                multiple
                hidden
                onChange={(e) => {
                  const files = Array.from(e.target.files ?? []);
                  if (files.length > 0) void handleUpload(files);
                  e.target.value = '';
                }}
              />
            </div>
          </div>
        </div>
      )}

      {rows.length > 0 && (
        <table className="rip-field-grid">
          <thead>
            <tr>
              <th scope="col">Preview</th>
              <th scope="col">File</th>
              <th scope="col">Description</th>
              <th scope="col">Type</th>
              <th scope="col">Actions</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => {
              const thumb = row.checklistAttachmentId
                ? thumbs[row.checklistAttachmentId]
                : undefined;
              return (
                <tr key={`att-${row.checklistAttachmentId ?? index}`}>
                  <td>
                    {thumb ? (
                      <button
                        type="button"
                        className="image-thumb-button"
                        onClick={() =>
                          setPreview({
                            src: thumb,
                            alt: row.description || row.fileName || `Attachment ${index + 1}`,
                          })
                        }
                      >
                        <img
                          className="rip-attach__thumb image-thumb--clickable"
                          src={thumb}
                          alt={row.description || row.fileName || `Attachment ${index + 1}`}
                        />
                      </button>
                    ) : (
                      <span className="rip-attach__thumb rip-attach__thumb--placeholder">
                        {isImage(row) ? '…' : row.mimeTypeCode || 'File'}
                      </span>
                    )}
                  </td>
                  <td>{row.fileName || '—'}</td>
                  <td>{row.description || '—'}</td>
                  <td>{row.mimeTypeCode || '—'}</td>
                  <td className="rip-grid__choice">
                    <Button
                      kind="ghost"
                      size="sm"
                      hasIconOnly
                      renderIcon={Download}
                      iconDescription="Download"
                      disabled={busy}
                      onClick={() => void handleDownload(row)}
                    />
                    {canManage && (
                      <Button
                        kind="danger--ghost"
                        size="sm"
                        hasIconOnly
                        renderIcon={TrashCan}
                        iconDescription="Delete"
                        disabled={busy}
                        onClick={() => void handleDelete(row)}
                      />
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      {/*
        Shown whenever the checklist has attachments — including when they all fit on one page, so
        the count and the page-size selector stay available. It was previously gated on
        `totalCount > PAGE_SIZES[0]`, which hid both below 10 rows; that guard also compared against
        the literal PAGE_SIZES[0] rather than the current pageSize, so it never tracked the
        selection it was meant to reflect.

        Hidden only at zero, where a pager reading "0–0 of 0 items" is noise above an empty tab.
        Gated on totalCount rather than the rendered row count so it doesn't flicker off during the
        re-read that follows a delete. `loading` early-returns a skeleton above, so this never
        renders before the first count arrives.
      */}
      {totalCount > 0 && (
        <Pagination
          page={page + 1}
          pageSize={pageSize}
          pageSizes={PAGE_SIZES}
          totalItems={totalCount}
          disabled={busy}
          onChange={({ page: nextPage, pageSize: nextSize }) => {
            // Carbon's page is 1-based, the API 0-based. A page-size change resets to the first
            // page so the offset stays valid.
            void refreshRows(nextSize === pageSize ? nextPage - 1 : 0, nextSize);
          }}
        />
      )}

      {preview && (
        <ImagePreviewModal
          open
          src={preview.src}
          alt={preview.alt}
          onClose={() => setPreview(null)}
        />
      )}
    </div>
  );
};

export default RipAttachmentsView;

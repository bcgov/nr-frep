import { Download, TrashCan, Upload } from '@carbon/icons-react';
import { Button, SkeletonText, TextArea } from '@carbon/react';
import { useCallback, useEffect, useRef, useState, type FC } from 'react';

import ImagePreviewModal from '@/components/core/ImagePreviewModal';
import { requiredLabel } from '@/utils/requiredLabel';

import type { AttachmentRow } from '@/types/protocolChecklist';

import { useConfirm } from '@/context/confirm/useConfirm';
import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';
import { apiErrorMessage } from '@/utils/apiError';
import { byteLength, overLimitError } from '@/utils/textLimits';

/**
 * Byte limit of the attachment description column.
 *
 * <p>`frep_checklist_attachments.save` inserts into a different table per protocol — only
 * Biodiversity reaches this app (`normalizeProtocolType` accepts BIO/SLB only), so the column is
 * `biodiversity_chklst_attach.description` (FREP_CHECKLIST_ATTACHMENTS.pks:1191-1201). Note the
 * package's shared cursor record borrows its types from `riparian_checklist_attach` regardless of
 * protocol — that record is not the target table.
 *
 * <p>120 comes from the legacy UI, which capped this input at `maxlength="120"`
 * (`checklistAttachment.jsp:62`) on the one screen it used for every protocol; its validator only
 * checked the field was present. So 120 is the widest value the old app is known to have written
 * successfully, not necessarily the column width — the table DDL is not in this repo. If the column
 * is wider this can safely be raised:
 *   SELECT char_used, data_length FROM all_tab_columns
 *    WHERE table_name = 'BIODIVERSITY_CHKLST_ATTACH' AND column_name = 'DESCRIPTION';
 */
const DESCRIPTION_LIMIT = 120;

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

// Allowed attachment extensions = the codes in THE.MIME_TYPE_CODE (keyed by extension). The
// FREP_CHECKLIST_ATTACHMENTS proc rejects anything else (ORA-01400), so guard here for a friendly
// message + native picker filter. The backend re-validates authoritatively.
const ALLOWED_ATTACHMENT_EXTENSIONS = [
  'bmp',
  'csv',
  'doc',
  'gif',
  'htm',
  'ifm',
  'jpg',
  'jpk',
  'mdb',
  'mde',
  'obd',
  'pdf',
  'png',
  'pps',
  'ppt',
  'rpt',
  'rtf',
  'tif',
  'txt',
  'wav',
  'xld',
  'xls',
  'xml',
  'zip',
];
const ALLOWED_ATTACHMENT_ACCEPT = ALLOWED_ATTACHMENT_EXTENSIONS.map((e) => `.${e}`).join(',');

// Keep in step with spring.servlet.multipart.max-file-size (application.yml).
const MAX_ATTACHMENT_MB = 15;
const MAX_ATTACHMENT_BYTES = MAX_ATTACHMENT_MB * 1024 * 1024;
const formatMb = (bytes: number) => (bytes / (1024 * 1024)).toFixed(1);

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
    () =>
      API.protocolChecklist
        .getAttachments(protocol, checklistId)
        .then((list) => setRows(list)),
    [protocol, checklistId],
  );

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    API.protocolChecklist
      .getAttachments(protocol, checklistId)
      .then((list) => {
        if (!cancelled) setRows(list);
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
      (r) => r.checklistAttachmentId && isImage(r) && !thumbs[r.checklistAttachmentId],
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

  const handleUpload = async (file: File) => {
    // Legacy FREP303 requires a description before an attachment can be saved — surface it as
    // inline field validation rather than blocking the Browse button.
    if (!description.trim()) {
      setDescInvalid(true);
      return;
    }
    // Over-length is reported by the field's own counter; just don't start an upload that the
    // database would reject at the end of it.
    if (descLimitError) return;
    const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : '';
    if (!ALLOWED_ATTACHMENT_EXTENSIONS.includes(ext)) {
      const unsupported = ext ? `".${ext}" is not supported. ` : '';
      const allowed = ALLOWED_ATTACHMENT_EXTENSIONS.join(', ').toUpperCase();
      display({
        kind: 'error',
        title: 'Unsupported file type',
        subtitle: `${unsupported}Allowed types: ${allowed}.`,
        timeout: 8000,
      });
      return;
    }
    // Fail fast on size and empty files so the user isn't left waiting for an upload the server
    // will reject anyway. The server stays the source of truth for both (413 / 400).
    if (file.size === 0) {
      display({
        kind: 'error',
        title: 'File is empty',
        subtitle: `"${file.name}" contains no data. Choose a different file.`,
        timeout: 8000,
      });
      return;
    }
    if (file.size > MAX_ATTACHMENT_BYTES) {
      display({
        kind: 'error',
        title: 'File is too large',
        subtitle:
          `"${file.name}" is ${formatMb(file.size)} MB. The maximum is ${MAX_ATTACHMENT_MB} MB.`,
        timeout: 8000,
      });
      return;
    }
    setBusy(true);
    try {
      await API.protocolChecklist.uploadAttachment(
        protocol,
        checklistId,
        file,
        description.trim(),
      );
      await refreshRows();
      setDescription('');
      display({ kind: 'success', title: 'Attachment uploaded', timeout: 4000 });
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
      await API.protocolChecklist.deleteAttachment(protocol, checklistId, row.checklistAttachmentId);
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
      {rows.length > 0 && (
        <table className="rip-field-grid">
          <thead>
            <tr>
              <th scope="col">Preview</th>
              <th scope="col">File</th>
              <th scope="col">Description</th>
              <th scope="col">Type</th>
              <th scope="col" aria-label="Actions" />
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

      {canManage && (
        <div className="attach-card">
          <div className="attach-card__header">Upload file</div>
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
                const file = e.dataTransfer.files?.[0];
                if (file && !busy) void handleUpload(file);
              }}
            >
              <span className="attach-drop__icon">
                <Upload size={24} />
              </span>
              <p className="attach-drop__text">Select or drag and drop your file to upload.</p>
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
                hidden
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) void handleUpload(file);
                  e.target.value = '';
                }}
              />
            </div>
          </div>
        </div>
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

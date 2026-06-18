import { Download, TrashCan, Upload } from '@carbon/icons-react';
import { Button, SkeletonText, TextInput } from '@carbon/react';
import { useCallback, useEffect, useRef, useState, type FC } from 'react';

import ImagePreviewModal from '@/components/core/ImagePreviewModal';

import type { AttachmentRow } from '@/types/protocolChecklist';

import { useConfirm } from '@/context/confirm/useConfirm';
import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

/**
 * Checklist Attachments tab (legacy {@code checklistAttachment} / FREP_CHECKLIST_ATTACHMENTS) —
 * list, download, upload, and delete file attachments. Bytes move as base64 JSON (mirroring the
 * CHR photo flow) so the standard authenticated API client handles auth + CSRF.
 */

type Props = {
  protocol: string;
  checklistId: string;
  canEdit: boolean;
  submitted: boolean;
};

// True when an attachment is an image we can preview as a thumbnail.
function isImage(row: AttachmentRow): boolean {
  const mime = (row.mimeTypeCode || '').toLowerCase();
  if (mime.includes('image')) return true;
  return /\.(jpe?g|png|gif|bmp|tiff?|webp)$/.test((row.fileName || '').toLowerCase());
}

// Read a File as base64 (without the data: prefix).
function toBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result);
      resolve(result.slice(result.indexOf(',') + 1));
    };
    reader.onerror = () => reject(reader.error ?? new Error('File read failed'));
    reader.readAsDataURL(file);
  });
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
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const reportError = useCallback(
    (title: string, err: unknown) =>
      display({
        kind: 'error',
        title,
        subtitle: err instanceof Error ? err.message : 'Unknown error',
        timeout: 9000,
      }),
    [display],
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
          return [
            r.checklistAttachmentId as string,
            `data:${mime};base64,${content.data}`,
          ] as const;
        } catch {
          return null;
        }
      }),
    ).then((entries) => {
      if (cancelled) return;
      const loaded = entries.filter((e): e is readonly [string, string] => e !== null);
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
    setBusy(true);
    try {
      const data = await toBase64(file);
      const updated = await API.protocolChecklist.uploadAttachment(protocol, checklistId, {
        fileName: file.name,
        description: description.trim(),
        contentType: file.type,
        data,
      });
      setRows(updated);
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
      const updated = await API.protocolChecklist.deleteAttachment(
        protocol,
        checklistId,
        row.checklistAttachmentId,
      );
      setRows(updated);
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
              <th scope="col">Size</th>
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
                  <td>{row.fileSize || '—'}</td>
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
            <TextInput
              id="attach-description"
              className="attach-card__desc"
              labelText="Description"
              required
              invalid={descInvalid}
              invalidText="The description field must be entered."
              value={description}
              disabled={busy}
              onChange={(e) => {
                setDescription(e.target.value);
                if (e.target.value.trim()) setDescInvalid(false);
              }}
            />
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

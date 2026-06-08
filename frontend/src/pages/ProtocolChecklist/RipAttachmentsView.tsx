import { Download, TrashCan } from '@carbon/icons-react';
import { Button, FileUploaderButton, SkeletonText, TextInput } from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';

import type { AttachmentRow } from '@/types/protocolChecklist';

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
  const [rows, setRows] = useState<AttachmentRow[]>([]);
  const [description, setDescription] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

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

  const handleUpload = async (file: File) => {
    setBusy(true);
    try {
      const data = await toBase64(file);
      const updated = await API.protocolChecklist.uploadAttachment(protocol, checklistId, {
        fileName: file.name,
        description,
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
      {rows.length > 0 ? (
        <table className="rip-field-grid">
          <thead>
            <tr>
              <th scope="col">File</th>
              <th scope="col">Description</th>
              <th scope="col">Type</th>
              <th scope="col">Size</th>
              <th scope="col" aria-label="Actions" />
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => (
              <tr key={`att-${row.checklistAttachmentId ?? index}`}>
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
                      kind="danger--tertiary"
                      size="sm"
                      hasIconOnly
                      renderIcon={TrashCan}
                      iconDescription="Remove attachment"
                      disabled={busy}
                      onClick={() => void handleDelete(row)}
                    />
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <p>No attachments.</p>
      )}

      {canManage && (
        <div className="rip-form__add-evaluator">
          <TextInput
            id="attach-description"
            labelText="Description (optional)"
            value={description}
            disabled={busy}
            onChange={(e) => setDescription(e.target.value)}
          />
          <FileUploaderButton
            labelText="Add attachment"
            buttonKind="tertiary"
            disableLabelChanges
            disabled={busy}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
              const file = e.target.files?.[0];
              if (file) void handleUpload(file);
              e.target.value = '';
            }}
          />
        </div>
      )}
    </div>
  );
};

export default RipAttachmentsView;

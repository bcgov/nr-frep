import { Download, TrashCan, Upload } from '@carbon/icons-react';
import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useRef, useState, type FC } from 'react';

import ImagePreviewModal from '@/components/core/ImagePreviewModal';
import { TextField } from '@/pages/ChrChecklist/fields';

import type { Picture } from '@/types/chrChecklist';

import { useConfirm } from '@/context/confirm/useConfirm';
import { formatShortDate } from '@/utils/date';

/**
 * Build a displayable image src from a picture's `code`. Newly-added photos already carry a
 * full data URL; photos loaded from the server carry RAW base64 (legacy contract), so prepend a
 * `data:<mimeType>;base64,` prefix using the picture's mimeTypeCode. Returns undefined when there
 * is no image data to show.
 */
const photoSrc = (picture: Picture): string | undefined => {
  const code = picture.code;
  if (!code) return undefined;
  if (code.startsWith('data:')) return code;
  const mime =
    picture.mimeTypeCode && picture.mimeTypeCode.includes('image/')
      ? picture.mimeTypeCode
      : `image/${(picture.mimeTypeCode || 'jpeg').toLowerCase()}`;
  return `data:${mime};base64,${code}`;
};

// Client-side downscale/re-encode bounds, mirroring the legacy CHR upload: cap the full image at
// 800×1200 and re-encode to JPEG so full-resolution photos don't bloat the payload and storage.
const MAX_WIDTH = 800;
const MAX_HEIGHT = 1200;
const JPEG_QUALITY = 0.7;

const readDataUrl = (file: File): Promise<string> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = () => reject(reader.error ?? new Error('File read failed'));
    reader.readAsDataURL(file);
  });

const loadImage = (src: string): Promise<HTMLImageElement> =>
  new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('Image decode failed'));
    img.src = src;
  });

/**
 * Resize (never upscale) an image file to fit within {@link MAX_WIDTH}×{@link MAX_HEIGHT} and
 * re-encode it as JPEG. Non-image files, or anything we can't decode, are returned as-is. Returns
 * the picture fields (code is a data URL; the data: prefix is stripped at save time).
 */
const processFile = async (
  file: File,
): Promise<{ code: string; mimeTypeCode: string; fileName: string }> => {
  const original = await readDataUrl(file);
  if (!file.type.startsWith('image/')) {
    return { code: original, mimeTypeCode: file.type, fileName: file.name };
  }
  try {
    const img = await loadImage(original);
    const scale = Math.min(1, MAX_WIDTH / img.width, MAX_HEIGHT / img.height);
    const width = Math.max(1, Math.round(img.width * scale));
    const height = Math.max(1, Math.round(img.height * scale));
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) return { code: original, mimeTypeCode: file.type, fileName: file.name };
    ctx.drawImage(img, 0, 0, width, height);
    const code = canvas.toDataURL('image/jpeg', JPEG_QUALITY);
    const baseName = file.name.replace(/\.[^.]+$/, '');
    return { code, mimeTypeCode: 'image/jpeg', fileName: `${baseName}.jpg` };
  } catch {
    return { code: original, mimeTypeCode: file.type, fileName: file.name };
  }
};

/**
 * Attachments tab — mirrors the Biodiversity checklist Attachments tab: a table of existing photos
 * with download / delete, plus an upload card where the description/date are entered before
 * browsing. Each upload and delete persists immediately (no tab-level Save button); `onSave` posts
 * the full picture list and pulls back the server's truth (new row ids, etc.).
 */
const Photos: FC<{
  pictures: Picture[];
  onSave: (pictures: Picture[]) => Promise<boolean>;
  readOnly: boolean;
  busy: boolean;
}> = ({ pictures, onSave, readOnly, busy }) => {
  const confirm = useConfirm();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [description, setDescription] = useState('');
  const [date, setDate] = useState('');
  const [preview, setPreview] = useState<{ src: string; alt: string } | null>(null);

  // Compress/resize each selected file, append them all with the entered description/date, and
  // persist in a single save. Clear the upload fields once the save succeeds.
  const addFiles = async (files: File[]) => {
    if (files.length === 0) return;
    const additions: Picture[] = await Promise.all(
      files.map(async (file) => ({
        ...(await processFile(file)), // code (data URL; prefix stripped at save), mimeTypeCode, fileName
        description: description.trim(),
        date: date.trim(),
      })),
    );
    if (await onSave([...pictures, ...additions])) {
      setDescription('');
      setDate('');
    }
  };

  const removeAt = async (index: number) => {
    if (
      !(await confirm({
        title: 'Delete photo?',
        message: "Delete this photo? This can't be undone.",
      }))
    )
      return;
    await onSave(pictures.filter((_, i) => i !== index));
  };

  const download = (picture: Picture) => {
    const src = photoSrc(picture);
    if (!src) return;
    const link = document.createElement('a');
    link.href = src;
    link.download = picture.fileName || `photo-${picture.id ?? ''}`;
    link.click();
  };

  return (
    <div className="rip-form">
      {pictures.length > 0 && (
        <Table size="sm" className="bio-strata__table">
          <TableHead>
            <TableRow>
              <TableHeader>Photo</TableHeader>
              <TableHeader>Description</TableHeader>
              <TableHeader>Date</TableHeader>
              <TableHeader>Actions</TableHeader>
            </TableRow>
          </TableHead>
          <TableBody>
            {pictures.map((picture, index) => {
              const src = photoSrc(picture);
              return (
                <TableRow key={picture.id ?? `photo-${index}`}>
                  <TableCell>
                    {src ? (
                      <button
                        type="button"
                        className="image-thumb-button"
                        onClick={() =>
                          setPreview({
                            src,
                            alt: picture.description || picture.fileName || `Photo ${index + 1}`,
                          })
                        }
                      >
                        <img
                          className="chr-checklist__thumb image-thumb--clickable"
                          src={src}
                          alt={picture.description || picture.fileName || `Photo ${index + 1}`}
                        />
                      </button>
                    ) : (
                      <span className="chr-checklist__thumb chr-checklist__thumb--placeholder">
                        {picture.fileName || 'Saved photo'}
                      </span>
                    )}
                  </TableCell>
                  <TableCell>{picture.description || '—'}</TableCell>
                  <TableCell>{formatShortDate(picture.date) || '—'}</TableCell>
                  <TableCell>
                    <Button
                      kind="ghost"
                      size="sm"
                      hasIconOnly
                      renderIcon={Download}
                      iconDescription="Download"
                      disabled={busy}
                      onClick={() => download(picture)}
                    />
                    {!readOnly && (
                      <Button
                        kind="danger--ghost"
                        size="sm"
                        hasIconOnly
                        renderIcon={TrashCan}
                        iconDescription="Delete"
                        disabled={busy}
                        onClick={() => void removeAt(index)}
                      />
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}

      {!readOnly && (
        <div className="attach-card">
          <div className="attach-card__header">Upload file</div>
          <div className="attach-card__body">
            <TextField
              id="photo-description"
              labelText="Description"
              value={description}
              disabled={busy}
              onChange={setDescription}
            />
            {/* Plain text input (not DateField): this picker would mount in the always-rendered
                upload card during the page's load→content transition, which trips a Carbon DatePicker
                render loop. The other CHR date fields mount on user interaction and use DateField. */}
            <TextField
              id="photo-date"
              labelText="Date"
              placeholder="YYYY-MM-DD"
              value={date}
              disabled={busy}
              onChange={setDate}
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
                const files = Array.from(e.dataTransfer.files ?? []);
                if (files.length > 0 && !busy) void addFiles(files);
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
                accept="image/*"
                capture="environment"
                multiple
                hidden
                onChange={(e) => {
                  const files = Array.from(e.target.files ?? []);
                  if (files.length > 0) void addFiles(files);
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

export default Photos;

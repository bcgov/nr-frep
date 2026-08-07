import { Download, TrashCan, Upload } from '@carbon/icons-react';
import {
  Button,
  Pagination,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useEffect, useRef, useState, type FC } from 'react';

import ImagePreviewModal from '@/components/core/ImagePreviewModal';
import { DateField, TextAreaField } from '@/pages/ChrChecklist/fields';
import { requiredLabel } from '@/utils/requiredLabel';

import type { Picture } from '@/types/chrChecklist';

import { useConfirm } from '@/context/confirm/useConfirm';
import { useNotification } from '@/context/notification/useNotification';
import { ATTACHMENT_TEXT_LIMITS } from '@/pages/ChrChecklist/textLimits';
import { formatShortDate } from '@/utils/date';
import { overLimitError } from '@/utils/textLimits';

/**
 * Build a displayable src from a picture's own `code`, when it has one.
 *
 * Only two kinds of photo carry bytes locally now: one just captured (a data URL from the canvas
 * downscale), and one held in an offline copy. Photos read from the server carry metadata only and
 * are fetched individually from the content endpoint — see `resolveSrc` below.
 */
const photoSrc = (picture: Picture): string | undefined => {
  const code = picture.code;
  if (!code) return undefined;
  if (code.startsWith('data:')) return code;
  const mime = picture.mimeTypeCode?.includes('image/')
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
  /** Add the given new photos (each carries its bytes as a data-URL `code`). */
  onAdd: (additions: Picture[]) => Promise<boolean>;
  /** Remove one existing photo. */
  onDelete: (picture: Picture) => Promise<boolean>;
  /** Fetch one photo's bytes for display. Not called for photos that already carry a `code`. */
  fetchContent: (photoId: string) => Promise<Blob>;
  /** Pager state; `onPageChange` is 0-based. */
  page: number;
  pageSize: number;
  totalCount: number;
  onPageChange: (page: number, pageSize: number) => void;
  readOnly: boolean;
  busy: boolean;
  /** True when the Attachments tab is the selected tab. Gates the date picker's mount: Carbon mounts
   * every tab panel up front, and mounting the DatePicker during the page's initial load→content
   * transition trips a flatpickr render loop. Deferring to first activation mounts it post-load
   * (same timing as the Edit-triggered pickers on the other tabs), which is safe. */
  active: boolean;
}> = ({
  pictures,
  onAdd,
  onDelete,
  fetchContent,
  page,
  pageSize,
  totalCount,
  onPageChange,
  readOnly,
  busy,
  active,
}) => {
  const confirm = useConfirm();
  const { display } = useNotification();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  // Object URLs for photos fetched from the content endpoint, keyed by photo id. Revoked on unmount
  // so a long session paging through photos doesn't leak them.
  const [fetched, setFetched] = useState<Record<string, string>>({});

  useEffect(() => {
    let cancelled = false;
    const pending = pictures.filter((p) => p.id && !p.code && !fetched[p.id]);
    if (pending.length === 0) return undefined;
    void Promise.all(
      pending.map(async (picture) => {
        try {
          const blob = await fetchContent(picture.id as string);
          return [picture.id as string, URL.createObjectURL(blob)] as [string, string];
        } catch {
          return null; // one unreadable photo must not blank the whole tab
        }
      }),
    ).then((entries) => {
      if (cancelled) return;
      const loaded = entries.filter((e): e is [string, string] => e !== null);
      if (loaded.length > 0) setFetched((prev) => ({ ...prev, ...Object.fromEntries(loaded) }));
    });
    return () => {
      cancelled = true;
    };
  }, [pictures, fetched, fetchContent]);

  useEffect(
    () => () => {
      Object.values(fetched).forEach((url) => URL.revokeObjectURL(url));
    },
    // Intentionally on unmount only; revoking on every change would break images still rendered.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  /** A photo's src: its own bytes when it has them, otherwise the fetched object URL. */
  const resolveSrc = (picture: Picture): string | undefined =>
    photoSrc(picture) ?? (picture.id ? fetched[picture.id] : undefined);

  const [description, setDescription] = useState('');
  const [descriptionInvalid, setDescriptionInvalid] = useState(false);
  const [date, setDate] = useState('');
  const [preview, setPreview] = useState<{ src: string; alt: string } | null>(null);

  // Compress/resize each selected file, append them all with the entered description/date, and
  // persist in a single save. Clear the upload fields once the save succeeds.
  const addFiles = async (files: File[]) => {
    if (files.length === 0) return;
    // Description is required for a new photo (the backend rejects a blank one). Flag it here so the
    // user gets immediate inline feedback instead of a "Bad Request" after the round-trip.
    if (!description.trim()) {
      setDescriptionInvalid(true);
      return;
    }
    // Over-length is reported by the field's own counter; just don't start an upload that the
    // database would reject at the end of it.
    if (overLimitError(description, ATTACHMENT_TEXT_LIMITS.description)) return;
    setDescriptionInvalid(false);
    // Photos are image-only: the attachment table stores a 3-char MIME_TYPE_CODE with a FK to the code
    // table, so a non-image would blow up on save (value-too-large / FK). The native picker uses
    // accept="image/jpeg,image/png,image/gif,image/bmp", but drag-and-drop bypasses it — so re-check here.
    // TIFF is excluded: browsers can't decode it, so the downscale below would silently keep the
    // full-resolution original and it could never render. Matches ALLOWED_IMAGE_CODES server-side.
    const images = files.filter(
      (file) => file.type.startsWith('image/') && !file.type.includes('tif'),
    );
    if (images.length < files.length) {
      display({
        kind: 'error',
        title: 'Only image files can be uploaded',
        subtitle: 'Photos must be image files (JPG, PNG, GIF, BMP). Other files were skipped.',
        timeout: 8000,
      });
    }
    if (images.length === 0) return;
    const additions: Picture[] = await Promise.all(
      images.map(async (file) => ({
        ...(await processFile(file)), // code (data URL; prefix stripped at save), mimeTypeCode, fileName
        description: description.trim(),
        date: date.trim(),
      })),
    );
    // Only the new photos are sent: each is created individually by the photo endpoint, so the
    // existing set is never resubmitted (and so can never be deleted by omission).
    if (await onAdd(additions)) {
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
    await onDelete(pictures[index]);
  };

  const download = (picture: Picture) => {
    const src = resolveSrc(picture);
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
              const src = resolveSrc(picture);
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

      {totalCount > pageSize && (
        <Pagination
          page={page + 1}
          pageSize={pageSize}
          pageSizes={[10, 25, 50]}
          totalItems={totalCount}
          disabled={busy}
          onChange={({ page: nextPage, pageSize: nextSize }) => {
            // Carbon is 1-based, the API 0-based; a size change resets to the first page.
            onPageChange(nextSize === pageSize ? nextPage - 1 : 0, nextSize);
          }}
        />
      )}

      {!readOnly && (
        <div className="attach-card">
          <div className="attach-card__header">Upload file</div>
          <div className="attach-card__body">
            <div className="attach-card__fields">
              <div className="attach-card__field attach-card__field--date">
                {/* The Carbon DatePicker is mounted only once the Attachments tab is active (see the
                    `active` prop) — mounting it during the page's initial load trips a flatpickr
                    render loop, since Carbon mounts every tab panel up front. */}
                {active && (
                  <DateField
                    id="photo-date"
                    labelText="Date"
                    value={date}
                    disabled={busy}
                    onChange={setDate}
                  />
                )}
              </div>
              <div className="attach-card__field attach-card__field--desc">
                <TextAreaField
                  id="photo-description"
                  labelText={requiredLabel('Description', true)}
                  value={description}
                  rows={3}
                  disabled={busy}
                  limit={ATTACHMENT_TEXT_LIMITS.description}
                  invalid={descriptionInvalid}
                  invalidText="Enter a description before uploading a photo."
                  onChange={(v) => {
                    setDescription(v);
                    if (v.trim()) setDescriptionInvalid(false);
                  }}
                />
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
                accept="image/jpeg,image/png,image/gif,image/bmp"
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

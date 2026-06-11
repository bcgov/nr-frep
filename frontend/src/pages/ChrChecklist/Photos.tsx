import { TrashCan, Upload } from '@carbon/icons-react';
import { Button } from '@carbon/react';
import { useRef, useState, type FC } from 'react';

import { TextField } from '@/pages/ChrChecklist/fields';

import type { Picture } from '@/types/chrChecklist';

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

/**
 * Section — attachments: capture photos via the Biodiversity drag-and-drop upload card, store
 * base64, edit description/date, remove. Existing photos keep their thumbnails below the card.
 */
const Photos: FC<{
  pictures: Picture[];
  onChange: (pictures: Picture[]) => void;
  onSave: () => Promise<boolean>;
  readOnly: boolean;
  busy: boolean;
}> = ({ pictures, onChange, onSave, readOnly, busy }) => {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);

  const patchAt = (index: number, patch: Partial<Picture>) =>
    onChange(pictures.map((p, i) => (i === index ? { ...p, ...patch } : p)));
  const removeAt = (index: number) => onChange(pictures.filter((_, i) => i !== index));

  const addFile = (file: File) => {
    const reader = new FileReader();
    reader.onload = () => {
      onChange([
        ...pictures,
        {
          code: reader.result as string, // data URL; prefix stripped at save time
          mimeTypeCode: file.type,
          fileName: file.name,
          description: '',
          date: '',
        },
      ]);
    };
    reader.readAsDataURL(file);
  };

  return (
    <div className="rip-form">
      {!readOnly && (
        <div className="protocol-checklist__section-actions">
          <Button size="lg" disabled={busy} onClick={() => void onSave()}>
            Save
          </Button>
        </div>
      )}
      {!readOnly && (
        <div className="attach-card">
          <div className="attach-card__header">Upload file</div>
          <div className="attach-card__body">
            <div
              className={`attach-drop${dragOver ? ' attach-drop--over' : ''}`}
              onDragOver={(e) => {
                e.preventDefault();
                setDragOver(true);
              }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(e) => {
                e.preventDefault();
                setDragOver(false);
                const file = e.dataTransfer.files?.[0];
                if (file) addFile(file);
              }}
            >
              <span className="attach-drop__icon">
                <Upload size={24} />
              </span>
              <p className="attach-drop__text">Select or drag and drop your file to upload.</p>
              <Button kind="primary" size="lg" onClick={() => fileInputRef.current?.click()}>
                Browse files
              </Button>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                capture="environment"
                hidden
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) addFile(file);
                  e.target.value = '';
                }}
              />
            </div>
          </div>
        </div>
      )}

      {pictures.length === 0 && <p>No photos attached.</p>}
      {pictures.map((picture, index) => {
        const src = photoSrc(picture);
        return (
          <fieldset key={picture.id ?? `photo-${index}`} className="rip-form__group">
            <legend>Photo {index + 1}</legend>
            <div className="chr-checklist__photo">
              {src ? (
                <img
                  className="chr-checklist__thumb"
                  src={src}
                  alt={picture.description || picture.fileName || `Photo ${index + 1}`}
                />
              ) : (
                <span className="chr-checklist__thumb chr-checklist__thumb--placeholder">
                  {picture.fileName || 'Saved photo'}
                </span>
              )}
              <div className="rip-form__grid">
                <TextField
                  id={`photo-desc-${index}`}
                  labelText="Description"
                  value={picture.description}
                  disabled={readOnly}
                  onChange={(v) => patchAt(index, { description: v })}
                />
                <TextField
                  id={`photo-date-${index}`}
                  labelText="Date"
                  placeholder="YYYY-MM-DD"
                  value={picture.date}
                  disabled={readOnly}
                  onChange={(v) => patchAt(index, { date: v })}
                />
              </div>
            </div>
            {!readOnly && (
              <Button
                kind="danger--ghost"
                size="sm"
                renderIcon={TrashCan}
                onClick={() => removeAt(index)}
              >
                Remove photo
              </Button>
            )}
          </fieldset>
        );
      })}
    </div>
  );
};

export default Photos;

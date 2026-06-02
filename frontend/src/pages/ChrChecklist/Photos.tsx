import { TrashCan } from '@carbon/icons-react';
import { Button, Column, Grid, Tile } from '@carbon/react';
import { useRef, type ChangeEvent, type FC } from 'react';

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

/** Section — photos: capture via file/camera input, store base64, edit description/date, remove. */
const Photos: FC<{
  pictures: Picture[];
  onChange: (pictures: Picture[]) => void;
  readOnly: boolean;
}> = ({ pictures, onChange, readOnly }) => {
  const fileInputRef = useRef<HTMLInputElement>(null);

  const patchAt = (index: number, patch: Partial<Picture>) =>
    onChange(pictures.map((p, i) => (i === index ? { ...p, ...patch } : p)));
  const removeAt = (index: number) => onChange(pictures.filter((_, i) => i !== index));

  const onFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
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
    event.target.value = '';
  };

  return (
    <Grid fullWidth className="chr-checklist__section">
      <Column sm={4} md={8} lg={16}>
        {pictures.length === 0 && <p>No photos attached.</p>}
        {pictures.map((picture, index) => {
          const src = photoSrc(picture);
          return (
            <Tile key={picture.id ?? `photo-${index}`} className="chr-checklist__row">
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
                <div className="chr-checklist__form">
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
                  kind="danger--tertiary"
                  size="sm"
                  renderIcon={TrashCan}
                  onClick={() => removeAt(index)}
                >
                  Remove photo
                </Button>
              )}
            </Tile>
          );
        })}
        {!readOnly && (
          <>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              capture="environment"
              hidden
              onChange={onFile}
            />
            <Button kind="tertiary" onClick={() => fileInputRef.current?.click()}>
              Add photo
            </Button>
          </>
        )}
      </Column>
    </Grid>
  );
};

export default Photos;

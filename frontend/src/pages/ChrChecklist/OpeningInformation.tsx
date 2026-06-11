import { Edit } from '@carbon/icons-react';
import { Button } from '@carbon/react';
import { useState, type FC } from 'react';

import { IndicatorCheckbox, TextAreaField, TextField } from '@/pages/ChrChecklist/fields';

import type { CheckList } from '@/types/chrChecklist';

const RoField: FC<{ label: string; value?: string }> = ({ label, value }) => (
  <div className="protocol-checklist__field">
    <span className="protocol-checklist__label">{label}</span>
    <span className="protocol-checklist__value">{value || '—'}</span>
  </div>
);

const yesNo = (v?: string) => (v === 'true' ? 'Yes' : 'No');

type Draft = Pick<CheckList, 'evaluationDate' | 'firstNationName' | 'generalLocation' | 'targeted'>;

/**
 * Section 1 — opening information. Read-only by default with an Edit / Save / Cancel toggle,
 * mirroring the Biodiversity Opening tab. Save persists the whole CHR checklist via `onSave`.
 */
const OpeningInformation: FC<{
  value: CheckList;
  onSave: (patch: Partial<CheckList>) => Promise<boolean>;
  readOnly: boolean;
  busy: boolean;
}> = ({ value, onSave, readOnly, busy }) => {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<Draft>({});

  const beginEdit = () => {
    setDraft({
      evaluationDate: value.evaluationDate,
      firstNationName: value.firstNationName,
      generalLocation: value.generalLocation,
      targeted: value.targeted,
    });
    setEditing(true);
  };
  const save = async () => {
    if (await onSave(draft)) setEditing(false);
  };

  return (
    <div className="rip-form">
      <div className="protocol-checklist__section-actions">
        {!editing && !readOnly && (
          <Button kind="tertiary" size="lg" disabled={busy} onClick={beginEdit}>
            <span className="protocol-checklist__edit-label">
              <Edit /> Edit
            </span>
          </Button>
        )}
        {editing && (
          <>
            <Button kind="ghost" size="lg" disabled={busy} onClick={() => setEditing(false)}>
              Cancel
            </Button>
            <Button size="lg" disabled={busy} onClick={() => void save()}>
              Save
            </Button>
          </>
        )}
      </div>

      <fieldset className="rip-form__group">
        <legend>Site</legend>
        <div className="rip-form__grid">
          <RoField label="District" value={value.district ?? value.orgUnitName} />
          <RoField label="Opening ID" value={value.openingID} />
          <RoField label="Licensee" value={value.licensee} />
          <RoField label="Cutting permit" value={value.cuttingPermit} />
          <RoField label="Block" value={value.block} />
          <RoField label="Client" value={value.client} />
          <RoField label="Year of harvest" value={value.yearOfHarvest} />
        </div>
      </fieldset>

      <fieldset className="rip-form__group">
        <legend>Evaluation</legend>
        {editing ? (
          <>
            <div className="rip-form__grid">
              <TextField
                id="chr-evaluation-date"
                labelText="Evaluation date"
                placeholder="YYYY-MM-DD"
                value={draft.evaluationDate}
                onChange={(v) => setDraft((d) => ({ ...d, evaluationDate: v }))}
              />
              <TextField
                id="chr-first-nation"
                labelText="First Nation place name"
                value={draft.firstNationName}
                onChange={(v) => setDraft((d) => ({ ...d, firstNationName: v }))}
              />
              <IndicatorCheckbox
                id="chr-targeted"
                labelText="Targeted site"
                value={draft.targeted}
                onToggle={(v) => setDraft((d) => ({ ...d, targeted: v }))}
              />
            </div>
            <TextAreaField
              id="chr-general-location"
              labelText="General location"
              value={draft.generalLocation}
              onChange={(v) => setDraft((d) => ({ ...d, generalLocation: v }))}
            />
          </>
        ) : (
          <div className="rip-form__grid">
            <RoField label="Evaluation date" value={value.evaluationDate} />
            <RoField label="First Nation place name" value={value.firstNationName} />
            <RoField label="Targeted site" value={yesNo(value.targeted)} />
            <RoField label="General location" value={value.generalLocation} />
          </div>
        )}
      </fieldset>
    </div>
  );
};

export default OpeningInformation;

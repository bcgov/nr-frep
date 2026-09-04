import { Edit } from '@carbon/icons-react';
import { Button } from '@carbon/react';
import { useState, type FC } from 'react';

import {
  DateField,
  IndicatorCheckbox,
  TextAreaField,
  TextField,
} from '@/pages/ChrChecklist/fields';
import RequiredLegend from '@/pages/ProtocolChecklist/RequiredLegend';
import { requiredLabel } from '@/utils/requiredLabel';

import type { CheckList } from '@/types/chrChecklist';

import { useAuth } from '@/context/auth/useAuth';
import {
  openingFormatErrors,
  openingRequiredErrors,
} from '@/pages/ChrChecklist/checklistValidation';
import { OPENING_TEXT_LIMITS } from '@/pages/ChrChecklist/textLimits';
import { formatShortDate } from '@/utils/date';
import ActionButton from '@/components/core/ActionButton';

const RoField: FC<{ label: string; value?: string }> = ({ label, value }) => (
  <div className="protocol-checklist__field">
    <span className="protocol-checklist__label">{label}</span>
    <span className="protocol-checklist__value">{value || '—'}</span>
  </div>
);

const yesNo = (v?: string) => (v === 'true' ? 'Yes' : 'No');

type Draft = Pick<
  CheckList,
  'evaluationDate' | 'firstNationName' | 'generalLocation' | 'targeted' | 'assessedBy'
>;

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
  const { user } = useAuth();
  const [editing, setEditing] = useState(false);
  // Errors stay hidden until a save is attempted on this edit.
  const [showErrors, setShowErrors] = useState(false);
  const [draft, setDraft] = useState<Draft>({});

  // "Assessed by" is read-only and is set ONLY when the user explicitly assigns it to themselves via
  // "Assign it to me" (which takes effect on save). It's never auto-defaulted to the current user, so
  // it shows "—" until assigned — making the assignment a deliberate action that can't be silently
  // missed (and submit requires it).
  const me = user?.providerUsername;
  const assessedBy = value.assessedBy;
  const editAssessedBy = draft.assessedBy;
  // Show the FAM-resolved "Name (USERID)" for the persisted evaluator; after a self-assign the draft
  // holds the raw userid (no resolved name yet) so fall back to it until the save round-trips.
  const editAssessedByDisplay =
    editAssessedBy === assessedBy ? value.assessedByName || editAssessedBy : editAssessedBy;
  const canAssignToMe = Boolean(me) && editAssessedBy !== me;

  const beginEdit = () => {
    setDraft({
      evaluationDate: value.evaluationDate,
      firstNationName: value.firstNationName,
      generalLocation: value.generalLocation,
      targeted: value.targeted,
      assessedBy: value.assessedBy,
    });
    setShowErrors(false);
    setEditing(true);
  };
  // Validation runs live off the draft, but is only *displayed* once a save has been attempted —
  // opening an incomplete tab should not greet the user with errors they have not been asked to fix
  // yet (same gate as the Biodiversity tabs). `fieldErrors` drives the rendering, so every call site
  // below is gated at once; `blockingErrors` drives the save guard.
  const allErrors: Record<string, string> = editing
    ? { ...openingRequiredErrors(draft), ...openingFormatErrors(draft) }
    : {};
  // Only free text the column cannot store stops the save. A required field left blank is marked,
  // counted on the tab and blocks submit — but a part-finished Opening is a legitimate saved state.
  const blockingErrors = editing ? openingFormatErrors(draft) : {};
  const hasBlockingErrors = Object.keys(blockingErrors).length > 0;
  const fieldErrors = showErrors ? allErrors : {};
  const save = async () => {
    // First point the user has asked for the tab to be complete — reveal the errors now. Blank
    // required fields are shown but do not stop the save; only unstorable values do.
    setShowErrors(true);
    if (hasBlockingErrors) return;
    if (await onSave(draft)) setEditing(false);
  };

  return (
    <div className="rip-form">
      <div className="protocol-checklist__section-actions">
        {!editing && !readOnly && (
          <Button kind="tertiary" size="lg" disabled={busy} onClick={beginEdit}>
            <span className="protocol-checklist__edit-label">
              Edit <Edit />
            </span>
          </Button>
        )}
        {editing && (
          <>
            <Button
              kind="ghost"
              size="lg"
              disabled={busy}
              onClick={() => {
                setShowErrors(false);
                setEditing(false);
              }}
            >
              Cancel
            </Button>
            <ActionButton busy={busy} onClick={() => void save()} />
          </>
        )}
      </div>
      {/* Only while editing — the read-only view marks nothing required. */}
      {editing && <RequiredLegend />}

      <fieldset className="rip-form__group">
        <legend>Evaluation</legend>
        {editing ? (
          <>
            <div className="rip-form__grid">
              <DateField
                id="chr-evaluation-date"
                labelText={requiredLabel('Evaluation date', true)}
                value={draft.evaluationDate}
                invalid={Boolean(fieldErrors.evaluationDate)}
                invalidText={fieldErrors.evaluationDate}
                onChange={(v) => setDraft((d) => ({ ...d, evaluationDate: v }))}
              />
              <div className="protocol-checklist__field chr-checklist__assessed-by__field">
                <span className="protocol-checklist__label">
                  {requiredLabel('Evaluator', true)}
                </span>
                <span className="protocol-checklist__value chr-checklist__assessed-by__row">
                  {editAssessedBy && <span>{editAssessedByDisplay}</span>}
                  {canAssignToMe && (
                    <button
                      type="button"
                      className="chr-checklist__assessed-by__assign"
                      onClick={() => setDraft((d) => ({ ...d, assessedBy: me }))}
                    >
                      Assign it to me
                    </button>
                  )}
                  {!editAssessedBy && !canAssignToMe && <span>—</span>}
                </span>
                {fieldErrors.assessedBy && (
                  <span className="chr-checklist__assessed-by__error">
                    {fieldErrors.assessedBy}
                  </span>
                )}
              </div>
              <TextField
                id="chr-first-nation"
                labelText="First Nations' Place Name or Block Name"
                value={draft.firstNationName}
                maxLength={200}
                onChange={(v) => setDraft((d) => ({ ...d, firstNationName: v }))}
              />
              <div className="chr-checklist__grid-check">
                <IndicatorCheckbox
                  id="chr-targeted"
                  labelText="Targeted site"
                  value={draft.targeted}
                  onToggle={(v) => setDraft((d) => ({ ...d, targeted: v }))}
                />
              </div>
            </div>
            <TextAreaField
              id="chr-general-location"
              labelText={requiredLabel('General location', true)}
              value={draft.generalLocation}
              limit={OPENING_TEXT_LIMITS.generalLocation}
              invalid={Boolean(fieldErrors.generalLocation)}
              invalidText={fieldErrors.generalLocation}
              onChange={(v) => setDraft((d) => ({ ...d, generalLocation: v }))}
            />
          </>
        ) : (
          <div className="rip-form__grid">
            <RoField label="Evaluation date" value={formatShortDate(value.evaluationDate)} />
            <RoField label="Evaluator" value={value.assessedByName || assessedBy} />
            <RoField
              label="First Nations' Place Name or Block Name"
              value={value.firstNationName}
            />
            <RoField label="Targeted site" value={yesNo(value.targeted)} />
            <RoField label="General location" value={value.generalLocation} />
          </div>
        )}
      </fieldset>
    </div>
  );
};

export default OpeningInformation;

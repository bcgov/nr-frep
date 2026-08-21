import { Edit } from '@carbon/icons-react';
import { Button, Tag } from '@carbon/react';
import { useState, type FC } from 'react';

import { CodeSelect, IndicatorCheckbox, TextAreaField } from '@/pages/ChrChecklist/fields';
import { requiredLabel } from '@/utils/requiredLabel';

import type { CheckList } from '@/types/chrChecklist';

import {
  blockSummaryFormatErrors,
  blockSummaryRequiredErrors,
} from '@/pages/ChrChecklist/checklistValidation';
import { RATING_CODES, calculateMrvaRatingCode } from '@/pages/ChrChecklist/codeLists';
import { BLOCK_TEXT_LIMITS } from '@/pages/ChrChecklist/textLimits';

const RoField: FC<{ label: string; value?: string }> = ({ label, value }) => (
  <div className="protocol-checklist__field">
    <span className="protocol-checklist__label">{label}</span>
    <span className="protocol-checklist__value">{value || '—'}</span>
  </div>
);

const yesNo = (v?: string) => (v === 'true' ? 'Yes' : 'No');
const isYes = (v?: string) => v === 'true';

const ratingLabel = (code?: string) => RATING_CODES.find((r) => r.code === code)?.label ?? code;

// Verbatim question text from the legacy CHR Block Summary (frep-frontend BlockSummary.vue).
const Q8_LABEL =
  'Were there operational factors that limited CHR management options on this block?';
const Q9_LABEL =
  'Were there management strategies and/or practices used on this block that were particularly effective?';
const Q10_LABEL =
  'Are there management strategies and/or practices that could have been used to reduce the impact on this CHR values on this block?';
const RATING_QUESTION =
  'To what extent did practices on this block maintain CHR values given the recommendations and opportunities that were available?';

/** Friendly labels for the computed MRVA rating code (legacy mrvaRatingDesc). */
const MRVA_LABELS: Record<string, string> = {
  NUL: 'NUL',
  HIGH: 'High',
  MEDIUM: 'Medium',
  LOW: 'Low',
  VERYLOW: 'Very Low',
};

type Draft = Pick<
  CheckList,
  | 'q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock'
  | 'q8Comments'
  | 'q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues'
  | 'q9Comments'
  | 'q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock'
  | 'q10Comments'
  | 'rating'
  | 'ratingRationale'
>;

/**
 * One Q8/Q9/Q10 row in edit mode: the Yes/No checkbox plus a description box that appears (and is
 * required) only when the answer is Yes; a spacer keeps the 2-column rhythm otherwise.
 */
const EditQaRow: FC<{
  id: string;
  labelText: string;
  value?: string;
  onToggle: (v: string) => void;
  commentId: string;
  commentLabel: string;
  commentValue?: string;
  commentError?: string;
  /** Byte limit of the backing column (see textLimits.ts) — drives the counter. */
  commentLimit: number;
  onCommentChange: (v: string) => void;
}> = ({
  id,
  labelText,
  value,
  onToggle,
  commentId,
  commentLabel,
  commentValue,
  commentError,
  commentLimit,
  onCommentChange,
}) => (
  <>
    <IndicatorCheckbox id={id} labelText={labelText} value={value} onToggle={onToggle} />
    {isYes(value) ? (
      <TextAreaField
        id={commentId}
        labelText={requiredLabel(commentLabel, true)}
        value={commentValue}
        limit={commentLimit}
        invalid={Boolean(commentError)}
        invalidText={commentError}
        onChange={onCommentChange}
      />
    ) : (
      <div className="chr-block-qa__spacer" aria-hidden="true" />
    )}
  </>
);

/** Read-only counterpart of {@link EditQaRow}: Yes/No plus the description, shown only when Yes. */
const ReadOnlyQaRow: FC<{
  label: string;
  value?: string;
  commentLabel: string;
  comment?: string;
}> = ({ label, value, commentLabel, comment }) => (
  <>
    <RoField label={label} value={yesNo(value)} />
    {isYes(value) ? (
      <RoField label={commentLabel} value={comment} />
    ) : (
      <div className="chr-block-qa__spacer" aria-hidden="true" />
    )}
  </>
);

/**
 * Section — block summary: Q8/Q9/Q10, block rating + rationale, computed MRVA, comments.
 * Read-only by default with an Edit / Save / Cancel toggle, mirroring the Biodiversity Opening tab.
 * Save persists the whole CHR checklist via `onSave`.
 */
const BlockSummary: FC<{
  value: CheckList;
  onSave: (patch: Partial<CheckList>) => Promise<boolean>;
  readOnly: boolean;
  busy: boolean;
}> = ({ value, onSave, readOnly, busy }) => {
  const [editing, setEditing] = useState(false);
  // Errors stay hidden until a save is attempted on this edit.
  const [showErrors, setShowErrors] = useState(false);
  const [draft, setDraft] = useState<Draft>({});
  // While editing, preview the MRVA off the edited rating so it updates live with the Rating select
  // (features aren't editable here); otherwise show the saved value.
  const mrva = calculateMrvaRatingCode(
    editing ? (draft.rating ?? value.rating) : value.rating,
    value.features,
  );

  const beginEdit = () => {
    setDraft({
      q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock:
        value.q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock,
      q8Comments: value.q8Comments,
      q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues:
        value.q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues,
      q9Comments: value.q9Comments,
      q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock:
        value.q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock,
      q10Comments: value.q10Comments,
      rating: value.rating,
      ratingRationale: value.ratingRationale,
    });
    setShowErrors(false);
    setEditing(true);
  };
  // Validation runs live off the draft, but is only *displayed* once a save has been attempted —
  // opening an incomplete tab should not greet the user with errors they have not been asked to fix
  // yet (same gate as the Biodiversity tabs). `allErrors` drives the save guard, `fieldErrors` the
  // rendering, so every call site below is gated at once.
  const allErrors: Record<string, string> = editing
    ? { ...blockSummaryRequiredErrors(draft), ...blockSummaryFormatErrors(draft) }
    : {};
  // Only free text the column cannot store stops the save. A blank Rating or a missing Q8/Q9/Q10
  // description is marked, counted on the tab and blocks submit, but saves happily.
  const blockingErrors = editing ? blockSummaryFormatErrors(draft) : {};
  const hasBlockingErrors = Object.keys(blockingErrors).length > 0;
  const fieldErrors = showErrors ? allErrors : {};
  const save = async () => {
    // First point the user has asked for the tab to be complete — reveal the errors now. Blank
    // required fields are shown but do not stop the save; only unstorable values do.
    setShowErrors(true);
    if (hasBlockingErrors) return;
    if (await onSave(draft)) setEditing(false);
  };
  const set = (patch: Partial<Draft>) => setDraft((d) => ({ ...d, ...patch }));

  const mrvaCell = (
    <div className="protocol-checklist__field">
      <span className="protocol-checklist__label">MRVA rating (computed)</span>
      <span className="protocol-checklist__value">
        <Tag type="blue" size="sm">
          {mrva ? (MRVA_LABELS[mrva] ?? mrva) : '—'}
        </Tag>
      </span>
      <details className="chr-mrva-help">
        <summary>How is the MRVA rating determined?</summary>
        <p>
          The Most Restrictive Value Assessment is derived from the block rating (and the
          per-feature ratings). NA = not applicable.
        </p>
        <table>
          <thead>
            <tr>
              <th>Block rating</th>
              <th>MRVA</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Don&apos;t know</td>
              <td>NUL</td>
            </tr>
            <tr>
              <td>Poorly / Very Poorly</td>
              <td>High</td>
            </tr>
            <tr>
              <td>Moderately</td>
              <td>Medium if any feature is Poorly/Very Poorly, otherwise Low</td>
            </tr>
            <tr>
              <td>Well</td>
              <td>Low if any feature is Poorly/Very Poorly, otherwise Very Low</td>
            </tr>
            <tr>
              <td>Very Well</td>
              <td>Very Low</td>
            </tr>
          </tbody>
        </table>
      </details>
    </div>
  );

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
            <Button size="lg" disabled={busy} onClick={() => void save()}>
              Save
            </Button>
          </>
        )}
      </div>

      {editing ? (
        <>
          <fieldset className="rip-form__group">
            <legend>Operational review</legend>
            {/* Each question sits beside its description; the description appears only when the
                question is Yes (legacy parity), otherwise a spacer keeps the 2-column rhythm. */}
            <div className="chr-block-qa">
              <EditQaRow
                id="chr-q8"
                labelText={Q8_LABEL}
                value={
                  draft.q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock
                }
                onToggle={(v) =>
                  set({
                    q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock: v,
                  })
                }
                commentId="chr-q8-comments"
                commentLabel="Q8 description"
                commentValue={draft.q8Comments}
                commentError={fieldErrors.q8Comments}
                commentLimit={BLOCK_TEXT_LIMITS.q8Comments}
                onCommentChange={(v) => set({ q8Comments: v })}
              />
              <EditQaRow
                id="chr-q9"
                labelText={Q9_LABEL}
                value={
                  draft.q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues
                }
                onToggle={(v) =>
                  set({
                    q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues:
                      v,
                  })
                }
                commentId="chr-q9-comments"
                commentLabel="Q9 description"
                commentValue={draft.q9Comments}
                commentError={fieldErrors.q9Comments}
                commentLimit={BLOCK_TEXT_LIMITS.q9Comments}
                onCommentChange={(v) => set({ q9Comments: v })}
              />
              <EditQaRow
                id="chr-q10"
                labelText={Q10_LABEL}
                value={
                  draft.q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock
                }
                onToggle={(v) =>
                  set({
                    q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock:
                      v,
                  })
                }
                commentId="chr-q10-comments"
                commentLabel="Q10 description"
                commentValue={draft.q10Comments}
                commentError={fieldErrors.q10Comments}
                commentLimit={BLOCK_TEXT_LIMITS.q10Comments}
                onCommentChange={(v) => set({ q10Comments: v })}
              />
            </div>
          </fieldset>

          <fieldset className="rip-form__group">
            <legend>Block rating</legend>
            <p className="rip-form__question">{RATING_QUESTION}</p>
            <div className="rip-form__grid">
              <CodeSelect
                id="chr-block-rating"
                labelText={requiredLabel('Rating', true)}
                value={draft.rating}
                options={RATING_CODES}
                includeBlank
                invalid={Boolean(fieldErrors.rating)}
                invalidText={fieldErrors.rating}
                onChange={(v) => set({ rating: v })}
              />
              {mrvaCell}
            </div>
            <TextAreaField
              id="chr-rating-rationale"
              labelText="Rating rationale"
              value={draft.ratingRationale}
              limit={BLOCK_TEXT_LIMITS.ratingRationale}
              invalid={Boolean(fieldErrors.ratingRationale)}
              invalidText={fieldErrors.ratingRationale}
              onChange={(v) => set({ ratingRationale: v })}
            />
          </fieldset>
        </>
      ) : (
        <>
          <fieldset className="rip-form__group">
            <legend>Operational review</legend>
            <div className="chr-block-qa">
              <ReadOnlyQaRow
                label={Q8_LABEL}
                value={
                  value.q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock
                }
                commentLabel="Q8 description"
                comment={value.q8Comments}
              />
              <ReadOnlyQaRow
                label={Q9_LABEL}
                value={
                  value.q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues
                }
                commentLabel="Q9 description"
                comment={value.q9Comments}
              />
              <ReadOnlyQaRow
                label={Q10_LABEL}
                value={
                  value.q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock
                }
                commentLabel="Q10 description"
                comment={value.q10Comments}
              />
            </div>
          </fieldset>

          <fieldset className="rip-form__group">
            <legend>Block rating</legend>
            <p className="rip-form__question">{RATING_QUESTION}</p>
            <div className="rip-form__grid">
              <RoField label="Rating" value={ratingLabel(value.rating)} />
              {mrvaCell}
            </div>
            <RoField label="Rating rationale" value={value.ratingRationale} />
          </fieldset>
        </>
      )}
    </div>
  );
};

export default BlockSummary;

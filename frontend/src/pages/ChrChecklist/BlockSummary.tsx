import { Edit } from '@carbon/icons-react';
import { Button, Tag } from '@carbon/react';
import { useState, type FC } from 'react';

import { CodeSelect, IndicatorCheckbox, TextAreaField } from '@/pages/ChrChecklist/fields';

import type { CheckList } from '@/types/chrChecklist';

import { RATING_CODES, calculateMrvaRatingCode } from '@/pages/ChrChecklist/codeLists';

const RoField: FC<{ label: string; value?: string }> = ({ label, value }) => (
  <div className="protocol-checklist__field">
    <span className="protocol-checklist__label">{label}</span>
    <span className="protocol-checklist__value">{value || '—'}</span>
  </div>
);

const yesNo = (v?: string) => (v === 'true' ? 'Yes' : 'No');

const ratingLabel = (code?: string) => RATING_CODES.find((r) => r.code === code)?.label ?? code;

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
  | 'commentaires'
>;

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
  const [draft, setDraft] = useState<Draft>({});
  const mrva = calculateMrvaRatingCode(value.rating, value.features);

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
      commentaires: value.commentaires,
    });
    setEditing(true);
  };
  const save = async () => {
    if (await onSave(draft)) setEditing(false);
  };
  const set = (patch: Partial<Draft>) => setDraft((d) => ({ ...d, ...patch }));

  const mrvaCell = (
    <div className="protocol-checklist__field">
      <span className="protocol-checklist__label">MRVA rating (computed)</span>
      <span className="protocol-checklist__value">
        <Tag type="blue" size="sm">
          {mrva || '—'}
        </Tag>
      </span>
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
            <Button kind="ghost" size="lg" disabled={busy} onClick={() => setEditing(false)}>
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
            <div className="rip-form__grid">
              <IndicatorCheckbox
                id="chr-q8"
                labelText="Q8 — Operational factors limited CHR management options on this block?"
                value={
                  draft.q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock
                }
                onToggle={(v) =>
                  set({
                    q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock: v,
                  })
                }
              />
              <IndicatorCheckbox
                id="chr-q9"
                labelText="Q9 — Management strategies/practices used were particularly effective?"
                value={
                  draft.q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues
                }
                onToggle={(v) =>
                  set({
                    q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues:
                      v,
                  })
                }
              />
              <IndicatorCheckbox
                id="chr-q10"
                labelText="Q10 — Strategies/practices could have reduced impacts on CHR values?"
                value={
                  draft.q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock
                }
                onToggle={(v) =>
                  set({
                    q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock:
                      v,
                  })
                }
              />
            </div>
            <TextAreaField
              id="chr-q8-comments"
              labelText="Q8 comments"
              value={draft.q8Comments}
              onChange={(v) => set({ q8Comments: v })}
            />
            <TextAreaField
              id="chr-q9-comments"
              labelText="Q9 comments"
              value={draft.q9Comments}
              onChange={(v) => set({ q9Comments: v })}
            />
            <TextAreaField
              id="chr-q10-comments"
              labelText="Q10 comments"
              value={draft.q10Comments}
              onChange={(v) => set({ q10Comments: v })}
            />
          </fieldset>

          <fieldset className="rip-form__group">
            <legend>Block rating</legend>
            <div className="rip-form__grid">
              <CodeSelect
                id="chr-block-rating"
                labelText="Block rating"
                value={draft.rating}
                options={RATING_CODES}
                includeBlank
                onChange={(v) => set({ rating: v })}
              />
              {mrvaCell}
            </div>
            <TextAreaField
              id="chr-rating-rationale"
              labelText="Rating rationale"
              value={draft.ratingRationale}
              onChange={(v) => set({ ratingRationale: v })}
            />
            <TextAreaField
              id="chr-block-comments"
              labelText="Additional comments"
              value={draft.commentaires}
              onChange={(v) => set({ commentaires: v })}
            />
          </fieldset>
        </>
      ) : (
        <>
          <fieldset className="rip-form__group">
            <legend>Operational review</legend>
            <div className="rip-form__grid">
              <RoField
                label="Q8 — Operational factors limited CHR management options?"
                value={yesNo(
                  value.q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock,
                )}
              />
              <RoField
                label="Q9 — Practices used were particularly effective?"
                value={yesNo(
                  value.q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues,
                )}
              />
              <RoField
                label="Q10 — Practices could have reduced impacts?"
                value={yesNo(
                  value.q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock,
                )}
              />
            </div>
            <RoField label="Q8 comments" value={value.q8Comments} />
            <RoField label="Q9 comments" value={value.q9Comments} />
            <RoField label="Q10 comments" value={value.q10Comments} />
          </fieldset>

          <fieldset className="rip-form__group">
            <legend>Block rating</legend>
            <div className="rip-form__grid">
              <RoField label="Block rating" value={ratingLabel(value.rating)} />
              {mrvaCell}
            </div>
            <RoField label="Rating rationale" value={value.ratingRationale} />
            <RoField label="Additional comments" value={value.commentaires} />
          </fieldset>
        </>
      )}
    </div>
  );
};

export default BlockSummary;

import { RadioButton, RadioButtonGroup } from '@carbon/react';
import FormLock from '@/components/core/FormLock';
import { useState, type FC } from 'react';

import { Modal } from '@/components/Modal';

import type { Feature } from '@/types/chrChecklist';

import { listFeatureLabels } from '@/pages/ChrChecklist/composites';

/** What to do with members that were never assessed in their own right. */
export type UngroupChoice = 'keep' | 'delete';

/**
 * Confirm dissolving a composite.
 *
 * More than a yes/no, because ungrouping can strand features: a feature added from inside the
 * composite dialog exists only to be part of the group, and on its own it owes a full assessment
 * nobody ever made. Rather than silently leaving those behind — or silently deleting them — the
 * dialog names them and makes the choice explicit.
 *
 * When every member was assessed in its own right there is nothing to strand, so the question is
 * not asked at all.
 */
const UngroupModal: FC<{
  memberCount: number;
  /** Members carrying nothing but their identity; empty when there is nothing to strand. */
  undescribed: Feature[];
  busy: boolean;
  onConfirm: (choice: UngroupChoice) => void;
  onCancel: () => void;
}> = ({ memberCount, undescribed, busy, onConfirm, onCancel }) => {
  const mustChoose = undescribed.length > 0;
  const [choice, setChoice] = useState<UngroupChoice | null>(null);
  /**
   * The button stays live while the choice is outstanding: a greyed-out Ungroup states that
   * something is wrong without saying what. Clicking it names the missing answer instead.
   */
  const [showErrors, setShowErrors] = useState(false);
  const choiceError = showErrors && mustChoose && choice === null;

  const submit = () => {
    setShowErrors(true);
    if (mustChoose && choice === null) return;
    onConfirm(choice ?? 'keep');
  };

  return (
    <Modal
      open
      danger
      className="chr-features__ungroup-modal"
      modalHeading="Are you sure you want to ungroup this composite?"
      primaryButtonText="Ungroup"
      secondaryButtonText="Cancel"
      primaryButtonDisabled={busy}
      // Carbon swaps the primary button's text for an inline loader while this is active, which is
      // the modal equivalent of ActionButton's spinner.
      loadingStatus={busy ? 'active' : 'inactive'}
      loadingDescription="Ungrouping…"
      size="sm"
      onRequestSubmit={submit}
      onRequestClose={onCancel}
    >

      <FormLock busy={busy}>        <p className="chr-features__composite-intro">
          {`The composite assessment will be deleted, and each of the ${memberCount} features will need its own before the checklist can be submitted.`}
        </p>

        {mustChoose && (
          <>
            <p className="chr-features__composite-required">
              <span aria-hidden="true">*</span> Required field.
            </p>
            <p className="chr-features__ungroup-stranded">
              {`${listFeatureLabels(undescribed)} ${
                undescribed.length === 1
                  ? 'has no details of its own'
                  : 'have no details of their own'
              }.`}
            </p>
            <RadioButtonGroup
              name="ungroup-choice"
              orientation="vertical"
              legendText={
                <>
                  What should happen to these features?{' '}
                  <span aria-hidden="true" className="chr-features__required-mark">
                    *
                  </span>
                </>
              }
              invalid={choiceError}
              invalidText="Choose whether to keep or delete them."
              valueSelected={choice ?? ''}
              onChange={(value) => setChoice(value as UngroupChoice)}
            >
              <RadioButton
                id="ungroup-keep"
                value="keep"
                labelText="Keep them and assess them individually"
                disabled={busy}
              />
              <RadioButton
                id="ungroup-delete"
                value="delete"
                labelText="Delete them — this cannot be undone"
                disabled={busy}
              />
            </RadioButtonGroup>
          </>
        )}
      </FormLock>

    </Modal>
  );
};

export default UngroupModal;

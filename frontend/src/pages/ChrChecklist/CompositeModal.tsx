import { Add, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useState, type FC } from 'react';

import { Modal } from '@/components/Modal';
import { CodeSelect } from '@/pages/ChrChecklist/fields';

import type { CompositeDraft } from '@/pages/ChrChecklist/composites';
import type { Feature } from '@/types/chrChecklist';

import { FEATURE_CLASS_CODES, INFORMATION_SOURCE_CODES } from '@/pages/ChrChecklist/codeLists';
import {
  classLabel,
  isCompositeAnchor,
  nextFeatureLabel,
  sameLabel,
  sourceLabel,
} from '@/pages/ChrChecklist/composites';

/** Every table cell reads the same way when it has nothing to show. */
const orDash = (value?: string) => (value?.trim() ? value : '—');

/**
 * What the dialog is missing, and what to say about it.
 *
 * `hasErrors` is what blocks the submit; the three messages are what the fields show, and stay
 * undefined until the user has asked to create the composite. The composite's own class and source
 * are only asked for when creating one — the members dialog does not show those fields, so it can
 * never be blocked on them.
 */
const compositeErrors = ({
  showErrors,
  editing,
  featureClass,
  infoSource,
  enoughMembers,
}: {
  showErrors: boolean;
  editing: boolean;
  featureClass: string;
  infoSource: string;
  enoughMembers: boolean;
}) => {
  const missingCodes = !editing && (!featureClass || !infoSource);
  const show = (missing: boolean, message: string) => (showErrors && missing ? message : undefined);
  return {
    classError: show(!editing && !featureClass, 'Feature class is required.'),
    sourceError: show(!editing && !infoSource, 'Information source is required.'),
    memberError: show(!enoughMembers, 'Select at least two features.'),
    hasErrors: !enoughMembers || missingCodes,
  };
};

/**
 * The features on offer in the dialog. A composite is never among them — a composite cannot
 * contain a composite.
 *
 * Beyond that the two modes differ. Creating a group offers only unattached features, so a new
 * composite cannot quietly empty an existing one. Editing a group's members offers everything,
 * because moving a feature across from another composite is the point of that dialog.
 */
const selectableFeatures = (features: Feature[], anchor?: Feature): Feature[] =>
  features.filter((f) => {
    if (f === anchor) return false;
    if (isCompositeAnchor(f)) return false;
    // Already in some composite: offered only when editing, where picking it moves it across.
    return !f.compositeFeature || anchor != null;
  });

/** One selectable row. A feature added from inside the dialog is described here for the first
 *  time, so it gets pickers and a Delete; an existing feature just shows what it already has. */
const CompositeRow: FC<{
  feature: Feature;
  index: number;
  added: boolean;
  checked: boolean;
  busy: boolean;
  showAction: boolean;
  onToggle: () => void;
  onPatch: (patch: Partial<Feature>) => void;
  onRemove: () => void;
}> = ({ feature, index, added, checked, busy, showAction, onToggle, onPatch, onRemove }) => {
  const label = feature.featureLabel;
  const editable = added && label != null;
  return (
    <TableRow>
      <TableCell>
        <Checkbox
          id={`composite-select-${feature.id ?? label ?? index}`}
          labelText={`Include feature ${label ?? index + 1} in this composite`}
          hideLabel
          checked={checked}
          disabled={busy}
          onChange={onToggle}
        />
      </TableCell>
      <TableCell>{label ?? index + 1}</TableCell>
      <TableCell>
        {editable ? (
          <CodeSelect
            id={`composite-add-class-${label}`}
            labelText={`Feature class for feature ${label}`}
            hideLabel
            value={feature.featureDescriptionCode}
            options={FEATURE_CLASS_CODES}
            includeBlank
            disabled={busy}
            onChange={(v) => onPatch({ featureDescriptionCode: v })}
          />
        ) : (
          orDash(classLabel(feature.featureDescriptionCode))
        )}
      </TableCell>
      <TableCell>
        {editable ? (
          <CodeSelect
            id={`composite-add-source-${label}`}
            labelText={`Information source for feature ${label}`}
            hideLabel
            value={feature.featureInfoSourceCode}
            options={INFORMATION_SOURCE_CODES}
            includeBlank
            disabled={busy}
            onChange={(v) => onPatch({ featureInfoSourceCode: v })}
          />
        ) : (
          orDash(sourceLabel(feature.featureInfoSourceCode))
        )}
      </TableCell>
      {showAction && (
        <TableCell className="table-actions">
          {editable && (
            <Button
              kind="danger--ghost"
              size="sm"
              renderIcon={TrashCan}
              disabled={busy}
              onClick={onRemove}
            >
              Delete
            </Button>
          )}
        </TableCell>
      )}
    </TableRow>
  );
};

/**
 * Create or edit a composite: pick the class and information source the group is assessed under,
 * then choose the features it covers.
 *
 * Features added from in here are held as local drafts rather than written straight to the
 * checklist, so Cancel leaves nothing behind. They are handed back with the rest of the draft and
 * only join the list once the composite is actually created.
 */
const CompositeModal: FC<{
  /** The checklist's features — the pool to group from. */
  features: Feature[];
  /** The composite being edited, or undefined when creating a new one. */
  anchor?: Feature;
  /** How the table names the composite being edited ("Composite 1"). */
  anchorName?: string;
  busy: boolean;
  onSubmit: (draft: CompositeDraft) => void;
  onCancel: () => void;
}> = ({ features, anchor, anchorName, busy, onSubmit, onCancel }) => {
  const editing = anchor != null;

  const [featureClass, setFeatureClass] = useState(anchor?.featureDescriptionCode ?? '');
  const [infoSource, setInfoSource] = useState(anchor?.featureInfoSourceCode ?? '');
  const [additions, setAdditions] = useState<Feature[]>([]);
  const [selected, setSelected] = useState<string[]>(() =>
    features
      .filter((f) => anchor && sameLabel(f.compositeFeature, anchor.featureLabel))
      .map((f) => f.featureLabel)
      .filter((label): label is string => Boolean(label)),
  );

  const selectable = selectableFeatures(features, anchor);

  // Newest first, matching where the row appears when "Add a new feature" is used.
  const rows = [...additions].reverse().concat(selectable);

  const isChecked = (label?: string) => Boolean(label) && selected.some((s) => sameLabel(s, label));

  const toggle = (label?: string) => {
    if (!label) return;
    setSelected((prev) =>
      prev.some((s) => sameLabel(s, label))
        ? prev.filter((s) => !sameLabel(s, label))
        : [...prev, label],
    );
  };

  const addFeature = () => {
    const label = nextFeatureLabel([...features, ...additions]);
    setAdditions((prev) => [...prev, { featureLabel: label, compositeFeatureInd: 'false' }]);
    // A feature added from in here was added *for* this composite, so it starts selected.
    setSelected((prev) => [...prev, label]);
  };

  const patchAddition = (label: string, patch: Partial<Feature>) =>
    setAdditions((prev) => prev.map((f) => (f.featureLabel === label ? { ...f, ...patch } : f)));

  const removeAddition = (label: string) => {
    setAdditions((prev) => prev.filter((f) => f.featureLabel !== label));
    setSelected((prev) => prev.filter((s) => !sameLabel(s, label)));
  };

  const isAddition = (feature: Feature) => additions.includes(feature);

  // A composite that groups fewer than two features is not a group, and the backend rejects it at
  // submit ("must include at least two features").
  const enoughMembers = selected.length >= 2;

  /**
   * Errors stay hidden until the user asks to create the composite.
   *
   * The button is deliberately left enabled while the form is incomplete: a greyed-out button says
   * "you cannot do this" without ever saying why, and the reason here is spread across three
   * separate controls. Clicking it names what is missing instead.
   */
  const [showErrors, setShowErrors] = useState(false);
  const { classError, sourceError, memberError, hasErrors } = compositeErrors({
    showErrors,
    editing,
    featureClass,
    infoSource,
    enoughMembers,
  });

  const submit = () => {
    setShowErrors(true);
    if (hasErrors) return;
    onSubmit({
      featureDescriptionCode: featureClass || undefined,
      featureInfoSourceCode: infoSource || undefined,
      memberLabels: selected,
      // Only the additions that survived to submit and are actually in the group.
      additions: additions.filter((f) => isChecked(f.featureLabel)),
    });
  };

  return (
    <Modal
      open
      className="chr-features__composite-modal"
      modalHeading={editing ? `Members of ${anchorName ?? 'this composite'}` : 'Create composite'}
      primaryButtonText={editing ? 'Save' : 'Create composite'}
      secondaryButtonText="Cancel"
      primaryButtonDisabled={busy}
      size="md"
      onRequestSubmit={submit}
      onRequestClose={onCancel}
    >
      {/* Editing membership is only about which features belong, so the composite's own class and
          source are not restated here — those are the anchor's, edited through Edit. */}
      {editing ? (
        <>
          <p className="chr-features__composite-intro">
            {`Clear a selection to remove a feature from the composite. Selecting a feature from another composite moves it to ${anchorName ?? 'this composite'}.`}
          </p>
          {/* The requirement line doubles as the error: repeating "Select at least two features"
              underneath itself in red says nothing the reader did not just read. */}
          <p
            className={`chr-features__composite-required${
              memberError ? ' chr-features__composite-required--error' : ''
            }`}
            role={memberError ? 'alert' : undefined}
          >
            <span aria-hidden="true">*</span> Select at least two features.
          </p>
        </>
      ) : (
        <>
          {/* Moved here from the feature editor: the concept needs explaining at the point someone
              decides to group features, not while they are describing one. What a composite *is*
              comes first, then the rule for using this dialog. */}
          <p className="chr-features__composite-intro">
            A composite feature is a group of two or more associated cultural heritage features that
            are assessed together because they are culturally, spatially, or functionally connected.
          </p>
          <p className="chr-features__composite-intro">
            <strong>Example:</strong> A cultural trail and an adjacent berry harvesting area that
            occur together and are protected within the same area could be assessed as one composite
            feature rather than as two separate features.
          </p>
          <p className="chr-features__composite-intro">
            A composite is assessed as one record, so only group features that received the same
            management and responded to it the same way.
          </p>
          <p className="chr-features__composite-required">
            <span aria-hidden="true">*</span> Required field.
          </p>

          <div className="chr-features__composite-codes">
            <CodeSelect
              id="composite-feature-class"
              labelText={
                <>
                  Feature class <span aria-hidden="true">*</span>
                </>
              }
              value={featureClass}
              options={FEATURE_CLASS_CODES}
              includeBlank
              disabled={busy}
              invalid={Boolean(classError)}
              invalidText={classError}
              onChange={setFeatureClass}
            />
            <CodeSelect
              id="composite-info-source"
              labelText={
                <>
                  Information source <span aria-hidden="true">*</span>
                </>
              }
              value={infoSource}
              options={INFORMATION_SOURCE_CODES}
              includeBlank
              disabled={busy}
              invalid={Boolean(sourceError)}
              invalidText={sourceError}
              onChange={setInfoSource}
            />
          </div>

          <h3 className="chr-features__composite-subhead">
            <span aria-hidden="true">*</span> Features in this composite
          </h3>
          <p
            className={`chr-features__composite-hint${
              memberError ? ' chr-features__composite-required--error' : ''
            }`}
            role={memberError ? 'alert' : undefined}
          >
            Select at least two features. Add new ones if needed.
          </p>
        </>
      )}

      <div className="chr-features__composite-panel">
        <div className="chr-features__composite-toolbar">
          <span>{`${rows.length} feature${rows.length === 1 ? '' : 's'}`}</span>
          <Button kind="ghost" size="sm" renderIcon={Add} disabled={busy} onClick={addFeature}>
            Add a new feature
          </Button>
        </div>
        <Table size="lg" className="chr-features__composite-table">
          <TableHead>
            <TableRow>
              <TableHeader />
              <TableHeader>Feature</TableHeader>
              <TableHeader>Feature class</TableHeader>
              <TableHeader>Information source</TableHeader>
              {additions.length > 0 && <TableHeader>Action</TableHeader>}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((feature, index) => {
              const label = feature.featureLabel;
              return (
                <CompositeRow
                  key={feature.id ?? `composite-row-${label ?? index}`}
                  feature={feature}
                  index={index}
                  added={isAddition(feature)}
                  checked={isChecked(label)}
                  busy={busy}
                  showAction={additions.length > 0}
                  onToggle={() => toggle(label)}
                  onPatch={(patch) => label && patchAddition(label, patch)}
                  onRemove={() => label && removeAddition(label)}
                />
              );
            })}
          </TableBody>
        </Table>
      </div>
    </Modal>
  );
};

export default CompositeModal;

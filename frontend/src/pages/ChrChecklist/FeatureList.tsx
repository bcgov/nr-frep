import {
  Add,
  Edit,
  GroupObjects,
  Layers,
  Link as LinkIcon,
  TrashCan,
  UngroupObjects,
} from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  InlineNotification,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { Fragment, useRef, useState, type FC } from 'react';

import { Modal } from '@/components/Modal';
import CompositeModal from '@/pages/ChrChecklist/CompositeModal';
import FeatureEditor from '@/pages/ChrChecklist/FeatureEditor';
import UngroupModal from '@/pages/ChrChecklist/UngroupModal';

import type { CompositeDraft, FeatureRow } from '@/pages/ChrChecklist/composites';
import type { UngroupChoice } from '@/pages/ChrChecklist/UngroupModal';
import type { Feature } from '@/types/chrChecklist';

import { useConfirm } from '@/context/confirm/useConfirm';
import {
  addComposite,
  classLabel,
  featureRows,
  membersOf,
  nextFeatureLabel,
  sourceLabel,
  undescribedMembers,
  ungroupComposite,
  ungroupDiscardingUndescribed,
  updateComposite,
} from '@/pages/ChrChecklist/composites';
import { featureHasErrors } from '@/pages/ChrChecklist/featureValidation';

/** Every table cell reads the same way when it has nothing to show. */
const orDash = (value?: string) => (value?.trim() ? value : '—');

/**
 * Section 3 — feature list. Master-detail: a table of features with per-row Edit / Delete and an
 * "Add feature" button (mirroring the Biodiversity Stratum and Contacts tabs). Editing or adding
 * opens the multi-tab {@link FeatureEditor}; Save persists the whole list, Cancel discards the
 * in-progress edits. Features are edited live against the parent (so cross-feature associations stay
 * in sync) — Cancel restores a snapshot taken when the editor opened.
 */
const FeatureList: FC<{
  features: Feature[];
  onChange: (features: Feature[]) => void;
  onSave: (features: Feature[]) => Promise<boolean>;
  readOnly: boolean;
  busy: boolean;
}> = ({ features, onChange, onSave, readOnly, busy }) => {
  const confirm = useConfirm();
  const [selected, setSelected] = useState<number | null>(null);
  // The full feature array as it was when the editor opened, restored on Cancel.
  const snapshot = useRef<Feature[] | null>(null);
  // Errors stay hidden until a save is attempted on the open feature.
  const [showErrors, setShowErrors] = useState(false);
  // Row whose associations are being edited from the list, if any.
  const [associating, setAssociating] = useState<number | null>(null);
  // Confirmation after a successful save. Dismissible, and cleared whenever the dialog reopens so a
  // stale "updated" never sits above a set of associations the user is still changing.
  const [associationsSaved, setAssociationsSaved] = useState(false);

  // The composite dialog: 'create' for a new group, or the anchor being edited.
  const [composing, setComposing] = useState<'create' | Feature | null>(null);
  // Confirmation after a composite is created or changed, naming it the way the table does.
  const [compositeSaved, setCompositeSaved] = useState<string | null>(null);
  // The composite awaiting an ungroup confirmation, if any.
  const [ungrouping, setUngrouping] = useState<Feature | null>(null);

  const openAssociate = (index: number) => {
    setAssociationsSaved(false);
    // Same snapshot/restore contract as the editor: Cancel puts the list back exactly as it was.
    snapshot.current = features;
    setAssociating(index);
  };

  const cancelAssociate = () => {
    if (snapshot.current) onChange(snapshot.current);
    snapshot.current = null;
    setAssociating(null);
  };

  const saveAssociate = async () => {
    if (await onSave(features)) {
      snapshot.current = null;
      setAssociating(null);
      setAssociationsSaved(true);
    }
  };

  const add = () => {
    setShowErrors(false);
    snapshot.current = features;
    const feature: Feature = {
      featureLabel: nextFeatureLabel(features),
      compositeFeatureInd: 'false',
    };
    onChange([...features, feature]);
    setSelected(features.length);
  };

  const openEdit = (index: number) => {
    setShowErrors(false);
    snapshot.current = features;
    setSelected(index);
  };

  const cancel = () => {
    setShowErrors(false);
    if (snapshot.current) onChange(snapshot.current);
    snapshot.current = null;
    setSelected(null);
  };

  const save = async () => {
    // First point the user has asked for the feature to be complete — reveal the errors now. Blank
    // required fields are marked, counted on the tab and block submit, but they do not stop the
    // save; only a value the column cannot store does (see featureValidation.ts).
    setShowErrors(true);
    const editing = selected === null ? undefined : features[selected];
    if (editing && featureHasErrors(editing)) return;
    if (await onSave(features)) {
      snapshot.current = null;
      setSelected(null);
    }
  };

  const openCompose = (target: 'create' | Feature) => {
    setCompositeSaved(null);
    setComposing(target);
  };

  /**
   * Create or update a composite and persist it in one step, the way the Associate dialog does — a
   * group that only exists in the browser is a group the next reader of this checklist cannot see.
   */
  const saveComposite = async (draft: CompositeDraft) => {
    const editing = composing !== 'create' && composing !== null ? composing : undefined;
    const next = editing
      ? updateComposite(features, editing, draft)
      : addComposite(features, draft);
    if (!(await onSave(next))) return;
    setComposing(null);
    // Name it from the saved list, so it matches the row the user is about to look at.
    const row = featureRows(next).find(
      (r) =>
        r.kind === 'composite' &&
        (editing ? r.anchor.featureLabel === editing.featureLabel : r.anchor === next[0]),
    );
    const label = row?.kind === 'composite' ? `Feature ${row.name}` : 'The composite';
    setCompositeSaved(
      editing
        ? `${label} now groups ${draft.memberLabels.length} features`
        : `${label} created as a composite of ${draft.memberLabels.length} features`,
    );
  };

  const ungroup = async (choice: UngroupChoice) => {
    if (!ungrouping) return;
    const next =
      choice === 'delete'
        ? ungroupDiscardingUndescribed(features, ungrouping)
        : ungroupComposite(features, ungrouping);
    if (await onSave(next)) {
      setUngrouping(null);
      setCompositeSaved(null);
    }
  };

  const removeAt = async (index: number) => {
    const label = features[index]?.featureLabel;
    if (
      !(await confirm({
        title: 'Delete feature?',
        message: `Delete feature ${label ?? index + 1}? This can't be undone.`,
      }))
    )
      return;
    await onSave(features.filter((_, i) => i !== index));
  };

  const patchSelected = (patch: Partial<Feature>) =>
    onChange(features.map((f, i) => (i === selected ? { ...f, ...patch } : f)));

  const current = selected === null ? undefined : features[selected];

  /**
   * Toggle an association between the selected feature and a sibling. Associations are stored
   * (bidirectionally) as the *other* feature's label in each feature's {@code associatedFeatures}
   * list, matching the legacy CHR ModalAssociateToggle behaviour.
   */
  const toggleAssociatedFor = (index: number, siblingLabel: string) => {
    const subject = features[index];
    const currentLabel = subject?.featureLabel;
    if (!currentLabel) return;
    const wasAssociated = (subject.associatedFeatures ?? []).includes(siblingLabel);
    const apply = (list: string[] | undefined, label: string): string[] => {
      const set = new Set(list ?? []);
      if (wasAssociated) set.delete(label);
      else set.add(label);
      return [...set].sort((a, b) => a.localeCompare(b));
    };
    onChange(
      features.map((f, i) => {
        if (i === index) {
          return { ...f, associatedFeatures: apply(f.associatedFeatures, siblingLabel) };
        }
        if (f.featureLabel === siblingLabel) {
          return { ...f, associatedFeatures: apply(f.associatedFeatures, currentLabel) };
        }
        return f;
      }),
    );
  };

  const rows = featureRows(features);
  // The row for the composite whose members are being edited — the dialog names it the way the
  // table does rather than by its stored label.
  const composingRow = rows.find(
    (r): r is Extract<FeatureRow, { kind: 'composite' }> =>
      r.kind === 'composite' && r.anchor === composing,
  );
  // Only features that stand on their own can be grouped: a composite cannot contain a composite,
  // and a feature already assessed under one is spoken for.
  const groupable = rows.filter((r) => r.kind === 'feature').length;

  // Detail view (add / edit a single feature) — the table is hidden while the editor is open.
  if (current) {
    return (
      <div className="rip-form">
        <div className="protocol-checklist__section-actions">
          {!readOnly && (
            <Button size="lg" disabled={busy} onClick={() => void save()}>
              Save
            </Button>
          )}
          <Button kind="ghost" size="lg" disabled={busy} onClick={cancel}>
            Cancel
          </Button>
        </div>
        <FeatureEditor
          key={current.id ?? `feature-${selected}`}
          feature={current}
          onPatch={patchSelected}
          readOnly={readOnly}
          showErrors={showErrors}
        />
      </div>
    );
  }

  // List view: a table of features with per-row Edit / Delete + an "Add feature" toolbar button.
  return (
    <div className="rip-form">
      {compositeSaved && (
        <InlineNotification
          kind="success"
          lowContrast
          title={compositeSaved}
          className="chr-features__saved"
          onClose={() => setCompositeSaved(null)}
        />
      )}
      {associationsSaved && (
        <InlineNotification
          kind="success"
          lowContrast
          title="Associations updated"
          className="chr-features__saved"
          onClose={() => setAssociationsSaved(false)}
        />
      )}
      <div className="bio-strata">
        {!readOnly && (
          <div className="bio-strata__toolbar">
            {/* Grouping needs two features to group, so the action only appears once there are
                two that are not already spoken for by a composite. */}
            <Button
              kind="ghost"
              size="lg"
              renderIcon={GroupObjects}
              disabled={busy || groupable < 2}
              onClick={() => openCompose('create')}
            >
              Create composite
            </Button>
            <Button
              kind="tertiary"
              size="lg"
              className="bio-strata__add"
              renderIcon={Add}
              disabled={busy}
              onClick={add}
            >
              Add feature
            </Button>
          </div>
        )}
        {features.length === 0 ? (
          <p>No features yet.</p>
        ) : (
          <Table size="sm" className="bio-strata__table chr-features__table">
            <TableHead>
              <TableRow>
                <TableHeader>Feature</TableHeader>
                <TableHeader>Feature class</TableHeader>
                <TableHeader>Information source</TableHeader>
                <TableHeader>Description</TableHeader>
                <TableHeader>Associated features</TableHeader>
                <TableHeader>Action</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => {
                if (row.kind === 'composite') {
                  const { anchor, members, name } = row;
                  return (
                    // A fragment, not nested rows: the members belong to this composite but a table
                    // row cannot contain rows, so they follow it and are tied to it visually.
                    <Fragment key={anchor.id ?? `composite-${anchor.featureLabel}`}>
                      <TableRow className="chr-features__composite-row">
                        <TableCell>{name}</TableCell>
                        <TableCell>{orDash(classLabel(anchor.featureDescriptionCode))}</TableCell>
                        <TableCell>{orDash(sourceLabel(anchor.featureInfoSourceCode))}</TableCell>
                        <TableCell>{orDash(anchor.featureDescription)}</TableCell>
                        <TableCell>
                          {orDash((anchor.associatedFeatures ?? []).join(', '))}
                        </TableCell>
                        <TableCell className="table-actions">
                          <Button
                            kind="ghost"
                            size="sm"
                            renderIcon={Edit}
                            disabled={busy}
                            onClick={() => openEdit(features.indexOf(anchor))}
                          >
                            Edit
                          </Button>
                          {!readOnly && (
                            <Button
                              kind="ghost"
                              size="sm"
                              renderIcon={Layers}
                              disabled={busy}
                              onClick={() => openCompose(anchor)}
                            >
                              Members
                            </Button>
                          )}
                          {!readOnly && (
                            <Button
                              kind="danger--ghost"
                              size="sm"
                              renderIcon={UngroupObjects}
                              disabled={busy}
                              onClick={() => setUngrouping(anchor)}
                            >
                              Ungroup
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                      <TableRow className="chr-features__composite-caption">
                        <TableCell colSpan={6}>
                          {`${members.length} feature${members.length === 1 ? '' : 's'} assessed as one unit`}
                        </TableCell>
                      </TableRow>
                      {members.map((member, i) => (
                        <TableRow
                          key={member.id ?? `member-${member.featureLabel ?? i}`}
                          className="chr-features__member-row"
                        >
                          <TableCell>{member.featureLabel || `Feature ${i + 1}`}</TableCell>
                          <TableCell>{orDash(classLabel(member.featureDescriptionCode))}</TableCell>
                          <TableCell>{orDash(sourceLabel(member.featureInfoSourceCode))}</TableCell>
                          <TableCell>{orDash(member.featureDescription)}</TableCell>
                          <TableCell>
                            {orDash((member.associatedFeatures ?? []).join(', '))}
                          </TableCell>
                          {/* No Edit or Delete: a member is assessed through its composite, and
                              removing one is what Ungroup or Members is for. Associating stays —
                              it is a relationship, not part of the assessment. */}
                          <TableCell className="table-actions">
                            {!readOnly && (
                              <Button
                                kind="ghost"
                                size="sm"
                                renderIcon={LinkIcon}
                                disabled={busy || features.length < 2}
                                onClick={() => openAssociate(features.indexOf(member))}
                              >
                                Associate
                              </Button>
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                    </Fragment>
                  );
                }

                const { feature } = row;
                const index = features.indexOf(feature);
                return (
                  <TableRow key={feature.id ?? `feature-${index}`}>
                    <TableCell>{feature.featureLabel || `Feature ${index + 1}`}</TableCell>
                    <TableCell>{orDash(classLabel(feature.featureDescriptionCode))}</TableCell>
                    <TableCell>{orDash(sourceLabel(feature.featureInfoSourceCode))}</TableCell>
                    <TableCell>{orDash(feature.featureDescription)}</TableCell>
                    {/* Associations are stored as the other feature's label on both sides, so this
                        reads straight off the row rather than scanning the list. */}
                    <TableCell>{orDash((feature.associatedFeatures ?? []).join(', '))}</TableCell>
                    <TableCell className="table-actions">
                      {/* Labelled rather than icon-only: three actions in a row are hard to tell
                          apart by glyph, and Delete is destructive. */}
                      <Button
                        kind="ghost"
                        size="sm"
                        renderIcon={Edit}
                        disabled={busy}
                        onClick={() => openEdit(index)}
                      >
                        Edit
                      </Button>
                      {!readOnly && (
                        <Button
                          kind="ghost"
                          size="sm"
                          renderIcon={LinkIcon}
                          disabled={busy || features.length < 2}
                          onClick={() => openAssociate(index)}
                        >
                          Associate
                        </Button>
                      )}
                      {!readOnly && (
                        <Button
                          kind="danger--ghost"
                          size="sm"
                          renderIcon={TrashCan}
                          disabled={busy}
                          onClick={() => void removeAt(index)}
                        >
                          Delete
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
      </div>

      {/* Associating from the list rather than only inside the editor: it is a relationship between
          two features, so making it a row action avoids opening one feature to describe another.
          Ticking a box updates both sides (see toggleAssociatedFor); Save persists, Cancel restores
          the snapshot taken when the dialog opened. */}
      {ungrouping && (
        <UngroupModal
          memberCount={membersOf(features, ungrouping).length}
          undescribed={undescribedMembers(features, ungrouping)}
          busy={busy}
          onConfirm={(choice) => void ungroup(choice)}
          onCancel={() => setUngrouping(null)}
        />
      )}

      {composing !== null && (
        <CompositeModal
          features={features}
          anchor={composing === 'create' ? undefined : composing}
          anchorName={composingRow ? `Feature ${composingRow.name}` : undefined}
          busy={busy}
          onSubmit={(draft) => void saveComposite(draft)}
          onCancel={() => setComposing(null)}
        />
      )}

      {associating !== null && (
        <Modal
          open
          className="chr-features__associate-modal"
          modalHeading="Associate features"
          primaryButtonText="Save associations"
          secondaryButtonText="Cancel"
          primaryButtonDisabled={busy}
          size="md"
          onRequestSubmit={() => void saveAssociate()}
          onRequestClose={cancelAssociate}
        >
          <p className="chr-features__associate-intro">
            {`Choose which features Feature ${
              features[associating]?.featureLabel ?? associating + 1
            } is related to.`}
          </p>
          {/* Says what an association does and — as importantly — what it does not do. Grouping
              features is not the same as grouping their assessments, and a composite is the thing
              that does the latter. */}
          <p className="chr-features__associate-intro">
            Associated features are recorded as related to one another. Each one is still assessed
            separately and can have a different outcome.
          </p>
          <Table size="lg" className="chr-features__associate-table">
            <TableHead>
              <TableRow>
                <TableHeader />
                <TableHeader>Feature</TableHeader>
                <TableHeader>Feature class</TableHeader>
                <TableHeader>Information source</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {features.map((f, i) => {
                const label = f.featureLabel;
                if (i === associating || !label) return null;
                const checked = (features[associating]?.associatedFeatures ?? []).includes(label);
                return (
                  <TableRow key={f.id ?? `assoc-${i}`}>
                    <TableCell>
                      <Checkbox
                        id={`assoc-row-${f.id ?? i}`}
                        labelText={`Associate with feature ${label}`}
                        hideLabel
                        checked={checked}
                        onChange={() => toggleAssociatedFor(associating, label)}
                      />
                    </TableCell>
                    <TableCell>{label}</TableCell>
                    <TableCell>{orDash(classLabel(f.featureDescriptionCode))}</TableCell>
                    <TableCell>{orDash(sourceLabel(f.featureInfoSourceCode))}</TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </Modal>
      )}
    </div>
  );
};

export default FeatureList;

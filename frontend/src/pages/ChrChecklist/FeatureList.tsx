import { Add, Edit, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useRef, useState, type FC } from 'react';

import FeatureEditor from '@/pages/ChrChecklist/FeatureEditor';

import type { Feature } from '@/types/chrChecklist';

import { useConfirm } from '@/context/confirm/useConfirm';
import { FEATURE_CLASS_CODES } from '@/pages/ChrChecklist/codeLists';
import { featureHasErrors } from '@/pages/ChrChecklist/featureValidation';

const classLabel = (code?: string) =>
  FEATURE_CLASS_CODES.find((c) => c.code === code)?.label ?? code ?? '';

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

  const nextLabel = (): string => {
    const numbers = features.map((f) => Number(f.featureLabel)).filter((n) => Number.isFinite(n));
    const max = numbers.length > 0 ? Math.max(...numbers) : 0;
    return String(max + 1);
  };

  const add = () => {
    snapshot.current = features;
    const feature: Feature = { featureLabel: nextLabel(), compositeFeatureInd: 'false' };
    onChange([...features, feature]);
    setSelected(features.length);
  };

  const openEdit = (index: number) => {
    snapshot.current = features;
    setSelected(index);
  };

  const cancel = () => {
    if (snapshot.current) onChange(snapshot.current);
    snapshot.current = null;
    setSelected(null);
  };

  const save = async () => {
    // Errors are shown inline on the feature fields; just block the save while any remain.
    const editing = selected === null ? undefined : features[selected];
    if (editing && featureHasErrors(editing)) return;
    if (await onSave(features)) {
      snapshot.current = null;
      setSelected(null);
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
  const toggleAssociated = (siblingLabel: string) => {
    const currentLabel = current?.featureLabel;
    if (!currentLabel) return;
    const wasAssociated = (current.associatedFeatures ?? []).includes(siblingLabel);
    const apply = (list: string[] | undefined, label: string): string[] => {
      const set = new Set(list ?? []);
      if (wasAssociated) set.delete(label);
      else set.add(label);
      return [...set].sort();
    };
    onChange(
      features.map((f, i) => {
        if (i === selected) {
          return { ...f, associatedFeatures: apply(f.associatedFeatures, siblingLabel) };
        }
        if (f.featureLabel === siblingLabel) {
          return { ...f, associatedFeatures: apply(f.associatedFeatures, currentLabel) };
        }
        return f;
      }),
    );
  };

  const otherFeatures = features.filter((_, i) => i !== selected);
  const siblingLabels = otherFeatures
    .map((f) => f.featureLabel)
    .filter((label): label is string => Boolean(label));

  // Candidate composite anchors: other features not already grouped into a composite themselves
  // (mirrors the legacy SelectFeature filter, which excludes siblings whose compositeFeature is set),
  // plus the current feature's existing target so editing it doesn't drop the current selection.
  const compositeCandidateLabels = otherFeatures
    .filter((f) => !f.compositeFeature || f.featureLabel === current?.compositeFeature)
    .map((f) => f.featureLabel)
    .filter((label): label is string => Boolean(label));

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
          siblingLabels={siblingLabels}
          compositeCandidateLabels={compositeCandidateLabels}
          onToggleAssociated={toggleAssociated}
        />
      </div>
    );
  }

  // List view: a table of features with per-row Edit / Delete + an "Add feature" toolbar button.
  return (
    <div className="rip-form">
      <div className="bio-strata">
        {!readOnly && (
          <div className="bio-strata__toolbar">
            <Button
              kind="tertiary"
              size="lg"
              className="bio-strata__add"
              disabled={busy}
              onClick={add}
            >
              <Add size={16} className="bio-strata__add-icon" />
              Add feature
            </Button>
          </div>
        )}
        {features.length === 0 ? (
          <p>No features yet.</p>
        ) : (
          <Table size="sm" className="bio-strata__table">
            <TableHead>
              <TableRow>
                <TableHeader>Feature</TableHeader>
                <TableHeader>Feature class</TableHeader>
                <TableHeader>Actions</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {features.map((feature, index) => (
                <TableRow key={feature.id ?? `feature-${index}`}>
                  <TableCell>{feature.featureLabel || `Feature ${index + 1}`}</TableCell>
                  <TableCell>{classLabel(feature.featureDescriptionCode) || '—'}</TableCell>
                  <TableCell>
                    <Button
                      kind="ghost"
                      size="sm"
                      renderIcon={Edit}
                      iconDescription="Edit"
                      hasIconOnly
                      disabled={busy}
                      onClick={() => openEdit(index)}
                    />
                    {!readOnly && (
                      <Button
                        kind="danger--ghost"
                        size="sm"
                        renderIcon={TrashCan}
                        iconDescription="Delete"
                        hasIconOnly
                        disabled={busy}
                        onClick={() => void removeAt(index)}
                      />
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>
    </div>
  );
};

export default FeatureList;

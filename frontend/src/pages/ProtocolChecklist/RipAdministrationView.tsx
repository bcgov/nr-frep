import { Add, Edit, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  Select,
  SelectItem,
  SkeletonText,
  Tag,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';

import type { AdministrationData } from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

/**
 * Checklist Administration tab (legacy FREP301 / checklistCostResource) — read-only view with
 * inline editing of the evaluation date and site-access / cost fields, plus the evaluation team
 * (lead + members).
 *
 * Team management: removing an evaluator works (delete_team_member). Adding one is wired
 * (save_team_member) but the picker is empty — the legacy "Add evaluator" list comes from a
 * WebADE/IDIR directory lookup (users with the FREP editor role in the district), which has no DB
 * proc and isn't ported yet. See the migration tracker.
 *
 * Note: Evaluation Date and Team Lead are mandatory for Submit, so this tab is where they're set.
 */

type Props = {
  checklistId: string;
  canEdit: boolean;
  submitted: boolean;
};

const SCALARS: { key: keyof AdministrationData; label: string }[] = [
  { key: 'evaluationDate', label: 'Evaluation date (YYYY-MM-DD)' },
  { key: 'siteAccessCode', label: 'Access type' },
  { key: 'blockAccessTime', label: 'Block access time' },
  { key: 'hoursOnBlock', label: 'Hours on block' },
  { key: 'peopleOnBlock', label: 'People on block' },
];

const RipAdministrationView: FC<Props> = ({ checklistId, canEdit, submitted }) => {
  const { display } = useNotification();
  const [data, setData] = useState<AdministrationData | null>(null);
  const [selectedEvaluator, setSelectedEvaluator] = useState('');
  const [addAsLead, setAddAsLead] = useState(false);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);

  const reportError = useCallback(
    (title: string, err: unknown) =>
      display({
        kind: 'error',
        title,
        subtitle: err instanceof Error ? err.message : 'Unknown error',
        timeout: 9000,
      }),
    [display],
  );

  const loadData = useCallback(
    (signal?: { cancelled: boolean }) => {
      setLoading(true);
      API.protocolChecklist
        .getRipAdministration(checklistId)
        .then((d) => {
          if (!signal?.cancelled) setData(d);
        })
        .catch((err: unknown) => {
          if (!signal?.cancelled) reportError("We couldn't load the administration data", err);
        })
        .finally(() => {
          if (!signal?.cancelled) setLoading(false);
        });
    },
    [checklistId, reportError],
  );

  useEffect(() => {
    const signal = { cancelled: false };
    loadData(signal);
    return () => {
      signal.cancelled = true;
    };
  }, [loadData]);

  const addMember = async () => {
    if (!selectedEvaluator) return;
    setBusy(true);
    try {
      const updated = await API.protocolChecklist.addRipTeamMember(
        checklistId,
        selectedEvaluator,
        addAsLead,
      );
      setData(updated);
      setSelectedEvaluator('');
      setAddAsLead(false);
      display({ kind: 'success', title: 'Evaluator added', timeout: 4000 });
    } catch (err) {
      reportError('Could not add evaluator', err);
    } finally {
      setBusy(false);
    }
  };

  const removeMember = async (evaluatorUserid?: string, revisionCount?: string) => {
    if (!evaluatorUserid) return;
    setBusy(true);
    try {
      const updated = await API.protocolChecklist.removeRipTeamMember(
        checklistId,
        evaluatorUserid,
        revisionCount,
      );
      setData(updated);
      display({ kind: 'success', title: 'Evaluator removed', timeout: 4000 });
    } catch (err) {
      reportError('Could not remove evaluator', err);
    } finally {
      setBusy(false);
    }
  };

  const get = (key: keyof AdministrationData): string =>
    ((data as Record<string, unknown>)?.[key] as string | undefined) ?? '';
  const set = (key: keyof AdministrationData, value: string) =>
    setData((prev) => (prev ? { ...prev, [key]: value } : prev));

  const handleSave = async () => {
    if (!data) return;
    setBusy(true);
    try {
      const saved = await API.protocolChecklist.saveRipAdministration(checklistId, data);
      setData(saved);
      setEditing(false);
      display({ kind: 'success', title: 'Administration saved', timeout: 4000 });
    } catch (err) {
      reportError('Save failed', err);
    } finally {
      setBusy(false);
    }
  };

  const cancel = () => {
    loadData();
    setEditing(false);
  };

  if (loading) {
    return <SkeletonText paragraph lineCount={8} />;
  }
  if (!data) {
    return <p>No administration data found.</p>;
  }

  const showEditControls = canEdit && !submitted;
  const team = data.teamMembers ?? [];
  const lead = team.find((m) => m.teamLeadInd === 'Y');

  return (
    <div className="rip-form">
      <div className="protocol-checklist__section-actions">
        {!editing && showEditControls && (
          <Button kind="tertiary" size="lg" onClick={() => setEditing(true)}>
            <span className="protocol-checklist__edit-label">
              <Edit /> Edit
            </span>
          </Button>
        )}
        {editing && (
          <>
            <Button kind="ghost" size="lg" disabled={busy} onClick={cancel}>
              Cancel
            </Button>
            <Button size="lg" disabled={busy} onClick={() => void handleSave()}>
              Save
            </Button>
          </>
        )}
      </div>

      <fieldset className="rip-form__group">
        <legend>Evaluation</legend>
        <div className="rip-form__grid">
          {SCALARS.map((field) =>
            editing ? (
              <TextInput
                key={field.key}
                id={`admin-${field.key}`}
                labelText={field.label}
                value={get(field.key)}
                onChange={(e) => set(field.key, e.target.value)}
              />
            ) : (
              <div className="protocol-checklist__field" key={field.key}>
                <span className="protocol-checklist__label">{field.label}</span>
                <span className="protocol-checklist__value">{get(field.key) || '—'}</span>
              </div>
            ),
          )}
        </div>
        {editing ? (
          <TextArea
            id="admin-additional-comments"
            labelText="Additional comments"
            value={get('additionalComments')}
            onChange={(e) => set('additionalComments', e.target.value)}
          />
        ) : (
          <div className="protocol-checklist__field">
            <span className="protocol-checklist__label">Additional comments</span>
            <span className="protocol-checklist__value protocol-checklist__multiline">
              {get('additionalComments') || '—'}
            </span>
          </div>
        )}
      </fieldset>

      <fieldset className="rip-form__group">
        <legend>Evaluation team</legend>
        <div className="protocol-checklist__field">
          <span className="protocol-checklist__label">Team lead</span>
          <span className="protocol-checklist__value">{lead?.evaluatorDescription || '—'}</span>
        </div>
        {team.length > 0 ? (
          <table className="rip-field-grid">
            <thead>
              <tr>
                <th scope="col">Evaluator</th>
                <th scope="col">Userid</th>
                <th scope="col">Role</th>
                {editing && <th aria-label="Actions" />}
              </tr>
            </thead>
            <tbody>
              {team.map((m, index) => (
                <tr key={`ev-${m.evaluatorUserid ?? index}`}>
                  <td>{m.evaluatorDescription || '—'}</td>
                  <td>{m.evaluatorUserid || '—'}</td>
                  <td>
                    {m.teamLeadInd === 'Y' ? (
                      <Tag type="blue" size="sm">
                        Team lead
                      </Tag>
                    ) : (
                      'Member'
                    )}
                  </td>
                  {editing && (
                    <td className="rip-grid__choice">
                      <Button
                        kind="danger--tertiary"
                        size="sm"
                        hasIconOnly
                        renderIcon={TrashCan}
                        iconDescription="Remove evaluator"
                        disabled={busy}
                        onClick={() => void removeMember(m.evaluatorUserid, m.revisionCount)}
                      />
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <p>No evaluators assigned.</p>
        )}

        {editing && (
          <>
            <div className="rip-form__add-evaluator">
              <Select
                id="admin-evaluator"
                labelText="Add evaluator"
                value={selectedEvaluator}
                onChange={(e) => setSelectedEvaluator(e.target.value)}
              >
                <SelectItem value="" text="—" />
              </Select>
              <Checkbox
                id="admin-add-as-lead"
                labelText="Add as team lead"
                checked={addAsLead}
                onChange={(_e, { checked }) => setAddAsLead(checked)}
              />
              <Button
                kind="tertiary"
                renderIcon={Add}
                disabled={busy || !selectedEvaluator}
                onClick={() => void addMember()}
              >
                Add
              </Button>
            </div>
            <p className="rip-form__hint">
              The evaluator list is not yet available — it requires an IDIR/WebADE directory lookup
              of users with the FREP editor role for the district. Removing existing evaluators
              works.
            </p>
          </>
        )}
      </fieldset>
    </div>
  );
};

export default RipAdministrationView;

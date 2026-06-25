import { Edit, TrashCan } from '@carbon/icons-react';
import {
  Button,
  DatePicker,
  DatePickerInput,
  Select,
  SelectItem,
  SkeletonText,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';

import EvaluatorSearch from '@/pages/ProtocolChecklist/EvaluatorSearch';

import type { CodeOption } from '@/types/configuration';
import type { AdministrationData } from '@/types/protocolChecklist';

import { useConfirm } from '@/context/confirm/useConfirm';
import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';
import { apiErrorMessage } from '@/utils/apiError';
import { formatShortDate } from '@/utils/date';

/**
 * Checklist Administration tab (legacy FREP301 / checklistCostResource) — read-only view with
 * inline editing of the evaluation date and site-access / cost fields, plus the evaluation team
 * (lead + members).
 *
 * Team management: add (save_team_member) and remove (delete_team_member) both work. The legacy
 * "Add evaluator" list was a WebADE/IDIR directory lookup of users with the FREP editor role in the
 * district; it's now an inline FAM-backed search ({@link EvaluatorSearch} → GET
 * /external/v1/users?role=FREP_EDITOR). FAM has no district dimension, so results are FREP editors
 * province-wide, not district-scoped.
 *
 * Note: Evaluation Date and Team Lead are mandatory for Submit, so this tab is where they're set.
 */

type Props = {
  protocol: string;
  checklistId: string;
  canEdit: boolean;
  submitted: boolean;
};

// Access type is a dropdown (site-access codes), and total hours is computed — both handled
// separately below. These are the plain text fields, in legacy FREP301 order.
const SCALARS: { key: keyof AdministrationData; label: string }[] = [
  { key: 'evaluationDate', label: 'Evaluation date (YYYY-MM-DD)' },
  { key: 'blockAccessTime', label: 'Hrs. access time' },
  { key: 'hoursOnBlock', label: 'Hrs. on block' },
  { key: 'peopleOnBlock', label: 'People on block' },
];

const toNumberOrNull = (value?: string): number | null => {
  if (value == null || `${value}`.trim() === '') return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
};

/**
 * Total hours = (hrs access time + hrs on block) × people on block, each term applied only when
 * present — mirrors the legacy {@code ChecklistCostResourceForm.getTotalHours()}. Formatted with a
 * trailing ".0" for whole numbers, as the legacy {@code Float.toString} does (e.g. "0.0", "20.0").
 */
const computeTotalHours = (data: AdministrationData | null): string => {
  if (!data) return '0.0';
  let total = 0;
  const accessTime = toNumberOrNull(data.blockAccessTime);
  if (accessTime !== null) total = accessTime;
  const hoursOnBlock = toNumberOrNull(data.hoursOnBlock);
  if (hoursOnBlock !== null) total += hoursOnBlock;
  const peopleOnBlock = toNumberOrNull(data.peopleOnBlock);
  if (peopleOnBlock !== null) total *= Math.trunc(peopleOnBlock);
  return Number.isInteger(total) ? `${total}.0` : `${total}`;
};

const RipAdministrationView: FC<Props> = ({ protocol, checklistId, canEdit, submitted }) => {
  const { display } = useNotification();
  const confirm = useConfirm();
  const [data, setData] = useState<AdministrationData | null>(null);
  const [accessCodes, setAccessCodes] = useState<CodeOption[]>([]);
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
        .getAdministration(protocol, checklistId)
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
    [protocol, checklistId, reportError],
  );

  useEffect(() => {
    const signal = { cancelled: false };
    loadData(signal);
    return () => {
      signal.cancelled = true;
    };
  }, [loadData]);

  // Access-type (site-access) options for the "Access type" dropdown.
  useEffect(() => {
    let cancelled = false;
    API.configuration
      .getSiteAccessCodes()
      .then((codes) => {
        if (!cancelled) setAccessCodes(codes);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  const addMember = async (evaluatorUserid: string, asTeamLead: boolean) => {
    if (!evaluatorUserid) return;
    setBusy(true);
    try {
      const updated = await API.protocolChecklist.addTeamMember(
        protocol,
        checklistId,
        evaluatorUserid,
        asTeamLead,
      );
      setData(updated);
      display({ kind: 'success', title: 'Evaluator added', timeout: 4000 });
    } catch (err) {
      // Friendly text for the legacy "already on the team" message keys.
      const detail = apiErrorMessage(err, '');
      if (detail.includes('teamMemberNameAlreadySelected')) {
        display({
          kind: 'warning',
          title: 'Already on the team',
          subtitle: 'This evaluator is already a team member.',
          timeout: 6000,
        });
      } else if (detail.includes('teamMemberNameEqualEvaluator')) {
        display({
          kind: 'warning',
          title: 'Already the team lead',
          subtitle: 'This evaluator is already the team lead.',
          timeout: 6000,
        });
      } else {
        reportError('Could not add evaluator', err);
      }
    } finally {
      setBusy(false);
    }
  };

  const removeMember = async (evaluatorUserid?: string, revisionCount?: string) => {
    if (!evaluatorUserid) return;
    if (
      !(await confirm({
        title: 'Remove team member?',
        message: `Remove ${evaluatorUserid} from this checklist? This can't be undone.`,
      }))
    )
      return;
    setBusy(true);
    try {
      const updated = await API.protocolChecklist.removeTeamMember(
        protocol,
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
      const saved = await API.protocolChecklist.saveAdministration(protocol, checklistId, data);
      setData(saved);
      setEditing(false);
      display({ kind: 'success', title: 'Administration saved', timeout: 4000 });
    } catch (err) {
      reportError('Save failed', err);
    } finally {
      setBusy(false);
    }
  };

  // Re-fetch immediately before editing so we hold the current optimistic-lock tokens. Opening,
  // Administration and Notes all persist to biodiversity_checklist and share its revision_count, so
  // a sibling-tab save can leave this view's tokens stale and the cost-resource SAVE fails with
  // "record.modified2". A silent refresh (no skeleton) avoids a flicker.
  const beginEdit = async () => {
    setBusy(true);
    try {
      const fresh = await API.protocolChecklist.getAdministration(protocol, checklistId);
      setData(fresh);
      setEditing(true);
    } catch (err) {
      reportError("We couldn't load the administration data", err);
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
  // The legacy GET returns the team lead separately (teamLeadNameId) — the members cursor only
  // holds non-lead members (evaluator_team_lead_ind = 'N'), so don't look for the lead in there.
  const leadUserid = data.teamLeadNameId;
  const members = (data.teamMembers ?? []).filter((m) => m.teamLeadInd !== 'Y');
  // Users already on the team can't be added again (the legacy proc rejects duplicates).
  const assignedUserIds = [leadUserid, ...members.map((m) => m.evaluatorUserid)].filter(
    (id): id is string => Boolean(id),
  );

  const handleSelectEvaluator = (user: CodeOption, asTeamLead: boolean) => {
    void addMember(user.code, asTeamLead);
  };

  const accessTypeLabel =
    accessCodes.find((c) => c.code === get('siteAccessCode'))?.description || get('siteAccessCode');

  const renderScalar = (field: { key: keyof AdministrationData; label: string }) =>
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
    );

  // Evaluation date uses a calendar picker (writes back the YYYY-MM-DD the proc expects) rather than
  // a free-text field.
  const renderEvaluationDate = () => {
    const value = get('evaluationDate');
    if (!editing) {
      return (
        <div className="protocol-checklist__field">
          <span className="protocol-checklist__label">Evaluation date</span>
          <span className="protocol-checklist__value">{formatShortDate(value) || '—'}</span>
        </div>
      );
    }
    return (
      <DatePicker
        datePickerType="single"
        dateFormat="Y-m-d"
        value={value ? [value] : []}
        onChange={(dates: Date[]) =>
          set('evaluationDate', dates[0] ? dates[0].toISOString().slice(0, 10) : '')
        }
      >
        <DatePickerInput
          id="admin-evaluationDate"
          labelText="Evaluation date"
          placeholder="YYYY-MM-DD"
        />
      </DatePicker>
    );
  };

  return (
    <div className="rip-form">
      <div className="protocol-checklist__section-actions">
        {!editing && showEditControls && (
          <Button kind="tertiary" size="lg" disabled={busy} onClick={() => void beginEdit()}>
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
          {/* Evaluation date — calendar picker */}
          {renderEvaluationDate()}

          {/* Access type — dropdown of site-access codes (FREP301) */}
          {editing ? (
            <Select
              id="admin-site-access"
              labelText="Access type"
              value={get('siteAccessCode')}
              onChange={(e) => set('siteAccessCode', e.target.value)}
            >
              <SelectItem value="" text="—" />
              {accessCodes.map((option) => (
                <SelectItem key={option.code} value={option.code} text={option.description} />
              ))}
            </Select>
          ) : (
            <div className="protocol-checklist__field">
              <span className="protocol-checklist__label">Access type</span>
              <span className="protocol-checklist__value">{accessTypeLabel || '—'}</span>
            </div>
          )}

          {/* hrs access time / hrs on block / people on block */}
          {SCALARS.slice(1).map(renderScalar)}

          {/* Total hours = (access time + hrs on block) × people on block — read-only, computed */}
          <div className="protocol-checklist__field">
            <span className="protocol-checklist__label">Total hours</span>
            <span className="protocol-checklist__value">
              <strong>{computeTotalHours(data)}</strong>
            </span>
          </div>
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

        {editing && (
          <EvaluatorSearch
            onSelect={handleSelectEvaluator}
            excludeUserIds={assignedUserIds}
            leadAssigned={Boolean(leadUserid)}
            disabled={busy}
          />
        )}

        <div className="rip-form__selected-evaluators">
          <div className="protocol-checklist__field">
            <span className="protocol-checklist__label">Team lead</span>
            <span className="protocol-checklist__value">
              {leadUserid ? (
                <span className="rip-form__evaluator">
                  {leadUserid}
                  {editing && (
                    <Button
                      kind="danger--ghost"
                      size="sm"
                      hasIconOnly
                      renderIcon={TrashCan}
                      iconDescription="Remove team lead"
                      disabled={busy}
                      onClick={() => void removeMember(leadUserid, data.teamLeadRevisionCount)}
                    />
                  )}
                </span>
              ) : (
                '—'
              )}
            </span>
          </div>
          <div className="protocol-checklist__field">
            <span className="protocol-checklist__label">Team member(s)</span>
            <span className="protocol-checklist__value">
              {members.length > 0 ? (
                <span className="rip-form__evaluator-list">
                  {members.map((m, index) => (
                    <span className="rip-form__evaluator" key={`ev-${m.evaluatorUserid ?? index}`}>
                      {m.evaluatorDescription || m.evaluatorUserid}
                      {editing && (
                        <Button
                          kind="danger--ghost"
                          size="sm"
                          hasIconOnly
                          renderIcon={TrashCan}
                          iconDescription="Remove evaluator"
                          disabled={busy}
                          onClick={() => void removeMember(m.evaluatorUserid, m.revisionCount)}
                        />
                      )}
                    </span>
                  ))}
                </span>
              ) : (
                '—'
              )}
            </span>
          </div>
        </div>
      </fieldset>
    </div>
  );
};

export default RipAdministrationView;

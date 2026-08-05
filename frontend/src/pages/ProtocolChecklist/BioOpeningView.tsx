import { Edit } from '@carbon/icons-react';
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
import { useCallback, useEffect, useMemo, useState, type FC, type ReactNode } from 'react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import { requiredLabel } from '@/utils/requiredLabel';

import type { CodeOption } from '@/types/configuration';
import type { BiodiversityOpening } from '@/types/protocolChecklist';

import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { OPENING_TEXT_LIMITS, validateOpening } from '@/pages/ProtocolChecklist/openingValidation';
import API from '@/services/APIs';
import { apiErrorMessage } from '@/utils/apiError';
import { formatShortDate } from '@/utils/date';
import { byteLength } from '@/utils/textLimits';

/**
 * Biodiversity Opening section (FREP210) — read-only form mirroring the legacy layout, edited
 * inline in place (no separate page). Drives off the typed DTO and round-trips the full record
 * (including unsurfaced columns + revision count) on save. The innovative-practice and
 * invasive-plant fields are answer-coded (Yes/No), resolved against the shared checklist answers.
 */

type Props = {
  checklistId: string;
  canEdit: boolean;
  submitted: boolean;
};

// The evaluator id comes from the legacy `biodiversity_evaluator_name` table (written by the FREP301
// proc), so it may be stored bare/differently-cased (e.g. `AVSODHI`) while `providerUsername` is the
// full `IDIR\AVSODHI`. Compare the way the backend does — strip the directory prefix, ignore case —
// so "Assign it to me" hides once the current user already is the evaluator.
const sameEvaluator = (a?: string, b?: string): boolean => {
  const norm = (v?: string) => v?.split('\\').pop()?.trim().toUpperCase() ?? '';
  return norm(a) !== '' && norm(a) === norm(b);
};

const BioOpeningView: FC<Props> = ({ checklistId, canEdit, submitted }) => {
  const { display } = useNotification();
  const { user } = useAuth();
  const me = user?.providerUsername;
  const [data, setData] = useState<BiodiversityOpening | null>(null);
  const [answers, setAnswers] = useState<CodeOption[]>([]);
  const [ratings, setRatings] = useState<CodeOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);

  // Inline validation runs live off the edited data (like SiteDetail): a field's error shows the
  // moment it's invalid and clears as soon as it's fixed. The Save handler just blocks while any
  // remain — no separate error toast.
  const fieldErrors = useMemo<Record<string, string>>(
    () => (editing && data ? validateOpening(data) : {}),
    [editing, data],
  );
  const hasErrors = Object.keys(fieldErrors).length > 0;

  const reportError = useCallback(
    (title: string, err: unknown) =>
      display({
        kind: 'error',
        title,
        subtitle: apiErrorMessage(err),
        timeout: 9000,
      }),
    [display],
  );

  const loadData = useCallback(
    (signal?: { cancelled: boolean }) => {
      setLoading(true);
      API.protocolChecklist
        .getBiodiversityOpening(checklistId)
        .then((d) => {
          if (!signal?.cancelled) setData(d);
        })
        .catch((err: unknown) => {
          if (!signal?.cancelled) reportError("We couldn't load the opening", err);
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
    // Innovative-practice and invasive-plant are Yes/No questions — exclude "NA", matching the
    // legacy FREP210 dropdowns.
    API.configuration
      .getChecklistAnswers('NA')
      .then((c) => !signal.cancelled && setAnswers(c))
      .catch(() => undefined);
    // Evaluator-opinion "Rating" is a coded dropdown (frep_site_evaluation_code), not free text —
    // matching the legacy FREP210 ratingDropDown.
    API.configuration
      .getSiteEvaluationCodes()
      .then((c) => !signal.cancelled && setRatings(c))
      .catch(() => undefined);
    return () => {
      signal.cancelled = true;
    };
  }, [loadData]);

  const get = (key: keyof BiodiversityOpening): string =>
    ((data as Record<string, unknown>)?.[key] as string | undefined) ?? '';
  const set = (key: keyof BiodiversityOpening, value: string) =>
    setData((prev) => (prev ? ({ ...prev, [key]: value } as BiodiversityOpening) : prev));

  const todayIso = new Date().toISOString().slice(0, 10);

  // "Assign it to me" (mirrors CHR): claim the evaluator (team lead) for the current user. Takes
  // effect on Save — the backend replaces any existing lead. Clear the resolved name so the userid
  // shows until the save round-trips the FAM-resolved name.
  const assignToMe = () => {
    if (!me) return;
    setData((prev) =>
      prev
        ? ({ ...prev, teamLeadNameId: me, teamLeadName: undefined } as BiodiversityOpening)
        : prev,
    );
  };

  const handleSave = async () => {
    if (!data) return;
    // Errors are already shown inline; just block the save while any remain (no error toast).
    if (hasErrors) return;
    setBusy(true);
    try {
      const saved = await API.protocolChecklist.saveBiodiversityOpening(checklistId, data);
      setData(saved);
      setEditing(false);
      display({ kind: 'success', title: 'Opening saved', timeout: 4000 });
    } catch (err) {
      reportError('Save failed', err);
    } finally {
      setBusy(false);
    }
  };

  // Re-fetch immediately before editing so we hold the current optimistic-lock token. The Opening,
  // Administration and Notes tabs share one biodiversity_checklist.revision_count, so a sibling-tab
  // save can otherwise leave this view's token stale (FREP_210_BIO_OPENING.SAVE then rejects it with
  // "record.modified2"). A silent refresh (no loading skeleton) avoids a flicker.
  const beginEdit = async () => {
    setBusy(true);
    try {
      const fresh = await API.protocolChecklist.getBiodiversityOpening(checklistId);
      setData(fresh);
      setEditing(true);
    } catch (err) {
      reportError("We couldn't load the opening", err);
    } finally {
      setBusy(false);
    }
  };

  const cancel = () => {
    loadData();
    setEditing(false);
  };

  // Read-only label/value cell.
  const cell = (label: string, value: string, multiline = false): ReactNode => (
    <div className="protocol-checklist__field" key={label}>
      <span className="protocol-checklist__label">{label}</span>
      <span
        className={
          multiline
            ? 'protocol-checklist__value protocol-checklist__multiline'
            : 'protocol-checklist__value'
        }
      >
        {value || '—'}
      </span>
    </div>
  );

  const text = (key: keyof BiodiversityOpening, label: string): ReactNode =>
    editing ? (
      <TextInput
        key={key}
        id={`bio-${key}`}
        labelText={label}
        value={get(key)}
        onChange={(e) => set(key, e.target.value)}
        invalid={Boolean(fieldErrors[key])}
        invalidText={fieldErrors[key]}
      />
    ) : (
      cell(label, get(key))
    );

  // Length-limited fields carry a live counter. `validateOpening` already blocks the save and
  // supplies the error text, so the counter only reports the count — the two can't disagree.
  const textarea = (key: keyof BiodiversityOpening, label: string, required = false): ReactNode => {
    if (!editing) {
      return cell(label, get(key), true);
    }
    const limit = OPENING_TEXT_LIMITS[key];
    const field = (
      <TextArea
        key={key}
        id={`bio-${key}`}
        labelText={requiredLabel(label, required)}
        value={get(key)}
        onChange={(e) => set(key, e.target.value)}
        invalid={Boolean(fieldErrors[key])}
        invalidText={fieldErrors[key]}
      />
    );
    return limit === undefined ? (
      field
    ) : (
      <FieldWithCounter key={key} used={byteLength(get(key))} limit={limit}>
        {field}
      </FieldWithCounter>
    );
  };

  // Evaluation date — a single-date picker writing back the YYYY-MM-DD the SAVE proc expects (mirrors
  // the CHR Opening tab). Future dates are blocked at the picker (maxDate) and in validateOpening.
  const dateField = (key: keyof BiodiversityOpening, label: string, required = false): ReactNode =>
    editing ? (
      <DatePicker
        key={key}
        className="frep-date-picker bio-opening__date-field"
        datePickerType="single"
        dateFormat="Y-m-d"
        maxDate={todayIso}
        value={get(key) ? [get(key)] : []}
        onChange={(dates: Date[]) => set(key, dates[0] ? dates[0].toISOString().slice(0, 10) : '')}
      >
        <DatePickerInput
          id={`bio-${key}`}
          labelText={requiredLabel(label, required)}
          placeholder="YYYY-MM-DD"
          invalid={Boolean(fieldErrors[key])}
          invalidText={fieldErrors[key]}
        />
      </DatePicker>
    ) : (
      cell(label, formatShortDate(get(key)))
    );

  // Evaluator — read-only, claimed via "Assign it to me" (mirrors the CHR Assessed-by widget). The
  // button shows for any editor who isn't already the evaluator, so takeover is allowed.
  const evaluatorField = (): ReactNode => {
    const currentId = get('teamLeadNameId');
    const displayName = get('teamLeadName') || currentId;
    if (!editing) {
      return cell('Evaluator', displayName);
    }
    return (
      <div className="protocol-checklist__field" key="evaluator">
        <span className="protocol-checklist__label">{requiredLabel('Evaluator', true)}</span>
        <span
          className="protocol-checklist__value"
          style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem' }}
        >
          <span>{displayName || '—'}</span>
          {me && !sameEvaluator(currentId, me) && (
            <Button kind="ghost" size="sm" disabled={busy} onClick={assignToMe}>
              Assign it to me
            </Button>
          )}
        </span>
        {fieldErrors.teamLeadNameId && (
          <span style={{ color: 'var(--cds-text-error)', fontSize: '0.75rem' }}>
            {fieldErrors.teamLeadNameId}
          </span>
        )}
      </div>
    );
  };

  const optionText = (code: string, options: CodeOption[]): string =>
    options.find((o) => o.code === code)?.description ?? code;

  const select = (
    key: keyof BiodiversityOpening,
    label: string,
    options: CodeOption[] = answers,
    required = false,
  ): ReactNode =>
    editing ? (
      <Select
        key={key}
        id={`bio-${key}`}
        labelText={requiredLabel(label, required)}
        value={get(key)}
        onChange={(e) => set(key, e.target.value)}
        invalid={Boolean(fieldErrors[key])}
        invalidText={fieldErrors[key]}
      >
        <SelectItem value="" text="—" />
        {options.map((o) => (
          <SelectItem key={o.code} value={o.code} text={o.description} />
        ))}
      </Select>
    ) : (
      cell(label, optionText(get(key), options))
    );

  if (loading) {
    return <SkeletonText paragraph lineCount={8} />;
  }
  if (!data) {
    return <p>No opening found.</p>;
  }

  const showEditControls = canEdit && !submitted;

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
        <div className="rip-form__grid bio-opening__evaluation-grid">
          {dateField('evaluationDate', 'Evaluation date', true)}
          {evaluatorField()}
        </div>
      </fieldset>

      <fieldset className="rip-form__group">
        <legend>Opening identification</legend>
        <div className="rip-form__grid">
          {/* Read-only RESULTS reference fields (from frep_selected_site) — never editable. */}
          {cell('Harvest complete date', formatShortDate(get('harvestDate')))}
          {cell('Net area to be reforested (ha)', get('netArea'))}
          {cell('Gross area (ha)', get('grossArea'))}
          {text('frepWtpOverride', 'FREP gross area override (ha)')}
        </div>
        {textarea('locationDescription', 'Location description', true)}
      </fieldset>

      <fieldset className="rip-form__group">
        <legend>Innovative practices</legend>
        <div className="rip-form__grid">
          {select(
            'innovativePracticeInd',
            'Innovative / unique forest practices used?',
            answers,
            true,
          )}
        </div>
        {textarea(
          'innovativePracticesComment',
          'Please describe',
          get('innovativePracticeInd') === 'Y',
        )}
      </fieldset>

      <fieldset className="rip-form__group">
        <legend>Evaluator opinion</legend>
        <div className="rip-form__grid">
          {select(
            'frepSiteEvaluationCode',
            'Rating (stand-level biodiversity maintained)',
            ratings,
            true,
          )}
        </div>
        {textarea('evaluatorOpinionComment', 'Rationale')}
      </fieldset>

      <fieldset className="rip-form__group">
        <legend>Invasive plants</legend>
        <div className="rip-form__grid">
          {select('invasivePlantIndicator', 'Invasive plant species present?', answers, true)}
        </div>
        {textarea('invasivePlantComment', 'Comments', get('invasivePlantIndicator') === 'Y')}
      </fieldset>
    </div>
  );
};

export default BioOpeningView;

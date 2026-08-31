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

import OutstandingPanel from './OutstandingPanel';
import RequiredLegend from './RequiredLegend';

import type { CodeOption } from '@/types/configuration';
import type { BiodiversityOpening } from '@/types/protocolChecklist';

import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import {
  OPENING_REQUIRED_LABELS,
  OPENING_REQUIRED_SECTIONS,
  OPENING_TEXT_LIMITS,
  evaluationDateRemovalError,
  openingFormatErrors,
  openingRequiredErrors,
  validateOpening,
} from '@/pages/ProtocolChecklist/openingValidation';
import { inFormSection } from '@/pages/ProtocolChecklist/tabStatus';
import API from '@/services/APIs';
import { apiErrorMessage } from '@/utils/apiError';
import { NO_AUTOFILL } from '@/utils/autofill';
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
  /** Called after a save or delete lands, so the tab-completion dots re-derive. */
  onSaved?: () => void;
  /** `error` once a submit has been refused — see OutstandingPanel. */
  tone?: 'neutral' | 'error';
};

// The evaluator id comes from the legacy `biodiversity_evaluator_name` table (written by the FREP301
// proc), so it may be stored bare/differently-cased (e.g. `AVSODHI`) while `providerUsername` is the
// full `IDIR\AVSODHI`. Compare the way the backend does — strip the directory prefix, ignore case —
// so "Assign it to me" hides once the current user already is the evaluator.
const sameEvaluator = (a?: string, b?: string): boolean => {
  const norm = (v?: string) => v?.split('\\').pop()?.trim().toUpperCase() ?? '';
  return norm(a) !== '' && norm(a) === norm(b);
};

const BioOpeningView: FC<Props> = ({ checklistId, canEdit, submitted, onSaved, tone }) => {
  const { display } = useNotification();
  const { user } = useAuth();
  const me = user?.providerUsername;
  const [data, setData] = useState<BiodiversityOpening | null>(null);
  /**
   * The record as it is *stored*: the load, the pre-edit refresh and the save response all land here.
   *
   * `data` doubles as the edit buffer, so it says what the user is typing rather than what is kept.
   * The banner and the tab count have to describe the latter — reading them off `data` meant a
   * brand-new checklist raised the banner the moment the first character was typed, listing every
   * field the user had not reached yet.
   */
  const [stored, setStored] = useState<BiodiversityOpening | null>(null);
  const [answers, setAnswers] = useState<CodeOption[]>([]);
  const [ratings, setRatings] = useState<CodeOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  // Whether a save has been attempted on this edit. Errors stay hidden until then — see below.
  const [showErrors, setShowErrors] = useState(false);
  // A save landed in this session, so the incomplete banner can lead with "Opening saved" rather
  // than reporting gaps in a record the user has not touched yet.

  // Validation runs live off the edited data, but is only *displayed* once the user has tried to
  // save. Opening a record that is merely incomplete (no evaluation date, no location description)
  // would otherwise greet the user with a wall of red before they have typed anything.
  //
  // `allErrors` drives the save guard, `fieldErrors` drives the rendering. Keeping the rendered
  // name unchanged means every `invalid` / `invalidText` site below is gated automatically, with no
  // chance of one being missed. After the first save attempt the errors are live again, so each
  // clears the moment it is fixed.
  const allErrors = useMemo<Record<string, string>>(
    () =>
      editing && data
        ? { ...validateOpening(data), ...evaluationDateRemovalError(stored, data) }
        : {},
    [editing, data, stored],
  );
  const fieldErrors = showErrors ? allErrors : {};

  // Only a value the column cannot store blocks the save (too long, future date, malformed
  // override) — plus removing an evaluation date the proc has no way to clear. A required field left
  // blank does not — see openingRequiredErrors and evaluationDateRemovalError.
  const blockingErrors = useMemo<Record<string, string>>(
    () =>
      editing && data
        ? { ...openingFormatErrors(data), ...evaluationDateRemovalError(stored, data) }
        : {},
    [editing, data, stored],
  );
  const hasBlockingErrors = Object.keys(blockingErrors).length > 0;

  // Required fields still owed, from the *stored* record — not the in-progress edits — so the banner
  // and the tab count describe what is actually kept. Recomputed on every load and save.
  const missingRequired = useMemo(() => {
    if (!stored) return [];
    const missing = openingRequiredErrors(stored);
    // Listed in tab order (the order of OPENING_REQUIRED_LABELS), so the banner reads top-to-bottom
    // the way the user would work down the form.
    return Object.keys(OPENING_REQUIRED_LABELS).filter((key) => key in missing);
  }, [stored]);

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
          if (!signal?.cancelled) {
            setData(d);
            setStored(d);
          }
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
    // Reveal any errors now: this is the first point the user has asked for the form to be
    // complete. Blank required fields are shown but do not stop the save — only values the column
    // cannot store do. No error toast either way; they are shown inline.
    setShowErrors(true);
    if (hasBlockingErrors) return;
    setBusy(true);
    try {
      const saved = await API.protocolChecklist.saveBiodiversityOpening(checklistId, data);
      setData(saved);
      setStored(saved);
      setEditing(false);
      onSaved?.();
      // The banner below carries the "saved, but still incomplete" wording once the record is
      // stored; the toast stays a plain confirmation so the two do not say the same thing twice.
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
      setStored(fresh);
      setShowErrors(false);
      setEditing(true);
    } catch (err) {
      reportError("We couldn't load the opening", err);
    } finally {
      setBusy(false);
    }
  };

  const cancel = () => {
    loadData();
    setShowErrors(false);
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
        autoComplete="off"
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
        autoComplete="off"
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
          {...NO_AUTOFILL}
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
          <div className="protocol-checklist__field-error">{fieldErrors.teamLeadNameId}</div>
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
        autoComplete="off"
        key={key}
        id={`bio-${key}`}
        labelText={requiredLabel(label, required)}
        value={get(key)}
        onChange={(e) => set(key, e.target.value)}
        invalid={Boolean(fieldErrors[key])}
        invalidText={fieldErrors[key]}
      >
        <SelectItem value="" text="Choose an option" />
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

  // Shown whenever anything is outstanding, including on a checklist nobody has touched yet: the
  // list is the answer to "why can't I submit?", and it is least useful when withheld from someone
  // just starting. Ungrouped — the Opening tab is a single form, so every item is one record's.
  const incompleteBanner = (
    <OutstandingPanel
      groups={[
        {
          items: missingRequired.map((key) =>
            inFormSection(OPENING_REQUIRED_LABELS[key] ?? key, OPENING_REQUIRED_SECTIONS[key]),
          ),
        },
      ]}
      tone={tone}
    />
  );

  return (
    <div className="rip-form">
      {incompleteBanner}
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
      {/* Only while editing: the read-only view marks nothing required, so the key would explain
          a symbol that is not on the page. */}
      {editing && <RequiredLegend />}

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
        <div className="rip-form__grid rip-form__grid--wide">
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
        <div className="rip-form__grid rip-form__grid--wide">
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
        <div className="rip-form__grid rip-form__grid--wide">
          {select('invasivePlantIndicator', 'Invasive plant species present?', answers, true)}
        </div>
        {textarea('invasivePlantComment', 'Comments', get('invasivePlantIndicator') === 'Y')}
      </fieldset>
    </div>
  );
};

export default BioOpeningView;

import { Edit } from '@carbon/icons-react';
import { Button, Select, SelectItem, SkeletonText, TextArea, TextInput } from '@carbon/react';
import { useCallback, useEffect, useState, type FC, type ReactNode } from 'react';

import { requiredLabel } from '@/utils/requiredLabel';

import type { CodeOption } from '@/types/configuration';
import type { BiodiversityOpening } from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

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

const BioOpeningView: FC<Props> = ({ checklistId, canEdit, submitted }) => {
  const { display } = useNotification();
  const [data, setData] = useState<BiodiversityOpening | null>(null);
  const [answers, setAnswers] = useState<CodeOption[]>([]);
  const [ratings, setRatings] = useState<CodeOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  // Set once the user tries to save with a blank Location description, so the required-field error
  // only shows after a save attempt (not the moment they enter edit mode).
  const [attemptedSave, setAttemptedSave] = useState(false);

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

  const handleSave = async () => {
    if (!data) return;
    // Location description is the one mandatory Opening field (legacy submit check
    // frep.submit.biodiversity.opening checks COUNT(location_description)). Block the save up front so
    // it never has to surface as a submit-time validation error.
    if (get('locationDescription').trim() === '') {
      setAttemptedSave(true);
      display({
        kind: 'error',
        title: 'Location description is required',
        subtitle: 'Enter a Location description before saving the Opening.',
        timeout: 6000,
      });
      return;
    }
    setBusy(true);
    try {
      const saved = await API.protocolChecklist.saveBiodiversityOpening(checklistId, data);
      setData(saved);
      setEditing(false);
      setAttemptedSave(false);
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
      setAttemptedSave(false);
      setEditing(true);
    } catch (err) {
      reportError("We couldn't load the opening", err);
    } finally {
      setBusy(false);
    }
  };

  const cancel = () => {
    loadData();
    setAttemptedSave(false);
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
      />
    ) : (
      cell(label, get(key))
    );

  const textarea = (key: keyof BiodiversityOpening, label: string, required = false): ReactNode =>
    editing ? (
      <TextArea
        key={key}
        id={`bio-${key}`}
        labelText={requiredLabel(label, required)}
        value={get(key)}
        onChange={(e) => set(key, e.target.value)}
        invalid={required && attemptedSave && get(key).trim() === ''}
        invalidText={`${label} is required.`}
      />
    ) : (
      cell(label, get(key), true)
    );

  const optionText = (code: string, options: CodeOption[]): string =>
    options.find((o) => o.code === code)?.description ?? code;

  const select = (
    key: keyof BiodiversityOpening,
    label: string,
    options: CodeOption[] = answers,
  ): ReactNode =>
    editing ? (
      <Select
        key={key}
        id={`bio-${key}`}
        labelText={label}
        value={get(key)}
        onChange={(e) => set(key, e.target.value)}
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
        <legend>Opening identification</legend>
        <div className="rip-form__grid">
          {/* Read-only RESULTS reference fields (from frep_selected_site) — never editable. */}
          {cell('Harvest complete date', get('harvestDate'))}
          {cell('Net area to be reforested (ha)', get('netArea'))}
          {cell('Gross area (ha)', get('grossArea'))}
          {text('frepWtpOverride', 'FREP gross area override (ha)')}
        </div>
        {textarea('locationDescription', 'Location description', true)}
      </fieldset>

      <fieldset className="rip-form__group">
        <legend>Innovative practices</legend>
        <div className="rip-form__grid">
          {select('innovativePracticeInd', 'Innovative / unique forest practices used?')}
        </div>
        {textarea('innovativePracticesComment', 'Please describe')}
      </fieldset>

      <fieldset className="rip-form__group">
        <legend>Evaluator opinion</legend>
        <div className="rip-form__grid">
          {select(
            'frepSiteEvaluationCode',
            'Rating (stand-level biodiversity maintained)',
            ratings,
          )}
        </div>
        {textarea('evaluatorOpinionComment', 'Rationale')}
      </fieldset>

      <fieldset className="rip-form__group">
        <legend>Invasive plants</legend>
        <div className="rip-form__grid">
          {select('invasivePlantIndicator', 'Invasive plant species present?')}
        </div>
        {textarea('invasivePlantComment', 'Comments')}
      </fieldset>
    </div>
  );
};

export default BioOpeningView;

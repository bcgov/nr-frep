import { Edit } from '@carbon/icons-react';
import { Button } from '@carbon/react';
import {
  useCallback,
  useEffect,
  useState,
  type Dispatch,
  type FC,
  type SetStateAction,
} from 'react';

import type {
  ProtocolChecklistField,
  ProtocolChecklistSection,
  RipNoAnswerRow,
  RipOtherIndRow,
  RipQuestionRow,
} from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

/**
 * Inline editor for the repeated-answer Riparian sections — Other indicators (FREP232) and
 * Questions (FREP233) — mirroring the legacy forms:
 *   - FREP232: indicators grouped under section headers, each scored Yes/No.
 *   - FREP233: questions scored Yes/No/NA (NA only when applicable), plus the "cause of No answers"
 *     checkboxes.
 *
 * Drives off the section's display fields (the FREP GET proc returns the full reference list +
 * text); the typed reads only carry saved rows. Save reconstructs the full typed DTO from the draft
 * (ids + revision counts round-trip) and refreshes from the display read.
 */

type Props = {
  section: ProtocolChecklistSection;
  checklistId: string;
  canEdit: boolean;
  submitted: boolean;
};

type Row = Record<string, string>;

function parseRows(fields: ProtocolChecklistField[], prefix: string): Row[] {
  const pattern = new RegExp(`^${prefix} (\\d+) - (.+)$`);
  const byIndex = new Map<number, Row>();
  for (const field of fields) {
    const match = field.label.match(pattern);
    if (!match) continue;
    const index = Number(match[1]);
    if (!byIndex.has(index)) byIndex.set(index, {});
    byIndex.get(index)![match[2]] = field.value ?? '';
  }
  return [...byIndex.keys()].sort((a, b) => a - b).map((key) => byIndex.get(key)!);
}

const blank = (value: string | undefined) => (value && value !== '' ? value : undefined);

const RipChecklistGridEdit: FC<Props> = ({ section, checklistId, canEdit, submitted }) => {
  const { display } = useNotification();
  const isQuestions = section.id === 'questions';

  const [fields, setFields] = useState<ProtocolChecklistField[]>(section.fields);
  const [indicators, setIndicators] = useState<Row[]>([]);
  const [questions, setQuestions] = useState<Row[]>([]);
  const [noAnswers, setNoAnswers] = useState<Row[]>([]);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);

  const hydrate = useCallback((source: ProtocolChecklistField[]) => {
    setIndicators(parseRows(source, 'Other indicator'));
    setQuestions(parseRows(source, 'Question'));
    setNoAnswers(parseRows(source, 'No answer'));
  }, []);

  useEffect(() => {
    setFields(section.fields);
    hydrate(section.fields);
  }, [section.fields, hydrate]);

  const updateRow = (
    setter: Dispatch<SetStateAction<Row[]>>,
    index: number,
    key: string,
    value: string,
  ) => setter((prev) => prev.map((r, i) => (i === index ? { ...r, [key]: value } : r)));

  const buildOtherIndicators = (): RipOtherIndRow[] =>
    indicators.map((r) => ({
      otherIndTypeId: blank(r['Type id']),
      quesSectCode: blank(r['Section code']),
      headerQuestionInd: blank(r['Header question ind']),
      question: blank(r['Question']),
      otherIndicatorId: blank(r['Indicator id']),
      otherAnswerInd: blank(r['Answer ind']),
      revisionCount: blank(r['Revision count']),
    }));

  const buildQuestions = (): RipQuestionRow[] =>
    questions.map((r) => ({
      checklistId: blank(r['Checklist id']) ?? checklistId,
      checklistQuestionId: blank(r['Question id']),
      questionNo: blank(r['Question no']),
      question: blank(r['Question']),
      chanMorphologyCode: blank(r['Channel morphology code']),
      applicableInd: blank(r['Applicable ind']),
      morphologyDesc: blank(r['Morphology desc']),
      questionType: blank(r['Question type']),
      questionDesc: blank(r['Question desc']),
      subQuestion: blank(r['Sub question']),
      answerCode: blank(r['Answer code']),
      revisionCount: blank(r['Revision count']),
    }));

  const buildNoAnswers = (): RipNoAnswerRow[] =>
    noAnswers.map((r) => ({
      answerImpactId: blank(r['Impact id']),
      checklistId: blank(r['Checklist id']) ?? checklistId,
      checklistQuestionId: blank(r['Question id']),
      questionNo: blank(r['Question no']),
      answerImpactType: blank(r['Impact type']),
      answerImpactDesc: blank(r['Impact desc']),
      sortOrder: blank(r['Sort order']),
      answerInd: blank(r['Answer ind']),
      revisionCount: blank(r['Revision count']),
    }));

  const handleSave = async () => {
    setBusy(true);
    try {
      if (isQuestions) {
        await API.protocolChecklist.saveRipQuestions(checklistId, {
          checklistId,
          questions: buildQuestions(),
          noAnswers: buildNoAnswers(),
        });
      } else {
        await API.protocolChecklist.saveRipOtherIndicators(checklistId, {
          checklistId,
          indicators: buildOtherIndicators(),
        });
      }
      const refreshed = await API.protocolChecklist.getChecklist('rip', checklistId);
      const fresh = refreshed.sections.find((s) => s.id === section.id);
      if (fresh) {
        setFields(fresh.fields);
        hydrate(fresh.fields);
      }
      setEditing(false);
      display({ kind: 'success', title: 'Saved', timeout: 4000 });
    } catch (err) {
      display({
        kind: 'error',
        title: 'Save failed',
        subtitle: err instanceof Error ? err.message : 'Unknown error',
        timeout: 9000,
      });
    } finally {
      setBusy(false);
    }
  };

  const cancel = () => {
    hydrate(fields);
    setEditing(false);
  };

  const showEditControls = canEdit && !submitted;

  return (
    <div className="rip-field-data">
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

      {!isQuestions && (
        <table className="rip-field-grid">
          <caption>Other indicators</caption>
          <thead>
            <tr>
              <th scope="col">Indicator</th>
              <th scope="col">Yes</th>
              <th scope="col">No</th>
            </tr>
          </thead>
          <tbody>
            {indicators.map((row, index) =>
              row['Header question ind'] === 'Y' ? (
                <tr key={`oi-${index}`} className="rip-grid__section">
                  <th colSpan={3} scope="colgroup">
                    {[row['Section code'], row['Question']].filter(Boolean).join('. ')}
                  </th>
                </tr>
              ) : (
                <tr key={`oi-${index}`}>
                  <td>{row['Question'] || '—'}</td>
                  <td className="rip-grid__choice">
                    <input
                      type="radio"
                      name={`oi-${index}`}
                      checked={row['Answer ind'] === 'Y'}
                      disabled={!editing}
                      aria-label={`${row['Question']} — Yes`}
                      onChange={() => updateRow(setIndicators, index, 'Answer ind', 'Y')}
                    />
                  </td>
                  <td className="rip-grid__choice">
                    <input
                      type="radio"
                      name={`oi-${index}`}
                      checked={row['Answer ind'] === 'N'}
                      disabled={!editing}
                      aria-label={`${row['Question']} — No`}
                      onChange={() => updateRow(setIndicators, index, 'Answer ind', 'N')}
                    />
                  </td>
                </tr>
              ),
            )}
          </tbody>
        </table>
      )}

      {isQuestions && (
        <>
          <table className="rip-field-grid">
            <caption>Questions</caption>
            <thead>
              <tr>
                <th scope="col">Question No.</th>
                <th scope="col">Question</th>
                <th scope="col">Yes</th>
                <th scope="col">No</th>
                <th scope="col">NA</th>
              </tr>
            </thead>
            <tbody>
              {questions.map((row, index) => (
                <tr key={`q-${index}`}>
                  <td>{row['Question no'] || '—'}</td>
                  <td>
                    {[row['Question'], row['Question desc']].filter(Boolean).join('. ') || '—'}
                  </td>
                  <td className="rip-grid__choice">
                    <input
                      type="radio"
                      name={`q-${index}`}
                      checked={row['Answer code'] === 'Y'}
                      disabled={!editing}
                      aria-label="Yes"
                      onChange={() => updateRow(setQuestions, index, 'Answer code', 'Y')}
                    />
                  </td>
                  <td className="rip-grid__choice">
                    <input
                      type="radio"
                      name={`q-${index}`}
                      checked={row['Answer code'] === 'N'}
                      disabled={!editing}
                      aria-label="No"
                      onChange={() => updateRow(setQuestions, index, 'Answer code', 'N')}
                    />
                  </td>
                  <td className="rip-grid__choice">
                    {row['Applicable ind'] === 'Y' ? (
                      <input
                        type="radio"
                        name={`q-${index}`}
                        checked={row['Answer code'] === 'NA'}
                        disabled={!editing}
                        aria-label="Not applicable"
                        onChange={() => updateRow(setQuestions, index, 'Answer code', 'NA')}
                      />
                    ) : (
                      ''
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {noAnswers.length > 0 && (
            <table className="rip-field-grid">
              <caption>Cause of &quot;No&quot; answers</caption>
              <thead>
                <tr>
                  <th scope="col">Question No.</th>
                  <th scope="col">Cause</th>
                  <th scope="col">Applies</th>
                </tr>
              </thead>
              <tbody>
                {noAnswers.map((row, index) => (
                  <tr key={`na-${index}`}>
                    <td>{row['Question no'] || '—'}</td>
                    <td>{row['Impact desc'] || '—'}</td>
                    <td className="rip-grid__choice">
                      <input
                        type="checkbox"
                        checked={row['Answer ind'] === 'Y'}
                        disabled={!editing}
                        aria-label={`${row['Impact desc']} applies`}
                        onChange={(e) =>
                          updateRow(setNoAnswers, index, 'Answer ind', e.target.checked ? 'Y' : 'N')
                        }
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </div>
  );
};

export default RipChecklistGridEdit;

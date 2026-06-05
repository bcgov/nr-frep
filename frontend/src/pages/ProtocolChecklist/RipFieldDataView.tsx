import { Edit } from '@carbon/icons-react';
import { Button, Checkbox, TextInput } from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';

import type {
  ProtocolChecklistField,
  RipContinuousIndRow,
  RipPointIndRow,
  RiparianFieldData,
} from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

/**
 * Riparian Field Data section (FREP231) — read-only grid mirroring the legacy two-table layout,
 * with inline editing in place (no separate page).
 *
 * Rows are driven by the section's display fields (the FREP231 GET proc, which returns the full
 * indicator reference list + descriptions); the editable transect measurements (M1–6) and the
 * continuous notes/value bind to a local draft. Save reconstructs the full typed DTO from the draft
 * — including ids and revision counts — so unsurfaced columns round-trip.
 */

type Props = {
  fields: ProtocolChecklistField[];
  checklistId: string;
  canEdit: boolean;
  submitted: boolean;
};

type Row = Record<string, string>;

const TRANSECTS = [1, 2, 3, 4, 5, 6] as const;

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

const RipFieldDataView: FC<Props> = ({ fields, checklistId, canEdit, submitted }) => {
  const { display } = useNotification();
  const [points, setPoints] = useState<Row[]>([]);
  const [continuous, setContinuous] = useState<Row[]>([]);
  const [streamDry, setStreamDry] = useState('');
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);

  // Hydrate the draft from a set of display fields (initial render + after save/cancel).
  const hydrate = useCallback((source: ProtocolChecklistField[]) => {
    setPoints(parseRows(source, 'Point indicator'));
    setContinuous(parseRows(source, 'Continuous indicator'));
    setStreamDry(source.find((f) => f.label === 'Field data stream dry')?.value ?? '');
  }, []);

  useEffect(() => {
    hydrate(fields);
  }, [fields, hydrate]);

  const setPointCell = (index: number, key: string, value: string) =>
    setPoints((prev) => prev.map((r, i) => (i === index ? { ...r, [key]: value } : r)));
  const setContCell = (index: number, key: string, value: string) =>
    setContinuous((prev) => prev.map((r, i) => (i === index ? { ...r, [key]: value } : r)));

  const toDto = (): RiparianFieldData => ({
    checklistId,
    fieldDataStreamReachDry: streamDry || 'N',
    points: points.map(
      (r): RipPointIndRow => ({
        pointIndicatorId: blank(r['Indicator id']),
        questionNo: blank(r['Question no']),
        pointIndType: blank(r['Type']),
        transectNo: blank(r['Transect no']),
        measure1: r['Measure 1'] ?? '',
        measure2: r['Measure 2'] ?? '',
        measure3: r['Measure 3'] ?? '',
        measure4: r['Measure 4'] ?? '',
        measure5: r['Measure 5'] ?? '',
        measure6: r['Measure 6'] ?? '',
        threshold: blank(r['Threshold']),
        mean: blank(r['Mean']),
        revisionCount: blank(r['Revision count']),
      }),
    ),
    continuous: continuous.map(
      (r): RipContinuousIndRow => ({
        continuousIndId: blank(r['Indicator id']),
        questionNo: blank(r['Question no']),
        continuousIndType: blank(r['Type']),
        question: blank(r['Question']),
        total: r['Total'] ?? '',
        comments: r['Comments'] ?? '',
        threshold: blank(r['Threshold']),
        revisionCount: blank(r['Revision count']),
      }),
    ),
  });

  const handleSave = async () => {
    setBusy(true);
    try {
      await API.protocolChecklist.saveRipFieldData(checklistId, toDto());
      // Refresh from the display read so ids / revision counts / mean reflect the save.
      const refreshed = await API.protocolChecklist.getChecklist('rip', checklistId);
      const section = refreshed.sections.find((s) => s.id === 'field-data');
      if (section) hydrate(section.fields);
      setEditing(false);
      display({ kind: 'success', title: 'Field data saved', timeout: 4000 });
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

      <p className="rip-field-data__flag">
        <Checkbox
          id="rip-field-stream-dry"
          labelText="Stream reach is dry"
          checked={streamDry === 'Y'}
          disabled={!editing}
          onChange={(_e, { checked }) => setStreamDry(checked ? 'Y' : 'N')}
        />
      </p>

      {points.length > 0 && (
        <table className="rip-field-grid">
          <caption>Point indicators (measured at 6 equidistant transects)</caption>
          <thead>
            <tr>
              <th rowSpan={2} scope="col">
                Question No.
              </th>
              <th rowSpan={2} scope="col">
                Point indicator
              </th>
              <th colSpan={6} scope="colgroup">
                Transect No. (6 equidistant points)
              </th>
              <th rowSpan={2} scope="col">
                Threshold
              </th>
              <th rowSpan={2} scope="col">
                Mean
              </th>
            </tr>
            <tr>
              {TRANSECTS.map((n) => (
                <th key={n} scope="col">
                  {n}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {points.map((row, index) => (
              <tr key={`pt-${index}`}>
                <td>{row['Question no'] || '—'}</td>
                <td>{row['Transect no'] || '—'}</td>
                {TRANSECTS.map((n) => {
                  const key = `Measure ${n}`;
                  return (
                    <td key={n}>
                      {editing ? (
                        <TextInput
                          id={`pt-${index}-m${n}`}
                          labelText={`Transect ${n}`}
                          hideLabel
                          size="sm"
                          value={row[key] ?? ''}
                          onChange={(e) => setPointCell(index, key, e.target.value)}
                        />
                      ) : (
                        (row[key] ?? '')
                      )}
                    </td>
                  );
                })}
                <td>{row['Threshold'] ?? ''}</td>
                <td>{row['Mean'] ?? ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {continuous.length > 0 && (
        <table className="rip-field-grid">
          <caption>Continuous indicators (measured along the whole reach)</caption>
          <thead>
            <tr>
              <th scope="col">Question No.</th>
              <th scope="col">Continuous indicator</th>
              <th scope="col">Notes</th>
              <th scope="col">Threshold</th>
              <th scope="col">%</th>
            </tr>
          </thead>
          <tbody>
            {continuous.map((row, index) => (
              <tr key={`cont-${index}`}>
                <td>{row['Question no'] || '—'}</td>
                <td>{row['Question'] || '—'}</td>
                <td>
                  {editing ? (
                    <TextInput
                      id={`cont-${index}-notes`}
                      labelText="Notes"
                      hideLabel
                      size="sm"
                      value={row['Comments'] ?? ''}
                      onChange={(e) => setContCell(index, 'Comments', e.target.value)}
                    />
                  ) : (
                    (row['Comments'] ?? '')
                  )}
                </td>
                <td>{row['Threshold'] ?? ''}</td>
                <td>
                  {editing ? (
                    <TextInput
                      id={`cont-${index}-total`}
                      labelText="Percent"
                      hideLabel
                      size="sm"
                      value={row['Total'] ?? ''}
                      onChange={(e) => setContCell(index, 'Total', e.target.value)}
                    />
                  ) : (
                    (row['Total'] ?? '')
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};

export default RipFieldDataView;

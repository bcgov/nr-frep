import { Add, Edit, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  RadioButton,
  RadioButtonGroup,
  Select,
  SelectItem,
  SkeletonText,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC, type ReactNode } from 'react';

import type { CodeOption } from '@/types/configuration';
import type { RipStreamEdgeRow, RiparianStreamOpening } from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

/**
 * Riparian Stream / Opening section (FREP230) — read-only form mirroring the legacy layout, with
 * inline editing in place (no separate page). Drives off the typed DTO; coded fields render as
 * dropdowns / radios in edit mode and resolve to descriptions in read mode. Save round-trips the
 * full DTO (including unsurfaced columns + revision count).
 */

type Props = {
  checklistId: string;
  canEdit: boolean;
  submitted: boolean;
};

const UTM_ZONES = ['7', '8', '9', '10', '11'];
const ZONE_OPTIONS: CodeOption[] = UTM_ZONES.map((z) => ({ code: z, description: z }));

const RETENTION_ROWS: {
  label: string;
  domsOnPlans: keyof RiparianStreamOpening;
  domsInField: keyof RiparianStreamOpening;
  undrstryOnPlans: keyof RiparianStreamOpening;
  undrstryInField: keyof RiparianStreamOpening;
}[] = [
  {
    label: '% Retention in first 10m of the RMA (all classes)',
    domsOnPlans: 'rttnRmaDomsOnPlans',
    domsInField: 'rttnRmaDomsInField',
    undrstryOnPlans: 'rttnRmaUndrstryOnPlans',
    undrstryInField: 'rttnRmaUndrstryInField',
  },
  {
    label: '% Retention in rest of the RRZ (for S1, S2, S3)',
    domsOnPlans: 'rttnRrzDomsOnPlans',
    domsInField: 'rttnRrzDomsInField',
    undrstryOnPlans: 'rttnRrzUndrstryOnPlans',
    undrstryInField: 'rttnRrzUndrstryInField',
  },
  {
    label: '% Retention in rest of the RMA (all classes)',
    domsOnPlans: 'rttnRmzDomsOnPlans',
    domsInField: 'rttnRmzDomsInField',
    undrstryOnPlans: 'rttnRmzUndrstryOnPlans',
    undrstryInField: 'rttnRmzUndrstryInField',
  },
];

const RipStreamOpeningView: FC<Props> = ({ checklistId, canEdit, submitted }) => {
  const { display } = useNotification();
  const [data, setData] = useState<RiparianStreamOpening | null>(null);
  const [streamClasses, setStreamClasses] = useState<CodeOption[]>([]);
  const [answers, setAnswers] = useState<CodeOption[]>([]);
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
        .getRipStreamOpening(checklistId)
        .then((d) => {
          if (!signal?.cancelled) setData(d);
        })
        .catch((err: unknown) => {
          if (!signal?.cancelled) reportError("We couldn't load the stream opening", err);
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
    API.configuration
      .getStreamClasses()
      .then((c) => !signal.cancelled && setStreamClasses(c))
      .catch(() => undefined);
    // Invasive plant is a Yes/No question — exclude "NA", matching legacy FREP230
    // (Frep230RIPStreamOpenForm: getExtCodes("frep.lookup.checklist.answerCodes", "NA")).
    API.configuration
      .getChecklistAnswers('NA')
      .then((c) => !signal.cancelled && setAnswers(c))
      .catch(() => undefined);
    return () => {
      signal.cancelled = true;
    };
  }, [loadData]);

  const get = (key: keyof RiparianStreamOpening): string =>
    ((data as Record<string, unknown>)?.[key] as string | undefined) ?? '';
  const set = (key: keyof RiparianStreamOpening, value: string) =>
    setData((prev) => (prev ? ({ ...prev, [key]: value } as RiparianStreamOpening) : prev));

  const setEdge = (index: number, patch: Partial<RipStreamEdgeRow>) =>
    setData((prev) =>
      prev
        ? {
            ...prev,
            streamEdge: (prev.streamEdge ?? []).map((r, i) =>
              i === index ? { ...r, ...patch } : r,
            ),
          }
        : prev,
    );
  const addEdge = () =>
    setData((prev) => (prev ? { ...prev, streamEdge: [...(prev.streamEdge ?? []), {}] } : prev));
  const removeEdge = (index: number) =>
    setData((prev) =>
      prev ? { ...prev, streamEdge: (prev.streamEdge ?? []).filter((_, i) => i !== index) } : prev,
    );

  const handleSave = async () => {
    if (!data) return;
    setBusy(true);
    try {
      const saved = await API.protocolChecklist.saveRipStreamOpening(checklistId, data);
      setData(saved);
      setEditing(false);
      display({ kind: 'success', title: 'Stream opening saved', timeout: 4000 });
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

  // Read-only label/value cell.
  const cell = (label: string, value: string): ReactNode => (
    <div className="protocol-checklist__field" key={label}>
      <span className="protocol-checklist__label">{label}</span>
      <span className="protocol-checklist__value">{value || '—'}</span>
    </div>
  );

  const text = (key: keyof RiparianStreamOpening, label: string): ReactNode =>
    editing ? (
      <TextInput
        key={key}
        id={`rip-${key}`}
        labelText={label}
        value={get(key)}
        onChange={(e) => set(key, e.target.value)}
      />
    ) : (
      cell(label, get(key))
    );

  const optionText = (options: CodeOption[], code: string): string =>
    options.find((o) => o.code === code)?.description ?? code;

  const select = (
    key: keyof RiparianStreamOpening,
    label: string,
    options: CodeOption[],
  ): ReactNode =>
    editing ? (
      <Select
        key={key}
        id={`rip-${key}`}
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
      cell(label, optionText(options, get(key)))
    );

  const radio = (
    key: keyof RiparianStreamOpening,
    label: string,
    opts: { value: string; label: string }[],
  ): ReactNode =>
    editing ? (
      <RadioButtonGroup
        key={key}
        name={`rip-${key}`}
        legendText={label}
        valueSelected={get(key)}
        onChange={(v) => set(key, String(v ?? ''))}
      >
        {opts.map((o) => (
          <RadioButton
            key={o.value}
            id={`rip-${key}-${o.value}`}
            labelText={o.label}
            value={o.value}
          />
        ))}
      </RadioButtonGroup>
    ) : (
      cell(label, opts.find((o) => o.value === get(key))?.label ?? get(key))
    );

  const checkbox = (key: keyof RiparianStreamOpening, label: string, onValue: string): ReactNode =>
    editing ? (
      <Checkbox
        key={key}
        id={`rip-${key}`}
        labelText={label}
        checked={get(key) === onValue}
        onChange={(_e, { checked }) => set(key, checked ? onValue : '')}
      />
    ) : (
      cell(label, get(key) === onValue ? 'Yes' : 'No')
    );

  if (loading) {
    return <SkeletonText paragraph lineCount={8} />;
  }
  if (!data) {
    return <p>No stream opening found.</p>;
  }

  const showEditControls = canEdit && !submitted;
  const edges = data.streamEdge ?? [];

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
        <legend>Stream / opening identification</legend>
        <div className="rip-form__grid">
          {text('sampleNumber', 'Sample #')}
          {text('rangeUsePlan', 'Range use plan')}
          {text('pastureId', 'Pasture ID')}
          {text('streamName', 'Stream name')}
          {text('streamLocationInd', 'Stream location')}
          {select('plnRiparianStrmRmaCls', 'Stream class on plans', streamClasses)}
          {checkbox('plnRiparianStrNaInd', 'Stream class on plans — N/A', 'Y')}
          {select('actRiparianStrmRmaCls', 'Stream class in field', streamClasses)}
          {text('channelWidth', 'Channel width (m)')}
          {text('channelDepth', 'Channel depth (m)')}
          {text('channelGradientPct', 'Channel gradient (%)')}
          {text('reachLocationFrom', 'Reach location from (m)')}
          {text('reachLocationTo', 'Reach location to (m)')}
          {radio('reachLocationUpsDsInd', 'Reach location', [
            { value: 'U', label: 'U/S from' },
            { value: 'D', label: 'D/S from' },
          ])}
          {text('reachLocationFromDesc', 'Reach location from description')}
          {radio('riparianChanMorphology', 'Channel morphology', [
            { value: 'RC', label: 'Riffle/pool or Cascade/pool' },
            { value: 'SP', label: 'Step/pool' },
            { value: 'NA', label: 'Non-alluvial' },
          ])}
          {checkbox('utmSignal', 'No UTM signal available', 'N')}
          {radio('utmAtReference', 'UTM at', [
            { value: 'US', label: 'U/S' },
            { value: 'DS', label: 'D/S' },
          ])}
          {select('utmZone', 'UTM zone', ZONE_OPTIONS)}
          {text('utmEasting', 'UTM easting')}
          {text('utmNorthing', 'UTM northing')}
        </div>
      </fieldset>

      <fieldset className="rip-form__group">
        <legend>Riparian retention information in RMA</legend>

        <table className="rip-field-grid">
          <caption>Average distance (m) from stream edge</caption>
          <thead>
            <tr>
              <th scope="col">Measure type</th>
              <th scope="col">Measurement (m)</th>
              {editing && <th aria-label="Actions" />}
            </tr>
          </thead>
          <tbody>
            {edges.map((row, index) => (
              <tr key={`edge-${index}`}>
                <td>
                  {editing ? (
                    <TextInput
                      id={`edge-type-${index}`}
                      labelText="Measure type"
                      hideLabel
                      size="sm"
                      value={row.measureType ?? ''}
                      onChange={(e) => setEdge(index, { measureType: e.target.value })}
                    />
                  ) : (
                    (row.measureType ?? '—')
                  )}
                </td>
                <td>
                  {editing ? (
                    <TextInput
                      id={`edge-val-${index}`}
                      labelText="Measurement"
                      hideLabel
                      size="sm"
                      value={row.measurement ?? ''}
                      onChange={(e) => setEdge(index, { measurement: e.target.value })}
                    />
                  ) : (
                    (row.measurement ?? '')
                  )}
                </td>
                {editing && (
                  <td>
                    <Button
                      kind="danger--tertiary"
                      size="sm"
                      renderIcon={TrashCan}
                      iconDescription="Remove"
                      hasIconOnly
                      onClick={() => removeEdge(index)}
                    />
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
        {editing && (
          <Button kind="ghost" size="sm" renderIcon={Add} onClick={addEdge}>
            Add measurement
          </Button>
        )}

        <table className="rip-retention">
          <thead>
            <tr>
              <th scope="col" />
              <th scope="col">Dominants &amp; codominants on left side</th>
              <th scope="col">Dominants &amp; codominants in right side</th>
              <th scope="col">Understory retention on left side</th>
              <th scope="col">Understory retention in right side</th>
            </tr>
          </thead>
          <tbody>
            {RETENTION_ROWS.map((row) => (
              <tr key={row.label}>
                <th scope="row">{row.label}</th>
                {(
                  [
                    row.domsOnPlans,
                    row.domsInField,
                    row.undrstryOnPlans,
                    row.undrstryInField,
                  ] as (keyof RiparianStreamOpening)[]
                ).map((key) => (
                  <td key={String(key)}>
                    {editing ? (
                      <TextInput
                        id={`rip-${String(key)}`}
                        labelText={`${row.label} %`}
                        hideLabel
                        size="sm"
                        value={get(key)}
                        onChange={(e) => set(key, e.target.value)}
                      />
                    ) : (
                      get(key) || ''
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </fieldset>

      <fieldset className="rip-form__group">
        <legend>Invasive plants</legend>
        <div className="rip-form__grid">
          {select('invasivePlantIndicator', 'Invasive plant', answers)}
        </div>
        {editing ? (
          <TextArea
            id="rip-invasive-comment"
            labelText="Invasive plant comment"
            value={get('invasivePlantComment')}
            onChange={(e) => set('invasivePlantComment', e.target.value)}
          />
        ) : (
          cell('Invasive plant comment', get('invasivePlantComment'))
        )}
      </fieldset>
    </div>
  );
};

export default RipStreamOpeningView;

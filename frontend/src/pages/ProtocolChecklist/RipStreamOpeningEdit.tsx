import { Add, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Column,
  Grid,
  InlineNotification,
  SkeletonText,
  Stack,
  TextInput,
  Tile,
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import type { RipStreamEdgeRow, RiparianStreamOpening } from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import API from '@/services/APIs';

import './protocolChecklist.scss';

type FieldDef = { key: string; label: string };

const GROUPS: { title: string; fields: FieldDef[] }[] = [
  {
    title: 'Stream',
    fields: [
      { key: 'sampleNumber', label: 'Sample number' },
      { key: 'rangeUsePlan', label: 'Range use plan' },
      { key: 'pastureId', label: 'Pasture id' },
      { key: 'streamName', label: 'Stream name' },
      { key: 'streamLocationInd', label: 'Stream location indicator' },
      { key: 'plnRiparianStrmRmaCls', label: 'Planned riparian RMA class' },
      { key: 'actRiparianStrmRmaCls', label: 'Actual riparian RMA class' },
      { key: 'plnRiparianStrNaInd', label: 'Planned riparian N/A indicator' },
    ],
  },
  {
    title: 'Channel & reach',
    fields: [
      { key: 'channelWidth', label: 'Channel width' },
      { key: 'channelGradientPct', label: 'Channel gradient %' },
      { key: 'channelDepth', label: 'Channel depth' },
      { key: 'reachLocationTo', label: 'Reach location to' },
      { key: 'reachLocationFrom', label: 'Reach location from' },
      { key: 'reachLocationUpsDsInd', label: 'Reach location u/s-d/s indicator' },
      { key: 'reachLocationFromDesc', label: 'Reach location from description' },
      { key: 'riparianChanMorphology', label: 'Channel morphology' },
    ],
  },
  {
    title: 'UTM',
    fields: [
      { key: 'utmSignal', label: 'UTM signal' },
      { key: 'utmAtReference', label: 'UTM at reference' },
      { key: 'utmZone', label: 'UTM zone' },
      { key: 'utmEasting', label: 'UTM easting' },
      { key: 'utmNorthing', label: 'UTM northing' },
    ],
  },
  {
    title: 'Retention — RMA',
    fields: [
      { key: 'rttnRmaDomsOnPlans', label: 'Dominants on plans %' },
      { key: 'rttnRmaDomsOnPlansInd', label: 'Dominants on plans indicator' },
      { key: 'rttnRmaDomsInField', label: 'Dominants in field %' },
      { key: 'rttnRmaUndrstryOnPlans', label: 'Understory on plans %' },
      { key: 'rttnRmaUndrstryOnPlnI', label: 'Understory on plans indicator' },
      { key: 'rttnRmaUndrstryInField', label: 'Understory in field %' },
    ],
  },
  {
    title: 'Retention — RRZ',
    fields: [
      { key: 'rttnRrzDomsOnPlans', label: 'Dominants on plans %' },
      { key: 'rttnRrzDomsOnPlansInd', label: 'Dominants on plans indicator' },
      { key: 'rttnRrzDomsInFieldPct', label: 'Dominants in field %' },
      { key: 'rttnRrzDomsInField', label: 'Dominants in field' },
      { key: 'rttnRrzUndrstryOnPlans', label: 'Understory on plans %' },
      { key: 'rttnRrzUndrstryOnPlnI', label: 'Understory on plans indicator' },
      { key: 'rttnRrzUndrstryFldPct', label: 'Understory in field %' },
      { key: 'rttnRrzUndrstryInField', label: 'Understory in field' },
    ],
  },
  {
    title: 'Retention — RMZ',
    fields: [
      { key: 'rttnRmzDomsOnPlans', label: 'Dominants on plans %' },
      { key: 'rttnRmzDomsOnPlansInd', label: 'Dominants on plans indicator' },
      { key: 'rttnRmzDomsInField', label: 'Dominants in field %' },
      { key: 'rttnRmzUndrstryOnPlans', label: 'Understory on plans %' },
      { key: 'rttnRmzUndrstryOnPlnI', label: 'Understory on plans indicator' },
      { key: 'rttnRmzUndrstryInField', label: 'Understory in field %' },
    ],
  },
  {
    title: 'Invasive plants',
    fields: [
      { key: 'invasivePlantIndicator', label: 'Invasive plant indicator' },
      { key: 'invasivePlantComment', label: 'Invasive plant comment' },
    ],
  },
];

const RipStreamOpeningEditPage: FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { display } = useNotification();
  const { canEdit } = useAuthorization();

  const [current, setCurrent] = useState<RiparianStreamOpening | null>(null);
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState(true);
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

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([
      API.protocolChecklist.getRipStreamOpening(id),
      API.protocolChecklist.getChecklist('rip', id).then(
        (c) => c.statusCode,
        () => undefined,
      ),
    ])
      .then(([opening, statusCode]) => {
        if (cancelled) return;
        setCurrent(opening);
        setStatus(statusCode);
      })
      .catch((err: unknown) => {
        if (!cancelled) reportError("We couldn't load the stream opening", err);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [id, reportError]);

  const readOnly = !canEdit || status === 'SUB';

  const get = (key: string): string =>
    ((current as Record<string, unknown>)?.[key] as string | undefined) ?? '';
  const set = (key: string, value: string) =>
    setCurrent((prev) => (prev ? ({ ...prev, [key]: value } as RiparianStreamOpening) : prev));

  const setEdge = (index: number, patch: Partial<RipStreamEdgeRow>) =>
    setCurrent((prev) =>
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
    setCurrent((prev) => (prev ? { ...prev, streamEdge: [...(prev.streamEdge ?? []), {}] } : prev));
  const removeEdge = (index: number) =>
    setCurrent((prev) =>
      prev ? { ...prev, streamEdge: (prev.streamEdge ?? []).filter((_, i) => i !== index) } : prev,
    );

  const handleSave = async () => {
    if (!current) return;
    setBusy(true);
    try {
      const saved = await API.protocolChecklist.saveRipStreamOpening(id, current);
      setCurrent(saved);
      display({ kind: 'success', title: 'Stream opening saved', timeout: 4000 });
    } catch (err) {
      reportError('Save failed', err);
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return (
      <Grid fullWidth className="default-grid">
        <Column sm={4} md={8} lg={16}>
          <SkeletonText paragraph lineCount={8} />
        </Column>
      </Grid>
    );
  }

  return (
    <Grid fullWidth className="default-grid">
      <Column sm={4} md={8} lg={16}>
        <h1>Riparian stream opening — checklist {id}</h1>
      </Column>
      {readOnly && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="info"
            title={status === 'SUB' ? 'Submitted — read only' : 'View only'}
            hideCloseButton
            lowContrast
          />
        </Column>
      )}
      <Column sm={4} md={8} lg={12}>
        {!current ? (
          <p>No stream opening found.</p>
        ) : (
          <Tile>
            <Stack gap={6}>
              {GROUPS.map((group) => (
                <fieldset key={group.title} className="chr-checklist__fieldset">
                  <legend>{group.title}</legend>
                  {group.fields.map((f) => (
                    <TextInput
                      key={f.key}
                      id={`rip-${f.key}`}
                      labelText={f.label}
                      value={get(f.key)}
                      disabled={readOnly}
                      onChange={(e) => set(f.key, e.target.value)}
                    />
                  ))}
                </fieldset>
              ))}
              <fieldset className="chr-checklist__fieldset">
                <legend>Stream edge measurements</legend>
                {(current.streamEdge ?? []).map((r, index) => (
                  <div key={`edge-${index}`} className="chr-checklist__form">
                    <TextInput
                      id={`edge-type-${index}`}
                      labelText="Measure type"
                      value={r.measureType ?? ''}
                      disabled={readOnly}
                      onChange={(e) => setEdge(index, { measureType: e.target.value })}
                    />
                    <TextInput
                      id={`edge-value-${index}`}
                      labelText="Measurement"
                      value={r.measurement ?? ''}
                      disabled={readOnly}
                      onChange={(e) => setEdge(index, { measurement: e.target.value })}
                    />
                    {!readOnly && (
                      <Button
                        kind="danger--tertiary"
                        size="sm"
                        renderIcon={TrashCan}
                        onClick={() => removeEdge(index)}
                      >
                        Remove
                      </Button>
                    )}
                  </div>
                ))}
                {!readOnly && (
                  <Button kind="ghost" size="sm" renderIcon={Add} onClick={addEdge}>
                    Add measurement
                  </Button>
                )}
              </fieldset>

              <div className="protocol-checklist__actions">
                {!readOnly && (
                  <Button onClick={() => void handleSave()} disabled={busy}>
                    Save stream opening
                  </Button>
                )}
                <Button kind="ghost" onClick={() => navigate(-1)}>
                  Back
                </Button>
              </div>
            </Stack>
          </Tile>
        )}
      </Column>
    </Grid>
  );
};

export default RipStreamOpeningEditPage;

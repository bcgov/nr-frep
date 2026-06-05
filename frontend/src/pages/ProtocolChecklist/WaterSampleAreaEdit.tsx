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

import type {
  WaterSampleArea,
  WtrAccessRoadRow,
  WtrDisturbanceRow,
} from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import API from '@/services/APIs';

import './protocolChecklist.scss';

type FieldDef = { key: string; label: string };

const GROUPS: { title: string; fields: FieldDef[] }[] = [
  {
    title: 'Access & watershed',
    fields: [
      { key: 'siteAccessCode', label: 'Site access code' },
      { key: 'mainAccessRoadNumber', label: 'Main access road number' },
      { key: 'mainWatershedDescription', label: 'Main watershed description' },
    ],
  },
  {
    title: 'Drinking water / special resource',
    fields: [
      { key: 'drinkingWaterAnswerCode', label: 'Drinking water answer' },
      { key: 'waterIntakeComment', label: 'Water intake comment' },
      { key: 'intakeToCutblockDistance', label: 'Intake to cutblock distance' },
      { key: 'waterIntakeConnectivityCode', label: 'Water intake connectivity' },
      { key: 'intakeToCutblockComment', label: 'Intake to cutblock comment' },
      { key: 'specResourceAnswerCode', label: 'Special resource answer' },
      { key: 'specialResourceValueComment', label: 'Special resource comment' },
    ],
  },
  {
    title: 'Activity indicators',
    fields: [
      { key: 'reportedDisturbanceInd', label: 'Reported disturbance ind' },
      { key: 'fertilizerUseOnRoadInd', label: 'Fertilizer use on road ind' },
      { key: 'fertilizerUseWithinBlckInd', label: 'Fertilizer use within block ind' },
      { key: 'sensitiveSoilAnswerCode', label: 'Sensitive soil answer' },
      { key: 'herbicideUseOnRoadInd', label: 'Herbicide use on road ind' },
      { key: 'herbicideUseWithinBlockInd', label: 'Herbicide use within block ind' },
      { key: 'pesticideUseOnRoadInd', label: 'Pesticide use on road ind' },
      { key: 'pesticideUseWithinBlockInd', label: 'Pesticide use within block ind' },
      { key: 'streamCrossingsInd', label: 'Stream crossings ind' },
      { key: 'roadsParallelToStreamInd', label: 'Roads parallel to stream ind' },
      { key: 'unstableSlopesInd', label: 'Unstable slopes ind' },
      { key: 'sensitiveSoilsInd', label: 'Sensitive soils ind' },
      { key: 'adjacentHarvestingInd', label: 'Adjacent harvesting ind' },
      { key: 'livestockConcernsInd', label: 'Livestock concerns ind' },
      { key: 'otherActivityInd', label: 'Other activity ind' },
      { key: 'otherActivityDescription', label: 'Other activity description' },
    ],
  },
  {
    title: 'Notes & evaluation',
    fields: [
      { key: 'noteDescription', label: 'Note description' },
      { key: 'blockAccessTime', label: 'Block access time' },
      { key: 'hoursOnBlock', label: 'Hours on block' },
      { key: 'peopleOnBlock', label: 'People on block' },
      { key: 'invasivePlantAnswerCode', label: 'Invasive plant answer' },
      { key: 'invasivePlantComment', label: 'Invasive plant comment' },
    ],
  },
];

const WaterSampleAreaEditPage: FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { display } = useNotification();
  const { canEdit } = useAuthorization();

  const [current, setCurrent] = useState<WaterSampleArea | null>(null);
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  const reportError = useCallback(
    (heading: string, err: unknown) =>
      display({
        kind: 'error',
        title: heading,
        subtitle: err instanceof Error ? err.message : 'Unknown error',
        timeout: 9000,
      }),
    [display],
  );

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([
      API.protocolChecklist.getWaterSampleArea(id),
      API.protocolChecklist.getChecklist('wat', id).then(
        (c) => c.statusCode,
        () => undefined,
      ),
    ])
      .then(([area, statusCode]) => {
        if (cancelled) return;
        setCurrent(area);
        setStatus(statusCode);
      })
      .catch((err: unknown) => {
        if (!cancelled) reportError("We couldn't load the sample area", err);
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
    setCurrent((prev) => (prev ? ({ ...prev, [key]: value } as WaterSampleArea) : prev));

  const setDist = (index: number, patch: Partial<WtrDisturbanceRow>) =>
    setCurrent((prev) =>
      prev
        ? {
            ...prev,
            disturbances: (prev.disturbances ?? []).map((r, i) =>
              i === index ? { ...r, ...patch } : r,
            ),
          }
        : prev,
    );
  const addDist = () =>
    setCurrent((prev) =>
      prev ? { ...prev, disturbances: [...(prev.disturbances ?? []), {}] } : prev,
    );
  const removeDist = (index: number) =>
    setCurrent((prev) =>
      prev
        ? { ...prev, disturbances: (prev.disturbances ?? []).filter((_, i) => i !== index) }
        : prev,
    );

  const setRoad = (index: number, patch: Partial<WtrAccessRoadRow>) =>
    setCurrent((prev) =>
      prev
        ? {
            ...prev,
            accessRoads: (prev.accessRoads ?? []).map((r, i) =>
              i === index ? { ...r, ...patch } : r,
            ),
          }
        : prev,
    );
  const addRoad = () =>
    setCurrent((prev) =>
      prev ? { ...prev, accessRoads: [...(prev.accessRoads ?? []), {}] } : prev,
    );
  const removeRoad = (index: number) =>
    setCurrent((prev) =>
      prev
        ? { ...prev, accessRoads: (prev.accessRoads ?? []).filter((_, i) => i !== index) }
        : prev,
    );

  const handleSave = async () => {
    if (!current) return;
    setBusy(true);
    try {
      const saved = await API.protocolChecklist.saveWaterSampleArea(id, current);
      setCurrent(saved);
      display({ kind: 'success', title: 'Sample area saved', timeout: 4000 });
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
        <h1>Water sample area — checklist {id}</h1>
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
          <p>No sample area found.</p>
        ) : (
          <Tile>
            <Stack gap={6}>
              {GROUPS.map((group) => (
                <fieldset key={group.title} className="chr-checklist__fieldset">
                  <legend>{group.title}</legend>
                  {group.fields.map((f) => (
                    <TextInput
                      key={f.key}
                      id={`wtr-${f.key}`}
                      labelText={f.label}
                      value={get(f.key)}
                      disabled={readOnly}
                      onChange={(e) => set(f.key, e.target.value)}
                    />
                  ))}
                </fieldset>
              ))}

              <fieldset className="chr-checklist__fieldset">
                <legend>Disturbances</legend>
                {(current.disturbances ?? []).map((r, index) => (
                  <div key={`dist-${index}`} className="chr-checklist__form">
                    <TextInput
                      id={`dist-code-${index}`}
                      labelText="Disturbance code"
                      value={r.disturbanceCode ?? ''}
                      disabled={readOnly}
                      onChange={(e) => setDist(index, { disturbanceCode: e.target.value })}
                    />
                    <TextInput
                      id={`dist-age-${index}`}
                      labelText="Age code"
                      value={r.disturbanceAgeCode ?? ''}
                      disabled={readOnly}
                      onChange={(e) => setDist(index, { disturbanceAgeCode: e.target.value })}
                    />
                    <TextInput
                      id={`dist-num-${index}`}
                      labelText="Number"
                      value={r.disturbanceNumber ?? ''}
                      disabled={readOnly}
                      onChange={(e) => setDist(index, { disturbanceNumber: e.target.value })}
                    />
                    {!readOnly && (
                      <Button
                        kind="danger--tertiary"
                        size="sm"
                        renderIcon={TrashCan}
                        onClick={() => removeDist(index)}
                      >
                        Remove
                      </Button>
                    )}
                  </div>
                ))}
                {!readOnly && (
                  <Button kind="ghost" size="sm" renderIcon={Add} onClick={addDist}>
                    Add disturbance
                  </Button>
                )}
              </fieldset>

              <fieldset className="chr-checklist__fieldset">
                <legend>Access roads</legend>
                {(current.accessRoads ?? []).map((r, index) => (
                  <div key={`road-${index}`} className="chr-checklist__form">
                    <TextInput
                      id={`road-type-${index}`}
                      labelText="Road type"
                      value={r.accessRoadType ?? ''}
                      disabled={readOnly}
                      onChange={(e) => setRoad(index, { accessRoadType: e.target.value })}
                    />
                    <TextInput
                      id={`road-status-${index}`}
                      labelText="Status code"
                      value={r.accessRoadStatusCode ?? ''}
                      disabled={readOnly}
                      onChange={(e) => setRoad(index, { accessRoadStatusCode: e.target.value })}
                    />
                    <TextInput
                      id={`road-length-${index}`}
                      labelText="Approx length"
                      value={r.approximateRoadLength ?? ''}
                      disabled={readOnly}
                      onChange={(e) => setRoad(index, { approximateRoadLength: e.target.value })}
                    />
                    <TextInput
                      id={`road-age-${index}`}
                      labelText="Approx age"
                      value={r.approximateRoadAge ?? ''}
                      disabled={readOnly}
                      onChange={(e) => setRoad(index, { approximateRoadAge: e.target.value })}
                    />
                    {!readOnly && (
                      <Button
                        kind="danger--tertiary"
                        size="sm"
                        renderIcon={TrashCan}
                        onClick={() => removeRoad(index)}
                      >
                        Remove
                      </Button>
                    )}
                  </div>
                ))}
                {!readOnly && (
                  <Button kind="ghost" size="sm" renderIcon={Add} onClick={addRoad}>
                    Add access road
                  </Button>
                )}
              </fieldset>

              <div className="protocol-checklist__actions">
                {!readOnly && (
                  <Button onClick={() => void handleSave()} disabled={busy}>
                    Save sample area
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

export default WaterSampleAreaEditPage;

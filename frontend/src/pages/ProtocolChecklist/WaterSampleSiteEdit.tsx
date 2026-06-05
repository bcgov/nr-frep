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

import type { WaterSampleSite } from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import API from '@/services/APIs';

import './protocolChecklist.scss';

const FIELDS: { key: string; label: string }[] = [
  { key: 'waterSiteType', label: 'Site type' },
  { key: 'waterStreamWidthCode', label: 'Stream width code' },
  { key: 'evaluatorNameId', label: 'Evaluator name id' },
  { key: 'domesticIntakeInd', label: 'Domestic intake ind' },
  { key: 'sampleSiteNumber', label: 'Sample site number' },
  { key: 'utmSignal', label: 'UTM signal' },
  { key: 'utmZone', label: 'UTM zone' },
  { key: 'utmEasting', label: 'UTM easting' },
  { key: 'utmNorthing', label: 'UTM northing' },
  { key: 'roadTypeCode', label: 'Road type code' },
  { key: 'roadUseCode', label: 'Road use code' },
  { key: 'roadReference', label: 'Road reference' },
  { key: 'watershedReference', label: 'Watershed reference' },
  { key: 'communityWatershedInd', label: 'Community watershed ind' },
  { key: 'waterCompromisedInd', label: 'Water compromised ind' },
  { key: 'otherObservedConditionInd', label: 'Other observed condition ind' },
  { key: 'otherObservedConditionDesc', label: 'Other observed condition desc' },
  { key: 'otherSolutionInd', label: 'Other solution ind' },
  { key: 'otherSolutionDescription', label: 'Other solution description' },
  { key: 'assessmentComment', label: 'Assessment comment' },
  { key: 'rangeComment', label: 'Range comment' },
];

const WaterSampleSiteEditPage: FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { display } = useNotification();
  const { canEdit } = useAuthorization();

  const [current, setCurrent] = useState<WaterSampleSite | null>(null);
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
      API.protocolChecklist.getWaterSampleSite(id),
      API.protocolChecklist.getChecklist('wat', id).then(
        (c) => c.statusCode,
        () => undefined,
      ),
    ])
      .then(([site, statusCode]) => {
        if (cancelled) return;
        setCurrent(site);
        setStatus(statusCode);
      })
      .catch((err: unknown) => {
        if (!cancelled) reportError("We couldn't load the sample site", err);
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
    setCurrent((prev) => (prev ? ({ ...prev, [key]: value } as WaterSampleSite) : prev));

  const handleSave = async () => {
    if (!current) return;
    setBusy(true);
    try {
      const saved = await API.protocolChecklist.saveWaterSampleSite(id, current);
      setCurrent(saved);
      display({ kind: 'success', title: 'Sample site saved', timeout: 4000 });
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
        <h1>Water sample site — checklist {id}</h1>
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
          <p>No sample site found.</p>
        ) : (
          <Tile>
            <Stack gap={6}>
              <fieldset className="chr-checklist__fieldset">
                <legend>Sample site</legend>
                {FIELDS.map((f) => (
                  <TextInput
                    key={f.key}
                    id={`wss-${f.key}`}
                    labelText={f.label}
                    value={get(f.key)}
                    disabled={readOnly}
                    onChange={(e) => set(f.key, e.target.value)}
                  />
                ))}
              </fieldset>
              <div className="protocol-checklist__actions">
                {!readOnly && (
                  <Button onClick={() => void handleSave()} disabled={busy}>
                    Save sample site
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

export default WaterSampleSiteEditPage;

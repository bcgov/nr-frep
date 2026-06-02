import {
  Button,
  Checkbox,
  Column,
  Grid,
  InlineNotification,
  SkeletonText,
  Stack,
  Tile,
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import type { WaterAssessment, WtrAssessmentRow } from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import API from '@/services/APIs';

import './protocolChecklist.scss';

const WaterAssessmentEditPage: FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { display } = useNotification();
  const { canEdit } = useAuthorization();

  const [siteId, setSiteId] = useState<string>('');
  const [current, setCurrent] = useState<WaterAssessment | null>(null);
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
    (async () => {
      const [site, statusCode] = await Promise.all([
        API.protocolChecklist.getWaterSampleSite(id),
        API.protocolChecklist.getChecklist('wat', id).then(
          (c) => c.statusCode,
          () => undefined,
        ),
      ]);
      const sampleSiteId = site.waterSampleSiteId ?? '';
      const assessment = sampleSiteId
        ? await API.protocolChecklist.getWaterAssessment(sampleSiteId)
        : { waterSampleSiteId: '', conditions: [], solutions: [] };
      if (cancelled) return;
      setSiteId(sampleSiteId);
      setCurrent(assessment);
      setStatus(statusCode);
    })()
      .catch((err: unknown) => {
        if (!cancelled) reportError("We couldn't load the assessment", err);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [id, reportError]);

  const readOnly = !canEdit || status === 'SUB';

  const toggle = (listKey: 'conditions' | 'solutions', index: number, checked: boolean) =>
    setCurrent((prev) =>
      prev
        ? {
            ...prev,
            [listKey]: (prev[listKey] ?? []).map((r, i) =>
              i === index ? { ...r, assessmentInd: checked ? 'Y' : 'N' } : r,
            ),
          }
        : prev,
    );

  const handleSave = async () => {
    if (!current || !siteId) return;
    setBusy(true);
    try {
      const saved = await API.protocolChecklist.saveWaterAssessment(siteId, current);
      setCurrent(saved);
      display({ kind: 'success', title: 'Assessment saved', timeout: 4000 });
    } catch (err) {
      reportError('Save failed', err);
    } finally {
      setBusy(false);
    }
  };

  const renderList = (
    listKey: 'conditions' | 'solutions',
    legend: string,
    rows: WtrAssessmentRow[],
  ) => (
    <fieldset className="chr-checklist__fieldset">
      <legend>{legend}</legend>
      {rows.length === 0 && <p>None.</p>}
      {rows.map((r, index) => (
        <Checkbox
          key={`${listKey}-${index}`}
          id={`${listKey}-${index}`}
          labelText={r.assessmentDesc || r.assessmentType || `Row ${index + 1}`}
          checked={r.assessmentInd === 'Y'}
          disabled={readOnly}
          onChange={(_e, { checked }) => toggle(listKey, index, checked)}
        />
      ))}
    </fieldset>
  );

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
        <h1>Water assessment — checklist {id}</h1>
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
        {!current || !siteId ? (
          <p>No sample site to assess.</p>
        ) : (
          <Tile>
            <Stack gap={6}>
              {renderList('conditions', 'Observed conditions', current.conditions ?? [])}
              {renderList('solutions', 'Solutions', current.solutions ?? [])}
              <div className="protocol-checklist__actions">
                {!readOnly && (
                  <Button onClick={() => void handleSave()} disabled={busy}>
                    Save assessment
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

export default WaterAssessmentEditPage;

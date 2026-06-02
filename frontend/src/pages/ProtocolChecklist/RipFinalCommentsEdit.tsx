import {
  Button,
  Column,
  Grid,
  InlineNotification,
  SkeletonText,
  Stack,
  TextArea,
  Tile,
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import type { RiparianFinalComments } from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import API from '@/services/APIs';

import './protocolChecklist.scss';

const FIELDS: { key: keyof RiparianFinalComments; label: string }[] = [
  { key: 'conclusionComment', label: 'Conclusion' },
  { key: 'specificImpactComment', label: 'Specific impact' },
  { key: 'assessmentProblemsComment', label: 'Assessment problems' },
  { key: 'mapLegibilityComment', label: 'Map legibility' },
  { key: 'leaveStripAssessmentComment', label: 'Leave-strip assessment' },
  { key: 'checklistRecommComment', label: 'Checklist recommendation' },
];

const RipFinalCommentsEditPage: FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { display } = useNotification();
  const { canEdit } = useAuthorization();

  const [current, setCurrent] = useState<RiparianFinalComments | null>(null);
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
      API.protocolChecklist.getRipFinalComments(id),
      API.protocolChecklist.getChecklist('rip', id).then(
        (c) => c.statusCode,
        () => undefined,
      ),
    ])
      .then(([comments, statusCode]) => {
        if (cancelled) return;
        setCurrent(comments);
        setStatus(statusCode);
      })
      .catch((err: unknown) => {
        if (!cancelled) reportError("We couldn't load the final comments", err);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [id, reportError]);

  const readOnly = !canEdit || status === 'SUB';

  const patch = (p: Partial<RiparianFinalComments>) =>
    setCurrent((prev) => (prev ? { ...prev, ...p } : prev));

  const handleSave = async () => {
    if (!current) return;
    setBusy(true);
    try {
      const saved = await API.protocolChecklist.saveRipFinalComments(id, current);
      setCurrent(saved);
      display({ kind: 'success', title: 'Final comments saved', timeout: 4000 });
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
          <SkeletonText paragraph lineCount={6} />
        </Column>
      </Grid>
    );
  }

  return (
    <Grid fullWidth className="default-grid">
      <Column sm={4} md={8} lg={16}>
        <h1>Riparian final comments — checklist {id}</h1>
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
          <p>No final comments found.</p>
        ) : (
          <Tile>
            <Stack gap={6}>
              {FIELDS.map((f) => (
                <TextArea
                  key={f.key}
                  id={`rip-${f.key}`}
                  labelText={f.label}
                  value={(current[f.key] as string | undefined) ?? ''}
                  disabled={readOnly}
                  onChange={(e) => patch({ [f.key]: e.target.value })}
                />
              ))}
              <div className="protocol-checklist__actions">
                {!readOnly && (
                  <Button onClick={() => void handleSave()} disabled={busy}>
                    Save final comments
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

export default RipFinalCommentsEditPage;

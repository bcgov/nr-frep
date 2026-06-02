import {
  Button,
  Checkbox,
  Column,
  Grid,
  InlineNotification,
  SkeletonText,
  Stack,
  TextArea,
  TextInput,
  Tile,
} from '@carbon/react';
import { useEffect, useState, type FC } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import type { BiodiversityOpening } from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import API from '@/services/APIs';

const yn = (checked: boolean): string => (checked ? 'Y' : 'N');

/** Edit form for the Biodiversity Opening (FREP screen 210) — typed, calls FREP_210_BIO_OPENING.SAVE. */
const BioOpeningEditPage: FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { display } = useNotification();
  const { canEdit } = useAuthorization();

  const [opening, setOpening] = useState<BiodiversityOpening | null>(null);
  const [loading, setLoading] = useState(true);
  const [hasError, setHasError] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setHasError(false);
    API.protocolChecklist
      .getBiodiversityOpening(id)
      .then((data) => {
        if (!cancelled) setOpening(data);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        display({
          kind: 'error',
          title: "We couldn't load the opening",
          subtitle: err instanceof Error ? err.message : 'Unknown error',
          timeout: 9000,
        });
        setHasError(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [id, display]);

  const patch = (p: Partial<BiodiversityOpening>) =>
    setOpening((prev) => (prev ? { ...prev, ...p } : prev));

  const handleSave = async () => {
    if (!opening) return;
    setSaving(true);
    try {
      const saved = await API.protocolChecklist.saveBiodiversityOpening(id, opening);
      setOpening(saved);
      display({ kind: 'success', title: 'Opening saved', timeout: 4000 });
    } catch (err) {
      display({
        kind: 'error',
        title: 'Save failed',
        subtitle:
          err instanceof Error ? err.message : 'The checklist may have changed — reload and retry.',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
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

  if (hasError || !opening) {
    return (
      <Grid fullWidth className="default-grid">
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="error"
            title="Unable to load opening"
            hideCloseButton
            lowContrast
          />
        </Column>
      </Grid>
    );
  }

  const readOnly = !canEdit || opening.statusCode === 'SUB';

  return (
    <Grid fullWidth className="default-grid">
      <Column sm={4} md={8} lg={16}>
        <h1>Biodiversity opening — checklist {id}</h1>
      </Column>
      {readOnly && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="info"
            title={opening.statusCode === 'SUB' ? 'Submitted — read only' : 'View only'}
            hideCloseButton
            lowContrast
          />
        </Column>
      )}
      <Column sm={4} md={8} lg={16}>
        <Tile>
          <Stack gap={5}>
            <TextArea
              id="bio-location"
              labelText="Location description"
              value={opening.locationDescription ?? ''}
              disabled={readOnly}
              onChange={(e) => patch({ locationDescription: e.target.value })}
            />
            <Checkbox
              id="bio-patch-on-block"
              labelText="Patch reserves on block"
              checked={opening.patchReservesOnBlock === 'Y'}
              disabled={readOnly}
              onChange={(_e, { checked }) => patch({ patchReservesOnBlock: yn(checked) })}
            />
            <Checkbox
              id="bio-patch-sampled"
              labelText="Patch reserves sampled"
              checked={opening.patchReservesSampled === 'Y'}
              disabled={readOnly}
              onChange={(_e, { checked }) => patch({ patchReservesSampled: yn(checked) })}
            />
            <Checkbox
              id="bio-innovative"
              labelText="Innovative practice"
              checked={opening.innovativePracticeInd === 'Y'}
              disabled={readOnly}
              onChange={(_e, { checked }) => patch({ innovativePracticeInd: yn(checked) })}
            />
            <TextArea
              id="bio-innovative-comment"
              labelText="Innovative practice comment"
              value={opening.innovativePracticesComment ?? ''}
              disabled={readOnly}
              onChange={(e) => patch({ innovativePracticesComment: e.target.value })}
            />
            <Checkbox
              id="bio-invasive"
              labelText="Invasive plants present"
              checked={opening.invasivePlantIndicator === 'Y'}
              disabled={readOnly}
              onChange={(_e, { checked }) => patch({ invasivePlantIndicator: yn(checked) })}
            />
            <TextArea
              id="bio-invasive-comment"
              labelText="Invasive plant comment"
              value={opening.invasivePlantComment ?? ''}
              disabled={readOnly}
              onChange={(e) => patch({ invasivePlantComment: e.target.value })}
            />
            <TextInput
              id="bio-rating"
              labelText="Site evaluation rating code"
              value={opening.frepSiteEvaluationCode ?? ''}
              disabled={readOnly}
              onChange={(e) => patch({ frepSiteEvaluationCode: e.target.value })}
            />
            <TextArea
              id="bio-opinion"
              labelText="Evaluator opinion comment"
              value={opening.evaluatorOpinionComment ?? ''}
              disabled={readOnly}
              onChange={(e) => patch({ evaluatorOpinionComment: e.target.value })}
            />
            <div className="protocol-checklist__actions">
              {!readOnly && (
                <Button onClick={() => void handleSave()} disabled={saving}>
                  Save
                </Button>
              )}
              <Button kind="ghost" onClick={() => navigate(-1)}>
                Back
              </Button>
            </div>
          </Stack>
        </Tile>
      </Column>
    </Grid>
  );
};

export default BioOpeningEditPage;

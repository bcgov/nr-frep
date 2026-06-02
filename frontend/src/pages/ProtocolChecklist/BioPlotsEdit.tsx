import { Add, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
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

import type { BioCwdRow, BioPlot, BioPlotRow, BioStandRow } from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import API from '@/services/APIs';

import './protocolChecklist.scss';

type FieldDef = { key: string; label: string };

const PLOT_FIELDS: FieldDef[] = [
  { key: 'plotNumber', label: 'Plot number' },
  { key: 'assessorName', label: 'Assessor name' },
  { key: 'utmSignal', label: 'UTM signal' },
  { key: 'utmZone', label: 'UTM zone' },
  { key: 'utmEasting', label: 'UTM easting' },
  { key: 'utmNorthing', label: 'UTM northing' },
  { key: 'basalAreaFactor', label: 'Basal area factor' },
  { key: 'fixedAreaRadius', label: 'Fixed area radius' },
  { key: 'fullCountArea', label: 'Full count area' },
  { key: 'firstLegTransect', label: 'First leg transect' },
  { key: 'secondLegTransect', label: 'Second leg transect' },
  { key: 'plotComment', label: 'Plot comment' },
];

const PLOT_INDS: FieldDef[] = [
  { key: 'treeIndicator', label: 'Tree plot' },
  { key: 'cwdTransectIndicator', label: 'CWD transect' },
];

const STAND_FIELDS: FieldDef[] = [
  { key: 'speciesCode', label: 'Species code' },
  { key: 'treeNumber', label: 'Tree #' },
  { key: 'dbh', label: 'DBH' },
  { key: 'height', label: 'Height' },
  { key: 'decayClassCode', label: 'Decay class' },
  { key: 'comments', label: 'Comments' },
];

const CWD_FIELDS: FieldDef[] = [
  { key: 'speciesCode', label: 'Species code' },
  { key: 'logNumber', label: 'Log #' },
  { key: 'logDiameter', label: 'Log diameter' },
  { key: 'logLength', label: 'Log length' },
  { key: 'decayClassCode', label: 'Decay class' },
  { key: 'comments', label: 'Comments' },
];

const BioPlotsEditPage: FC = () => {
  const { id = '', stratumId = '' } = useParams<{ id: string; stratumId: string }>();
  const navigate = useNavigate();
  const { display } = useNotification();
  const { canEdit } = useAuthorization();

  const [rows, setRows] = useState<BioPlotRow[]>([]);
  const [current, setCurrent] = useState<BioPlot | null>(null);
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

  const loadList = useCallback(async () => {
    const list = await API.protocolChecklist.listBioPlots(stratumId);
    setRows(list);
    return list;
  }, [stratumId]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([
      API.protocolChecklist.listBioPlots(stratumId),
      API.protocolChecklist.getChecklist('bio', id).then(
        (c) => c.statusCode,
        () => undefined,
      ),
    ])
      .then(([list, statusCode]) => {
        if (cancelled) return;
        setRows(list);
        setStatus(statusCode);
      })
      .catch((err: unknown) => {
        if (!cancelled) reportError("We couldn't load the plots", err);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [id, stratumId, reportError]);

  const readOnly = !canEdit || status === 'SUB';

  const get = (key: string): string =>
    ((current as Record<string, unknown>)?.[key] as string | undefined) ?? '';
  const set = (key: string, value: string) =>
    setCurrent((prev) => (prev ? ({ ...prev, [key]: value } as BioPlot) : prev));

  const select = async (plotId: string) => {
    setBusy(true);
    try {
      setCurrent(await API.protocolChecklist.getBioPlot(plotId));
    } catch (err) {
      reportError('Could not load the plot', err);
    } finally {
      setBusy(false);
    }
  };

  const addPlot = () =>
    setCurrent({
      stratumId,
      treeIndicator: 'N',
      cwdTransectIndicator: 'N',
      standTable: [],
      cwdTable: [],
    });

  const handleSave = async () => {
    if (!current) return;
    setBusy(true);
    try {
      const saved = await API.protocolChecklist.saveBioPlot(stratumId, current);
      setCurrent(saved);
      await loadList();
      display({ kind: 'success', title: 'Plot saved', timeout: 4000 });
    } catch (err) {
      reportError('Save failed', err);
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async () => {
    if (!current?.plotId) {
      setCurrent(null);
      return;
    }
    setBusy(true);
    try {
      await API.protocolChecklist.deleteBioPlot(current.plotId, current.revisionCount ?? '');
      setCurrent(null);
      await loadList();
      display({ kind: 'success', title: 'Plot deleted', timeout: 4000 });
    } catch (err) {
      reportError('Delete failed', err);
    } finally {
      setBusy(false);
    }
  };

  // --- stand-table sub-collection ---
  const setStand = (index: number, patch: Partial<BioStandRow>) =>
    setCurrent((prev) =>
      prev
        ? {
            ...prev,
            standTable: (prev.standTable ?? []).map((r, i) =>
              i === index ? { ...r, ...patch } : r,
            ),
          }
        : prev,
    );
  const addStand = () =>
    setCurrent((prev) => (prev ? { ...prev, standTable: [...(prev.standTable ?? []), {}] } : prev));
  const removeStand = (index: number) =>
    setCurrent((prev) =>
      prev ? { ...prev, standTable: (prev.standTable ?? []).filter((_, i) => i !== index) } : prev,
    );

  // --- CWD sub-collection ---
  const setCwd = (index: number, patch: Partial<BioCwdRow>) =>
    setCurrent((prev) =>
      prev
        ? {
            ...prev,
            cwdTable: (prev.cwdTable ?? []).map((r, i) => (i === index ? { ...r, ...patch } : r)),
          }
        : prev,
    );
  const addCwd = () =>
    setCurrent((prev) => (prev ? { ...prev, cwdTable: [...(prev.cwdTable ?? []), {}] } : prev));
  const removeCwd = (index: number) =>
    setCurrent((prev) =>
      prev ? { ...prev, cwdTable: (prev.cwdTable ?? []).filter((_, i) => i !== index) } : prev,
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
        <h1>Biodiversity plots — stratum {stratumId}</h1>
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

      <Column sm={4} md={2} lg={4}>
        <div className="chr-checklist__feature-list">
          {rows.length === 0 && <p>No plots yet.</p>}
          {rows.map((row) => (
            <Button
              key={row.plotId}
              kind={current?.plotId === row.plotId ? 'primary' : 'ghost'}
              size="sm"
              disabled={busy}
              onClick={() => void select(row.plotId ?? '')}
            >
              {row.plotNumber || row.plotId}
            </Button>
          ))}
          {!readOnly && (
            <Button kind="tertiary" size="sm" renderIcon={Add} disabled={busy} onClick={addPlot}>
              Add plot
            </Button>
          )}
        </div>
      </Column>

      <Column sm={4} md={6} lg={12}>
        {!current ? (
          <p>Select or add a plot to edit.</p>
        ) : (
          <Tile>
            <Stack gap={6}>
              <fieldset className="chr-checklist__fieldset">
                <legend>Plot</legend>
                {PLOT_FIELDS.map((f) => (
                  <TextInput
                    key={f.key}
                    id={`plot-${f.key}`}
                    labelText={f.label}
                    value={get(f.key)}
                    disabled={readOnly}
                    onChange={(e) => set(f.key, e.target.value)}
                  />
                ))}
                {PLOT_INDS.map((f) => (
                  <Checkbox
                    key={f.key}
                    id={`plot-${f.key}`}
                    labelText={f.label}
                    checked={get(f.key) === 'Y'}
                    disabled={readOnly}
                    onChange={(_e, { checked }) => set(f.key, checked ? 'Y' : 'N')}
                  />
                ))}
              </fieldset>

              <fieldset className="chr-checklist__fieldset">
                <legend>Stand table (trees)</legend>
                {(current.standTable ?? []).map((r, index) => (
                  <div key={r.standId ?? `stand-${index}`} className="chr-checklist__form">
                    {STAND_FIELDS.map((f) => (
                      <TextInput
                        key={f.key}
                        id={`stand-${index}-${f.key}`}
                        labelText={f.label}
                        value={(r[f.key as keyof BioStandRow] as string | undefined) ?? ''}
                        disabled={readOnly}
                        onChange={(e) => setStand(index, { [f.key]: e.target.value })}
                      />
                    ))}
                    {!readOnly && (
                      <Button
                        kind="danger--tertiary"
                        size="sm"
                        renderIcon={TrashCan}
                        onClick={() => removeStand(index)}
                      >
                        Remove
                      </Button>
                    )}
                  </div>
                ))}
                {!readOnly && (
                  <Button kind="ghost" size="sm" renderIcon={Add} onClick={addStand}>
                    Add tree
                  </Button>
                )}
              </fieldset>

              <fieldset className="chr-checklist__fieldset">
                <legend>Coarse woody debris</legend>
                {(current.cwdTable ?? []).map((r, index) => (
                  <div key={r.cwdId ?? `cwd-${index}`} className="chr-checklist__form">
                    {CWD_FIELDS.map((f) => (
                      <TextInput
                        key={f.key}
                        id={`cwd-${index}-${f.key}`}
                        labelText={f.label}
                        value={(r[f.key as keyof BioCwdRow] as string | undefined) ?? ''}
                        disabled={readOnly}
                        onChange={(e) => setCwd(index, { [f.key]: e.target.value })}
                      />
                    ))}
                    {!readOnly && (
                      <Button
                        kind="danger--tertiary"
                        size="sm"
                        renderIcon={TrashCan}
                        onClick={() => removeCwd(index)}
                      >
                        Remove
                      </Button>
                    )}
                  </div>
                ))}
                {!readOnly && (
                  <Button kind="ghost" size="sm" renderIcon={Add} onClick={addCwd}>
                    Add log
                  </Button>
                )}
              </fieldset>

              <div className="protocol-checklist__actions">
                {!readOnly && (
                  <Button onClick={() => void handleSave()} disabled={busy}>
                    Save plot
                  </Button>
                )}
                {!readOnly && current.plotId && (
                  <Button
                    kind="danger--tertiary"
                    onClick={() => void handleDelete()}
                    disabled={busy}
                  >
                    Delete plot
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

export default BioPlotsEditPage;

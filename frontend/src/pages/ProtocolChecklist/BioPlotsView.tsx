import { Add, TrashCan } from '@carbon/icons-react';
import { Button, Checkbox, Select, SelectItem, SkeletonText, TextInput } from '@carbon/react';
import { useCallback, useEffect, useState, type FC, type ReactNode } from 'react';

import type {
  BioCwdRow,
  BioPlot,
  BioPlotRow,
  BioStandRow,
  BioStratumRow,
} from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

/**
 * Biodiversity Plots section (FREP212) — edited inline in place (no separate page). Plots are
 * stratum-scoped, so a Stratum selector filters the plot rail (mirroring the legacy FREP212
 * stratum dropdown). Pick (or add) a plot, then edit its scalar fields, the stand (tree) table,
 * and the coarse-woody-debris table. Save round-trips the full DTO + revision count.
 */

type Props = {
  checklistId: string;
  canEdit: boolean;
  submitted: boolean;
};

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

const BioPlotsView: FC<Props> = ({ checklistId, canEdit, submitted }) => {
  const { display } = useNotification();
  const [strata, setStrata] = useState<BioStratumRow[]>([]);
  const [stratumId, setStratumId] = useState('');
  const [rows, setRows] = useState<BioPlotRow[]>([]);
  const [current, setCurrent] = useState<BioPlot | null>(null);
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

  // Load the strata list once; default the selector to the first stratum.
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    API.protocolChecklist
      .listBioStrata(checklistId)
      .then((list) => {
        if (cancelled) return;
        setStrata(list);
        setStratumId((prev) => prev || list[0]?.stratumId || '');
      })
      .catch((err: unknown) => {
        if (!cancelled) reportError("We couldn't load the strata", err);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [checklistId, reportError]);

  const loadPlots = useCallback(async () => {
    const list = await API.protocolChecklist.listBioPlots(stratumId);
    setRows(list);
    return list;
  }, [stratumId]);

  // Reload the plot rail whenever the selected stratum changes. (No stratum selected yet → nothing
  // to load; `rows` already starts empty, so we avoid a synchronous setState here.)
  useEffect(() => {
    if (!stratumId) return;
    let cancelled = false;
    API.protocolChecklist
      .listBioPlots(stratumId)
      .then((list) => {
        if (!cancelled) setRows(list);
      })
      .catch((err: unknown) => {
        if (!cancelled) reportError("We couldn't load the plots", err);
      });
    return () => {
      cancelled = true;
    };
  }, [stratumId, reportError]);

  const readOnly = !canEdit || submitted;

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
      await loadPlots();
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
      await loadPlots();
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

  const plotField = (key: string, label: string): ReactNode =>
    readOnly ? (
      <div className="protocol-checklist__field" key={key}>
        <span className="protocol-checklist__label">{label}</span>
        <span className="protocol-checklist__value">{get(key) || '—'}</span>
      </div>
    ) : (
      <TextInput
        key={key}
        id={`plot-${key}`}
        labelText={label}
        value={get(key)}
        onChange={(e) => set(key, e.target.value)}
      />
    );

  // Renders one child-collection grid (stand or CWD). Rows are accessed by string key; the change
  // callback receives (index, key, value) so each caller keeps its own typed mutator.
  const childGrid = (
    caption: string,
    fields: FieldDef[],
    items: Array<Record<string, string | undefined>>,
    keyOf: (index: number) => string,
    onChange: (index: number, key: string, value: string) => void,
    removeRow: (index: number) => void,
    addRow: () => void,
    addLabel: string,
  ): ReactNode => (
    <fieldset className="rip-form__group">
      <legend>{caption}</legend>
      <table className="rip-field-grid">
        <thead>
          <tr>
            {fields.map((f) => (
              <th scope="col" key={f.key}>
                {f.label}
              </th>
            ))}
            {!readOnly && <th aria-label="Actions" />}
          </tr>
        </thead>
        <tbody>
          {items.length === 0 && (
            <tr>
              <td colSpan={fields.length + (readOnly ? 0 : 1)}>None.</td>
            </tr>
          )}
          {items.map((row, index) => (
            <tr key={keyOf(index)}>
              {fields.map((f) => (
                <td key={f.key}>
                  {readOnly ? (
                    (row[f.key] ?? '—')
                  ) : (
                    <TextInput
                      id={`${caption}-${index}-${f.key}`}
                      labelText={f.label}
                      hideLabel
                      size="sm"
                      value={row[f.key] ?? ''}
                      onChange={(e) => onChange(index, f.key, e.target.value)}
                    />
                  )}
                </td>
              ))}
              {!readOnly && (
                <td>
                  <Button
                    kind="danger--tertiary"
                    size="sm"
                    hasIconOnly
                    renderIcon={TrashCan}
                    iconDescription="Remove row"
                    onClick={() => removeRow(index)}
                  />
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
      {!readOnly && (
        <Button kind="ghost" size="sm" renderIcon={Add} onClick={addRow}>
          {addLabel}
        </Button>
      )}
    </fieldset>
  );

  if (loading) {
    return <SkeletonText paragraph lineCount={8} />;
  }

  if (strata.length === 0) {
    return <p>Add a stratum on the Stratum summary tab before adding plots.</p>;
  }

  return (
    <div className="rip-form">
      <div className="rip-form__grid">
        <Select
          id="plots-stratum"
          labelText="Stratum"
          value={stratumId}
          onChange={(e) => {
            // Switching stratum clears the open plot so the rail + detail reload cleanly.
            setStratumId(e.target.value);
            setCurrent(null);
          }}
        >
          {strata.map((s) => (
            <SelectItem
              key={s.stratumId}
              value={s.stratumId ?? ''}
              text={s.stratumNumber || s.stratumId || ''}
            />
          ))}
        </Select>
      </div>

      <div className="bio-master">
        <div className="bio-master__list">
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

        <div className="bio-master__detail">
          {!current ? (
            <p>Select or add a plot to edit.</p>
          ) : (
            <>
              <fieldset className="rip-form__group">
                <legend>Plot</legend>
                <div className="rip-form__grid">
                  {PLOT_FIELDS.map((f) => plotField(f.key, f.label))}
                  {PLOT_INDS.map((f) =>
                    readOnly ? (
                      <div className="protocol-checklist__field" key={f.key}>
                        <span className="protocol-checklist__label">{f.label}</span>
                        <span className="protocol-checklist__value">
                          {get(f.key) === 'Y' ? 'Yes' : 'No'}
                        </span>
                      </div>
                    ) : (
                      <Checkbox
                        key={f.key}
                        id={`plot-${f.key}`}
                        labelText={f.label}
                        checked={get(f.key) === 'Y'}
                        onChange={(_e, { checked }) => set(f.key, checked ? 'Y' : 'N')}
                      />
                    ),
                  )}
                </div>
              </fieldset>

              {childGrid(
                'Stand table (trees)',
                STAND_FIELDS,
                (current.standTable ?? []) as Array<Record<string, string | undefined>>,
                (i) => current.standTable?.[i]?.standId ?? `stand-${i}`,
                (index, key, value) => setStand(index, { [key]: value }),
                removeStand,
                addStand,
                'Add tree',
              )}

              {childGrid(
                'Coarse woody debris',
                CWD_FIELDS,
                (current.cwdTable ?? []) as Array<Record<string, string | undefined>>,
                (i) => current.cwdTable?.[i]?.cwdId ?? `cwd-${i}`,
                (index, key, value) => setCwd(index, { [key]: value }),
                removeCwd,
                addCwd,
                'Add log',
              )}

              {!readOnly && (
                <div className="protocol-checklist__actions">
                  <Button disabled={busy} onClick={() => void handleSave()}>
                    Save plot
                  </Button>
                  {current.plotId && (
                    <Button
                      kind="danger--tertiary"
                      disabled={busy}
                      onClick={() => void handleDelete()}
                    >
                      Delete plot
                    </Button>
                  )}
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default BioPlotsView;

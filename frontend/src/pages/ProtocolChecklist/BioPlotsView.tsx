import { Add, Edit, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  InlineNotification,
  Select,
  SelectItem,
  SkeletonText,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC, type ReactNode } from 'react';

import type { CodeOption } from '@/types/configuration';
import type {
  BioCwdRow,
  BioPlot,
  BioPlotRow,
  BioStandRow,
  BioStratumRow,
} from '@/types/protocolChecklist';

import { useConfirm } from '@/context/confirm/useConfirm';
import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

/**
 * Biodiversity Plots section (FREP212) — edited inline. Plots are stratum-scoped, so a Stratum
 * selector drives the plot table; pick (or add) a plot, then edit its scalar fields, the stand
 * (tree) table, and the coarse-woody-debris table. Mirrors the legacy frep212BIOPlots.jsp:
 * coded dropdowns (species / WT-class / CWD-decay / evaluator / UTM zone), the "No UTM signal"
 * checkbox that disables the UTM fields, conditional stand/CWD tables, easting/northing digit
 * checks, and the stratum type + "# plots completed of expected" read-out.
 */

type Props = {
  checklistId: string;
  canEdit: boolean;
  submitted: boolean;
  /** True when the Plots tab is the active tab — triggers a strata refetch (see the effect). */
  active?: boolean;
};

// Sub-table columns. `kind` picks the cell control: index = read-only row number,
// select-* = coded dropdown, otherwise a (length-capped) text input.
type Col = {
  key: string;
  label: string;
  kind?: 'index' | 'select-spp' | 'select-wt' | 'select-cwd';
  maxLength?: number;
};

// Legacy column order: Tree# | Spp. | WT Class | DBH (cm) | Ht (m) | Comments.
const STAND_COLS: Col[] = [
  { key: 'treeNumber', label: 'Tree #', kind: 'index' },
  { key: 'speciesCode', label: 'Spp.', kind: 'select-spp' },
  { key: 'decayClassCode', label: 'WT Class', kind: 'select-wt' },
  { key: 'dbh', label: 'DBH (cm)', maxLength: 5 },
  { key: 'height', label: 'Ht (m)', maxLength: 4 },
  { key: 'comments', label: 'Comments', maxLength: 50 },
];

// Legacy column order: Log# | Spp. | Decay Class | Dia. (cm) | Length (m) | Comments.
const CWD_COLS: Col[] = [
  { key: 'logNumber', label: 'Log #', kind: 'index' },
  { key: 'speciesCode', label: 'Spp.', kind: 'select-spp' },
  { key: 'decayClassCode', label: 'Decay Class', kind: 'select-cwd' },
  { key: 'logDiameter', label: 'Dia. (cm)', maxLength: 5 },
  { key: 'logLength', label: 'Length (m)', maxLength: 4 },
  { key: 'comments', label: 'Comments', maxLength: 50 },
];

const UTM_ZONE_OPTIONS: CodeOption[] = ['7', '8', '9', '10', '11'].map((z) => ({
  code: z,
  description: z,
}));

const TABLE_MAX = 100;
const TABLE_WARN = 50;

const BioPlotsView: FC<Props> = ({ checklistId, canEdit, submitted, active }) => {
  const { display } = useNotification();
  const confirm = useConfirm();
  const [strata, setStrata] = useState<BioStratumRow[]>([]);
  const [stratumId, setStratumId] = useState('');
  const [rows, setRows] = useState<BioPlotRow[]>([]);
  const [current, setCurrent] = useState<BioPlot | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  // Set once a save is attempted on an invalid plot, so required-field highlights only appear after
  // the user tries to save (not the moment the form opens). Reset whenever a fresh plot form opens.
  const [attemptedSave, setAttemptedSave] = useState(false);

  // Reference data for the coded dropdowns. `evaluators` is null until loaded so we can show the
  // "no evaluator yet" notice only after the lookup resolves.
  const [species, setSpecies] = useState<CodeOption[]>([]);
  const [wtDecay, setWtDecay] = useState<CodeOption[]>([]);
  const [cwdDecay, setCwdDecay] = useState<CodeOption[]>([]);
  const [strataTypes, setStrataTypes] = useState<CodeOption[]>([]);
  const [evaluators, setEvaluators] = useState<CodeOption[] | null>(null);

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
    // Keyed on checklistId only — reportError is recreated each render and would re-run
    // (and cancel) the load before it settles.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [checklistId]);

  // Carbon keeps the Stratum tab mounted alongside this one, so a stratum added there won't appear
  // in our once-loaded list. Refetch the strata whenever this tab becomes active so newly-added
  // strata show up without a full page reload. (Refetches only the lightweight list; keeps the
  // current selection and any open plot.)
  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    API.protocolChecklist
      .listBioStrata(checklistId)
      .then((list) => {
        if (cancelled) return;
        setStrata(list);
        setStratumId((prev) => prev || list[0]?.stratumId || '');
      })
      .catch((err: unknown) => {
        if (!cancelled) reportError("We couldn't load the strata", err);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active, checklistId]);

  // Reference data (coded dropdowns + the checklist's evaluator list).
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      API.configuration.getSpecies(),
      API.configuration.getWildlifeTreeDecay(),
      API.configuration.getCwdDecay(),
      API.configuration.getStrataTypes(),
      API.configuration.getEvaluators(checklistId),
    ])
      .then(([sp, wt, cwd, st, ev]) => {
        if (cancelled) return;
        setSpecies(sp);
        setWtDecay(wt);
        setCwdDecay(cwd);
        setStrataTypes(st);
        setEvaluators(ev);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setEvaluators([]);
        reportError("We couldn't load the plot reference data", err);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [checklistId]);

  const loadPlots = useCallback(async () => {
    const list = await API.protocolChecklist.listBioPlots(stratumId);
    setRows(list);
    return list;
  }, [stratumId]);

  // Reload the plot table whenever the selected stratum changes.
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
    // Keyed on stratumId only — see the strata-load effect above.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stratumId]);

  const readOnly = !canEdit || submitted;

  const get = (key: string): string =>
    ((current as Record<string, unknown>)?.[key] as string | undefined) ?? '';
  const set = (key: string, value: string) =>
    setCurrent((prev) => (prev ? ({ ...prev, [key]: value } as BioPlot) : prev));

  // "No UTM Signal Available" is the legacy checkbox: checked ⇔ utm_signal = 'N', which disables
  // the UTM zone/easting/northing fields.
  const noUtmSignal = get('utmSignal') === 'N';

  // --- computed stratum read-out (legacy "Stratum Identification" block) ---
  const selectedStratum = strata.find((s) => s.stratumId === stratumId);
  const stratumTypeLabel =
    strataTypes.find((t) => t.code === selectedStratum?.strataTypeCode)?.description ??
    selectedStratum?.strataTypeCode ??
    '';
  const plotsExpected = selectedStratum?.plotCount ?? '';
  const plotsCompleted = String(rows.length);

  const select = async (plotId: string) => {
    setBusy(true);
    try {
      setAttemptedSave(false);
      setCurrent(await API.protocolChecklist.getBioPlot(plotId));
    } catch (err) {
      reportError('Could not load the plot', err);
    } finally {
      setBusy(false);
    }
  };

  const addPlot = () => {
    setAttemptedSave(false);
    setCurrent({
      stratumId,
      treeIndicator: 'N',
      cwdTransectIndicator: 'N',
      standTable: [],
      cwdTable: [],
    });
  };

  // Per-field validation message ('' = valid). Single source of truth shared by validate() (which
  // blocks the save) and the inline field controls, so the toast and the field highlights stay in
  // sync. UTM zone/easting/northing are required + length-checked only when a signal is available
  // ("No UTM signal available" unchecked); when checked, those fields are disabled and exempt.
  const plotFieldError = (key: string): string => {
    const value = get(key).trim();
    switch (key) {
      case 'utmZone':
        return noUtmSignal || value !== '' ? '' : 'Zone is required.';
      case 'utmEasting':
        if (noUtmSignal) return '';
        if (value === '') return 'Easting is required.';
        return /^\d{6}$/.test(value) ? '' : 'Easting must be exactly 6 digits.';
      case 'utmNorthing':
        if (noUtmSignal) return '';
        if (value === '') return 'Northing is required.';
        return /^\d{7}$/.test(value) ? '' : 'Northing must be exactly 7 digits.';
      case 'firstLegTransect':
        return value === '' ? 'Bearing 1st leg is required.' : '';
      case 'secondLegTransect':
        return value === '' ? '2nd leg is required.' : '';
      default:
        return '';
    }
  };

  const validate = (): string[] => {
    // Bearing legs are always required; UTM is required only when a signal is available (legacy
    // submit checks frep.submit.biodiversity.plot.nobearingleg / .utmrequired). Enforce here so they
    // can't reach submit.
    const errs = ['utmZone', 'utmEasting', 'utmNorthing', 'firstLegTransect', 'secondLegTransect']
      .map(plotFieldError)
      .filter((e) => e !== '');
    // When "Trees exist" is checked, the stand table must have at least one row (legacy submit check
    // frep.submit.biodiversity.plot.notrees) — otherwise the indicator and the table disagree.
    if (get('treeIndicator') === 'Y' && (current?.standTable?.length ?? 0) === 0) {
      errs.push('"Trees exist" is checked — add at least one stand-table row, or uncheck it.');
    }
    return errs;
  };

  const handleSave = async () => {
    if (!current) return;
    const errs = validate();
    if (errs.length > 0) {
      setAttemptedSave(true);
      reportError('Please fix the following', new Error(errs.join(' ')));
      return;
    }
    setBusy(true);
    try {
      // Tree#/Log# are the row order (legacy shows the row index, not an editable field).
      const payload: BioPlot = {
        ...current,
        standTable: (current.standTable ?? []).map((r, i) => ({ ...r, treeNumber: String(i + 1) })),
        cwdTable: (current.cwdTable ?? []).map((r, i) => ({ ...r, logNumber: String(i + 1) })),
      };
      await API.protocolChecklist.saveBioPlot(stratumId, payload);
      setCurrent(null); // on save success, close the form and return to the table
      await loadPlots();
      display({ kind: 'success', title: 'Plot saved', timeout: 4000 });
    } catch (err) {
      reportError('Save failed', err);
    } finally {
      setBusy(false);
    }
  };

  const deleteRow = async (row: BioPlotRow) => {
    if (!row.plotId) return;
    if (
      !(await confirm({
        title: 'Delete plot?',
        message: `Delete plot ${row.plotNumber || row.plotId}? This can't be undone.`,
      }))
    )
      return;
    setBusy(true);
    try {
      await API.protocolChecklist.deleteBioPlot(row.plotId, row.revisionCount ?? '');
      await loadPlots();
      display({ kind: 'success', title: 'Plot deleted', timeout: 4000 });
    } catch (err) {
      reportError('Delete failed', err);
    } finally {
      setBusy(false);
    }
  };

  // Legacy row caps: hard stop at 100, confirm once past 50.
  const canAddRow = (count: number, noun: string): boolean => {
    if (count >= TABLE_MAX) {
      display({
        kind: 'warning',
        title: `Max of ${TABLE_MAX} ${noun} rows reached`,
        timeout: 5000,
      });
      return false;
    }
    if (
      count >= TABLE_WARN &&
      !window.confirm('Are you sure you REALLY NEED to document this many?')
    ) {
      return false;
    }
    return true;
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
  const addStand = () => {
    if (!canAddRow((current?.standTable ?? []).length, 'Stand Table')) return;
    setCurrent((prev) => (prev ? { ...prev, standTable: [...(prev.standTable ?? []), {}] } : prev));
  };
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
  const addCwd = () => {
    if (!canAddRow((current?.cwdTable ?? []).length, 'Coarse Woody Debris')) return;
    setCurrent((prev) => (prev ? { ...prev, cwdTable: [...(prev.cwdTable ?? []), {}] } : prev));
  };
  const removeCwd = (index: number) =>
    setCurrent((prev) =>
      prev ? { ...prev, cwdTable: (prev.cwdTable ?? []).filter((_, i) => i !== index) } : prev,
    );

  const colOptions = (kind: Col['kind']): CodeOption[] =>
    kind === 'select-spp'
      ? species
      : kind === 'select-wt'
        ? wtDecay
        : kind === 'select-cwd'
          ? cwdDecay
          : [];

  // --- field render helpers ---
  const roField = (label: string, value: string): ReactNode => (
    <div className="protocol-checklist__field">
      <span className="protocol-checklist__label">{label}</span>
      <span className="protocol-checklist__value">{value || '—'}</span>
    </div>
  );

  const textField = (
    key: string,
    label: string,
    maxLength?: number,
    disabled = false,
    required = false,
  ): ReactNode => {
    const error = plotFieldError(key);
    return readOnly ? (
      roField(label, get(key))
    ) : (
      <TextInput
        id={`plot-${key}`}
        labelText={required ? `${label} (required)` : label}
        value={get(key)}
        maxLength={maxLength}
        disabled={disabled}
        onChange={(e) => set(key, e.target.value)}
        invalid={attemptedSave && error !== ''}
        invalidText={error}
      />
    );
  };

  const selectField = (
    key: string,
    label: string,
    options: CodeOption[],
    disabled = false,
    required = false,
  ): ReactNode => {
    const error = plotFieldError(key);
    return readOnly ? (
      roField(label, options.find((o) => o.code === get(key))?.description ?? get(key))
    ) : (
      <Select
        id={`plot-${key}`}
        labelText={required ? `${label} (required)` : label}
        value={get(key)}
        disabled={disabled}
        invalid={attemptedSave && error !== ''}
        invalidText={error}
        onChange={(e) => set(key, e.target.value)}
      >
        <SelectItem value="" text="—" />
        {options.map((o) => (
          <SelectItem key={o.code} value={o.code} text={o.description} />
        ))}
      </Select>
    );
  };

  const checkField = (key: string, label: string): ReactNode =>
    readOnly ? (
      roField(label, get(key) === 'Y' ? 'Yes' : 'No')
    ) : (
      <Checkbox
        id={`plot-${key}`}
        labelText={label}
        checked={get(key) === 'Y'}
        onChange={(_e, { checked }) => set(key, checked ? 'Y' : 'N')}
      />
    );

  // One sub-table cell control, driven by the column's `kind`.
  const cell = (
    caption: string,
    col: Col,
    row: Record<string, string | undefined>,
    index: number,
    onChange: (index: number, key: string, value: string) => void,
  ): ReactNode => {
    if (col.kind === 'index') return String(index + 1); // read-only row number
    const value = row[col.key] ?? '';
    if (col.kind) {
      const options = colOptions(col.kind);
      if (readOnly) return options.find((o) => o.code === value)?.description ?? value ?? '—';
      return (
        <Select
          id={`${caption}-${index}-${col.key}`}
          labelText={col.label}
          hideLabel
          size="sm"
          value={value}
          onChange={(e) => onChange(index, col.key, e.target.value)}
        >
          <SelectItem value="" text="—" />
          {options.map((o) => (
            <SelectItem key={o.code} value={o.code} text={o.description} />
          ))}
        </Select>
      );
    }
    if (readOnly) return value || '—';
    return (
      <TextInput
        id={`${caption}-${index}-${col.key}`}
        labelText={col.label}
        hideLabel
        size="sm"
        maxLength={col.maxLength}
        value={value}
        onChange={(e) => onChange(index, col.key, e.target.value)}
      />
    );
  };

  // One child-collection grid (stand or CWD).
  const childGrid = (
    caption: string,
    cols: Col[],
    items: Array<Record<string, string | undefined>>,
    keyOf: (index: number) => string,
    onChange: (index: number, key: string, value: string) => void,
    removeRowAt: (index: number) => void,
    addRow: () => void,
    addLabel: string,
  ): ReactNode => (
    <>
      <table className="rip-field-grid">
        <thead>
          <tr>
            {cols.map((c) => (
              <th scope="col" key={c.key}>
                {c.label}
              </th>
            ))}
            {!readOnly && <th aria-label="Actions" />}
          </tr>
        </thead>
        <tbody>
          {items.length === 0 && (
            <tr>
              <td colSpan={cols.length + (readOnly ? 0 : 1)}>None.</td>
            </tr>
          )}
          {items.map((row, index) => (
            <tr key={keyOf(index)}>
              {cols.map((c) => (
                <td key={c.key}>{cell(caption, c, row, index, onChange)}</td>
              ))}
              {!readOnly && (
                <td>
                  <Button
                    kind="danger--tertiary"
                    size="sm"
                    hasIconOnly
                    renderIcon={TrashCan}
                    iconDescription="Remove row"
                    onClick={() => removeRowAt(index)}
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
    </>
  );

  // Read-only stratum context shown in both the table and the open plot form. `tight` uses a
  // compact flex layout (values sit close together) instead of the full-width spread grid.
  const stratumInfo = (tight: boolean): ReactNode => (
    <div className={tight ? 'bio-plot__stratum-info' : 'rip-form__grid'}>
      {roField('Stratum type', stratumTypeLabel)}
      {roField('# of plots completed', `${plotsCompleted} of ${plotsExpected || '—'}`)}
    </div>
  );

  if (loading) {
    return <SkeletonText paragraph lineCount={8} />;
  }

  if (strata.length === 0) {
    return <p>Add a stratum on the Stratum summary tab before adding plots.</p>;
  }

  const noEvaluators = evaluators !== null && evaluators.length === 0;

  return (
    <div className="rip-form">
      {/* The plots table and the plot form are mutually exclusive — the table is hidden
          while a plot form is open (mirrors the Stratum summary tab). */}
      {!current && (
        <>
          {noEvaluators && (
            <InlineNotification
              kind="info"
              lowContrast
              hideCloseButton
              title="No evaluator saved"
              subtitle="Plots cannot be added until an Evaluator has been saved on the Administration tab."
            />
          )}

          {/* Two aligned columns: Stratum / Stratum type on the left, Add plot / # of plots
              completed on the right. */}
          <div className="bio-plot__header">
            <Select
              id="plots-stratum"
              labelText="Stratum"
              value={stratumId}
              onChange={(e) => setStratumId(e.target.value)}
            >
              {strata.map((s) => (
                <SelectItem
                  key={s.stratumId}
                  value={s.stratumId ?? ''}
                  text={s.stratumNumber || s.stratumId || ''}
                />
              ))}
            </Select>
            {!readOnly ? (
              <Button
                kind="tertiary"
                size="lg"
                className="bio-strata__add"
                disabled={busy || noEvaluators}
                onClick={addPlot}
              >
                <Add size={16} className="bio-strata__add-icon" />
                Add plot
              </Button>
            ) : (
              <div />
            )}
            {roField('Stratum type', stratumTypeLabel)}
            {roField('# of plots completed', `${plotsCompleted} of ${plotsExpected || '—'}`)}
          </div>

          <div className="bio-strata">
            {rows.length > 0 && (
              <Table size="sm" className="bio-strata__table">
                <TableHead>
                  <TableRow>
                    <TableHeader>Plot number</TableHeader>
                    <TableHeader>Assessor name</TableHeader>
                    <TableHeader>Actions</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow key={row.plotId}>
                      <TableCell>{row.plotNumber || row.plotId}</TableCell>
                      <TableCell>{row.assessorName}</TableCell>
                      <TableCell>
                        <Button
                          kind="ghost"
                          size="sm"
                          renderIcon={Edit}
                          iconDescription="Edit"
                          hasIconOnly
                          disabled={busy}
                          onClick={() => void select(row.plotId ?? '')}
                        />
                        {!readOnly && (
                          <Button
                            kind="danger--ghost"
                            size="sm"
                            renderIcon={TrashCan}
                            iconDescription="Delete"
                            hasIconOnly
                            disabled={busy}
                            onClick={() => void deleteRow(row)}
                          />
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </div>
        </>
      )}

      {current && (
        <>
          {/* Actions at the top, mirroring the Stratum summary tab. */}
          <div className="protocol-checklist__section-actions">
            {!readOnly && (
              <Button size="lg" disabled={busy} onClick={() => void handleSave()}>
                Save
              </Button>
            )}
            <Button kind="ghost" size="lg" disabled={busy} onClick={() => setCurrent(null)}>
              Cancel
            </Button>
          </div>

          {stratumInfo(true)}

          <fieldset className="rip-form__group">
            <legend>Plot identification</legend>
            <div className="rip-form__grid">
              {readOnly ? (
                roField('No UTM signal available', noUtmSignal ? 'Yes' : 'No')
              ) : (
                <Checkbox
                  id="plot-utmSignal"
                  labelText="No UTM signal available"
                  checked={noUtmSignal}
                  onChange={(_e, { checked }) => set('utmSignal', checked ? 'N' : 'Y')}
                />
              )}
            </div>
            <div className="rip-form__grid">
              {textField('plotNumber', 'Plot #', 3)}
              {selectField('assessorName', 'Evaluated by', evaluators ?? [])}
              {selectField('utmZone', 'Zone', UTM_ZONE_OPTIONS, noUtmSignal, !noUtmSignal)}
              {textField('utmEasting', 'Easting', 6, noUtmSignal, !noUtmSignal)}
              {textField('utmNorthing', 'Northing', 7, noUtmSignal, !noUtmSignal)}
            </div>
          </fieldset>

          <fieldset className="rip-form__group">
            <legend>Plot information</legend>
            <div className="rip-form__grid">{checkField('treeIndicator', 'Trees exist')}</div>
            <p className="rip-form__hint">Fill in one of:</p>
            <div className="rip-form__grid">
              {textField('basalAreaFactor', 'BAF', 2)}
              {textField('fixedAreaRadius', 'Fixed area radius (m)', 6)}
              {textField('fullCountArea', 'Full count area (ha)', 7)}
            </div>
            <div className="rip-form__grid">
              {checkField('cwdTransectIndicator', 'CWD in transect')}
            </div>
            <div className="rip-form__grid">
              {textField('firstLegTransect', 'Bearing 1st leg', 3, false, true)}
              {textField('secondLegTransect', '2nd leg', 3, false, true)}
            </div>
            <div className="rip-form__grid">{textField('plotComment', 'Comments')}</div>
          </fieldset>

          {/* Stand table only when "Trees exist"; CWD only when "CWD in transect" (legacy). */}
          {get('treeIndicator') === 'Y' && (
            <fieldset className="rip-form__group">
              <legend>Stand table (trees)</legend>
              {attemptedSave && (current.standTable ?? []).length === 0 && (
                <InlineNotification
                  kind="error"
                  title="Stand table required"
                  subtitle='"Trees exist" is checked — add at least one stand-table row, or uncheck it.'
                  hideCloseButton
                  lowContrast
                />
              )}
              {childGrid(
                'Stand',
                STAND_COLS,
                (current.standTable ?? []) as Array<Record<string, string | undefined>>,
                (i) => current.standTable?.[i]?.standId ?? `stand-${i}`,
                (index, key, value) => setStand(index, { [key]: value }),
                removeStand,
                addStand,
                'Add new row',
              )}
            </fieldset>
          )}

          {get('cwdTransectIndicator') === 'Y' && (
            <fieldset className="rip-form__group">
              <legend>Coarse woody debris (30 m transect)</legend>
              <div className="rip-form__grid">
                {roField('Bearing 1st leg', get('firstLegTransect'))}
                {roField('2nd leg', get('secondLegTransect'))}
              </div>
              {childGrid(
                'CWD',
                CWD_COLS,
                (current.cwdTable ?? []) as Array<Record<string, string | undefined>>,
                (i) => current.cwdTable?.[i]?.cwdId ?? `cwd-${i}`,
                (index, key, value) => setCwd(index, { [key]: value }),
                removeCwd,
                addCwd,
                'Add new row',
              )}
            </fieldset>
          )}

          {(get('treeIndicator') === 'Y' || get('cwdTransectIndicator') === 'Y') && (
            <p className="rip-form__hint">* Decimal place means measured</p>
          )}
        </>
      )}
    </div>
  );
};

export default BioPlotsView;

import { Add, Edit, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  Select,
  SelectItem,
  SkeletonText,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC, type ReactNode } from 'react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import { requiredLabel } from '@/utils/requiredLabel';

import OutstandingPanel from './OutstandingPanel';
import RequiredLegend from './RequiredLegend';

import type { OutstandingGroup } from './tabStatus';
import type { CodeOption } from '@/types/configuration';
import type {
  BioCwdRow,
  BioPlot,
  BioPlotRow,
  BioStandRow,
  BioStratumRow,
} from '@/types/protocolChecklist';
import type { ValidationMode } from '@/utils/validation';

import { useAuth } from '@/context/auth/useAuth';
import { useConfirm } from '@/context/confirm/useConfirm';
import { useNotification } from '@/context/notification/useNotification';
import { useSettledFields } from '@/hooks/useSettledFields';
import {
  PLOT_TEXT_LIMITS,
  cwdRowErrors,
  plotBlockingErrors,
  plotHeaderErrors,
  standRowErrors,
} from '@/pages/ProtocolChecklist/plotValidation';
import API from '@/services/APIs';
import { apiErrorMessage } from '@/utils/apiError';
import { byteLength } from '@/utils/textLimits';
import { errorsForSettledFields } from '@/utils/validation';
import FormLock from '@/components/core/FormLock';
import ActionButton from '@/components/core/ActionButton';

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
  /** Outstanding submit rules for this tab, grouped by plot (see OutstandingPanel). */
  outstanding?: OutstandingGroup[];
  /** Called after a save or delete lands, so the tab-completion dots re-derive. */
  onSaved?: () => void;
  /** `error` once a submit has been refused — see OutstandingPanel. */
  tone?: 'neutral' | 'error';
};

// Plot assessor userids are stored bare (no `IDIR\` prefix) — e.g. `ASODHI` — while the logged-in
// user's providerUsername is the full `IDIR\ASODHI`. Strip the directory prefix so both compare and
// store consistently with the existing bare-userid plot data.
const bareUserid = (value?: string): string => value?.split('\\').pop()?.trim() ?? '';
const sameEvaluator = (a?: string, b?: string): boolean => {
  const norm = (v?: string) => bareUserid(v).toUpperCase();
  return norm(a) !== '' && norm(a) === norm(b);
};

// Sub-table columns. `kind` picks the cell control: index = read-only row number,
// select-* = coded dropdown, otherwise a (length-capped) text input.
type Col = {
  key: string;
  label: string;
  kind?: 'index' | 'select-spp' | 'select-wt' | 'select-cwd';
  maxLength?: number;
  /**
   * Owed by every row once one exists — see {@link standRowErrors} / {@link cwdRowErrors}, which is
   * where the rule is enforced. The columns marked here are NOT NULL on
   * BIODIVERSITY_STAND_DETAIL / COARSE_WOODY_DEBRIS_DETAIL; the row number is generated and the
   * comment is free text, so neither is asked for.
   */
  required?: boolean;
};

// Legacy column order: Tree# | Spp. | WT Class | DBH (cm) | Ht (m) | Comments.
const STAND_COLS: Col[] = [
  { key: 'treeNumber', label: 'Tree #', kind: 'index' },
  { key: 'speciesCode', label: 'Spp.', kind: 'select-spp', required: true },
  { key: 'decayClassCode', label: 'WT Class', kind: 'select-wt', required: true },
  { key: 'dbh', label: 'DBH (cm)', maxLength: 5, required: true },
  { key: 'height', label: 'Ht (m)', maxLength: 4, required: true },
  { key: 'comments', label: 'Comments', maxLength: 50 },
];

// Legacy column order: Log# | Spp. | Decay Class | Dia. (cm) | Length (m) | Comments.
const CWD_COLS: Col[] = [
  { key: 'logNumber', label: 'Log #', kind: 'index' },
  { key: 'speciesCode', label: 'Spp.', kind: 'select-spp', required: true },
  { key: 'decayClassCode', label: 'Decay Class', kind: 'select-cwd', required: true },
  { key: 'logDiameter', label: 'Dia. (cm)', maxLength: 5, required: true },
  { key: 'logLength', label: 'Length (m)', maxLength: 4, required: true },
  { key: 'comments', label: 'Comments', maxLength: 50 },
];

const UTM_ZONE_OPTIONS: CodeOption[] = ['7', '8', '9', '10', '11'].map((z) => ({
  code: z,
  description: z,
}));

/**
 * Plot fields rendered as a multi-line box rather than a single-line input — the free-text comment
 * only. Mirrors MULTILINE_KEYS in BioStratumView, where the same 2000-char "Comments" field lives.
 */
const MULTILINE_KEYS = new Set(['plotComment']);

const TABLE_MAX = 100;
const TABLE_WARN = 50;

const anyRowInvalid = (
  rows: readonly unknown[] | undefined,
  rowErrors: (row: Record<string, string | undefined>) => Record<string, string>,
): boolean =>
  (rows ?? []).some(
    (r) => Object.keys(rowErrors(r as Record<string, string | undefined>)).length > 0,
  );

// A plot blocks Save when the header has errors, or "Trees exist" / "CWD in transect" is checked but the
// matching sub-table is empty (trees — the legacy `notrees` check) or has an invalid row. Extracted to
// keep the component's cognitive complexity down.
/**
 * Whether the plot cannot be stored as it stands.
 *
 * Not the same question as "is this plot finished". A blank bearing or a missing UTM fix is a gap:
 * it is marked, counted on the tab and blocks submit, but the plot still saves. A stand-table or CWD
 * row the user has *added* must be complete, because every column of BIODIVERSITY_STAND_DETAIL and
 * COARSE_WOODY_DEBRIS_DETAIL is NOT NULL. "Trees exist" with no rows at all is a gap, not a bad row.
 */
const plotHasBlockingErrors = (
  plot: BioPlot | null,
  readOnly: boolean,
  headerErrors: Record<string, string>,
): boolean => {
  if (!plot || readOnly) return false;
  const trees = plot.treeIndicator === 'Y';
  const standInvalid = trees && anyRowInvalid(plot.standTable, standRowErrors);
  const cwdInvalid =
    plot.cwdTransectIndicator === 'Y' && anyRowInvalid(plot.cwdTable, cwdRowErrors);
  return (
    Object.keys(plotBlockingErrors(plot, headerErrors)).length > 0 || standInvalid || cwdInvalid
  );
};

const BioPlotsView: FC<Props> = ({
  checklistId,
  canEdit,
  submitted,
  active,
  onSaved,
  outstanding = [],
  tone,
}) => {
  const { display } = useNotification();
  const { user } = useAuth();
  const me = user?.providerUsername;
  const confirm = useConfirm();
  const [strata, setStrata] = useState<BioStratumRow[]>([]);
  const [stratumId, setStratumId] = useState('');
  const [rows, setRows] = useState<BioPlotRow[]>([]);
  const [current, setCurrent] = useState<BioPlot | null>(null);
  // Errors stay hidden until a save is attempted on the open plot; reset when another plot is
  // opened or a new one is added.
  const [showErrors, setShowErrors] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  // Reference data for the coded dropdowns.
  const [species, setSpecies] = useState<CodeOption[]>([]);
  const [wtDecay, setWtDecay] = useState<CodeOption[]>([]);
  const [cwdDecay, setCwdDecay] = useState<CodeOption[]>([]);
  const [strataTypes, setStrataTypes] = useState<CodeOption[]>([]);
  // The checklist's Evaluator (team lead) from the Opening tab — the default "Evaluated by" for new
  // plots. `name` is the FAM-resolved display; `userid` is the bare IDIR userid stored on the plot.
  const [evaluator, setEvaluator] = useState<{ userid?: string; name?: string }>({});

  const reportError = useCallback(
    (title: string, err: unknown) =>
      display({
        kind: 'error',
        title,
        subtitle: apiErrorMessage(err),
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

  // Reference data for the coded dropdowns, plus the checklist Evaluator (team lead) that new plots
  // default their "Evaluated by" to. The evaluator comes from the Opening tab's source
  // (biodiversity_evaluator_name, FAM-resolved) — the same person shown as "Evaluator" there — not the
  // checklist tombstone's last-updater userid.
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      API.configuration.getSpecies(),
      API.configuration.getWildlifeTreeDecay(),
      API.configuration.getCwdDecay(),
      API.configuration.getStrataTypes(),
      API.protocolChecklist.getBiodiversityOpening(checklistId),
    ])
      .then(([sp, wt, cwd, st, opening]) => {
        if (cancelled) return;
        setSpecies(sp);
        setWtDecay(wt);
        setCwdDecay(cwd);
        setStrataTypes(st);
        setEvaluator({ userid: opening.teamLeadNameId, name: opening.teamLeadName });
      })
      .catch((err: unknown) => {
        if (cancelled) return;
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

  // Coordinates are owed only on an affirmative signal — the same test plotValidation makes, so the
  // asterisk and the outstanding item agree. Not simply "the box is unchecked": a legacy plot that
  // never answered the question is exempt, and marking its fields required would promise an error
  // that never comes.
  const utmSignalled = get('utmSignal') === 'Y';

  // --- computed stratum read-out (legacy "Stratum Identification" block) ---
  const selectedStratum = strata.find((s) => s.stratumId === stratumId);
  const stratumTypeLabel =
    strataTypes.find((t) => t.code === selectedStratum?.strataTypeCode)?.description ??
    selectedStratum?.strataTypeCode ??
    '';
  const plotsExpected = selectedStratum?.plotCount ?? '';
  const plotsCompleted = String(rows.length);

  const select = async (plotId?: string) => {
    // A row with no id is a bug upstream, not something to send: `/plots/` binds an empty string to
    // BIODIVERSITY_PLOT_ID (a NUMBER), which fails inside Oracle as "Invalid Input Number" and
    // reaches the evaluator as "A database error occurred". Say what is actually wrong instead.
    if (!plotId) {
      display({
        kind: 'error',
        title: 'Could not load the plot',
        subtitle: "This plot has no id, so it can't be opened. Reload the checklist and try again.",
        timeout: 9000,
      });
      return;
    }
    setBusy(true);
    try {
      setCurrent(await API.protocolChecklist.getBioPlot(plotId));
      setShowErrors(false);
      resetSettled();
    } catch (err) {
      reportError('Could not load the plot', err);
    } finally {
      setBusy(false);
    }
  };

  const addPlot = () => {
    setShowErrors(false);
    resetSettled();
    setCurrent({
      stratumId,
      // Default "Evaluated by" to the checklist's Evaluator; claimable via "Assign it to me".
      assessorName: bareUserid(evaluator.userid),
      // The "No UTM signal available" box renders unchecked on a new plot, and unchecked *means*
      // utm_signal = 'Y'. Stated here so the record says what the form shows: left unset, the plot
      // would read as one that never answered the question — which is how legacy rows read, and
      // those are deliberately exempt from the UTM rules (see utmErrors).
      utmSignal: 'Y',
      treeIndicator: 'N',
      cwdTransectIndicator: 'N',
      standTable: [],
      cwdTable: [],
    });
  };

  // "Assign it to me" — claim this plot's assessor for the current user (mirrors the Opening
  // Evaluator widget). Stored as a bare userid to match the existing plot data.
  // Clear the server-resolved display name with it: it describes the *previous* assessor, so leaving
  // it would show their name next to the new userid until the save round-trips.
  const assignToMe = () => {
    if (!me) return;
    setCurrent((prev) =>
      prev
        ? ({ ...prev, assessorName: bareUserid(me), assessorDisplayName: undefined } as BioPlot)
        : prev,
    );
  };

  // Inline validation runs live off the edited plot (like SiteDetail): a field's error shows the
  // moment it's invalid and clears when fixed. plotFieldError is a lookup into the header-error map;
  // standError / cwdError look up a sub-table cell. The Save handler blocks while any remain — no toast.
  const stratumType = selectedStratum?.strataTypeCode ?? '';
  // Errors are computed live but only *displayed* once a save has been attempted (see the same gate
  // in BioOpeningView). `headerErrors` still drives the save guard; the three lookups below are what
  // the UI renders, so every call site is gated at once — header fields and sub-table cells alike.
  // The numbers the other plots in this stratum already hold. The plot being edited is excluded by
  // id, so re-saving it without changing its number is not reported as a clash with itself — the
  // same exclusion FREP_BIODIVERSITY_PLOT.VALIDATE makes.
  const takenPlotNumbers = rows
    .filter((row) => !current?.plotId || row.plotId !== current.plotId)
    .map((row) => row.plotNumber ?? '');

  const headerErrors: Record<string, string> =
    current && !readOnly ? plotHeaderErrors(current, stratumType, takenPlotNumbers) : {};
  // Before the first save attempt the same rules run in 'typing' mode, which keeps only what no
  // further typing can rescue: a letter in a number, a decimal place too many, a value over the
  // maximum, an Easting already past six digits. Minimums, exact lengths, the duplicate plot number
  // and every blank required field wait for Save — each is a state a correct value passes through.
  const typingErrors: Record<string, string> =
    current && !readOnly ? plotHeaderErrors(current, stratumType, takenPlotNumbers, 'typing') : {};
  // Between the two: a field the user has filled in and moved on from is finished enough to judge
  // against the full rules — a short Easting, a measurement below its floor, a plot number already
  // taken. A field still blank is exempt, so tabbing through an empty plot marks nothing.
  const { settled, markSettled, resetSettled } = useSettledFields();
  const settledErrors = errorsForSettledFields(headerErrors, settled, (key) =>
    typeof (current as Record<string, unknown> | null)?.[key] === 'string'
      ? ((current as Record<string, string>)[key] ?? '')
      : '',
  );
  const plotFieldError = (key: string): string =>
    (showErrors ? headerErrors[key] : (settledErrors[key] ?? typingErrors[key])) ?? '';
  /** One sub-table cell, on the same three-way gate as a header field. */
  const rowError = (
    caption: string,
    rows: Array<Record<string, string | undefined>>,
    rules: (
      row: Record<string, string | undefined>,
      mode?: ValidationMode,
    ) => Record<string, string>,
    index: number,
    colKey: string,
  ): string => {
    const row = rows[index] ?? {};
    if (showErrors) return rules(row)[colKey] ?? '';
    const left = settled.has(`${caption}-${index}-${colKey}`) && (row[colKey] ?? '').trim() !== '';
    return (left ? rules(row)[colKey] : rules(row, 'typing')[colKey]) ?? '';
  };
  const standError = (index: number, colKey: string): string =>
    rowError(
      'Stand',
      (current?.standTable ?? []) as Array<Record<string, string | undefined>>,
      standRowErrors,
      index,
      colKey,
    );
  const cwdError = (index: number, colKey: string): string =>
    rowError(
      'CWD',
      (current?.cwdTable ?? []) as Array<Record<string, string | undefined>>,
      cwdRowErrors,
      index,
      colKey,
    );

  // Blocks Save while any header/sub-table error remains (incl. the legacy "Trees exist ⇒ ≥1 stand
  // row" consistency check). See plotHasBlockingErrors.
  const hasErrors = plotHasBlockingErrors(current, readOnly, headerErrors);

  const handleSave = async () => {
    if (!current) return;
    // First point the user has asked for the plot to be complete — reveal the errors now.
    setShowErrors(true);
    if (hasErrors) return;
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
      onSaved?.();
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
        title: 'Are you sure you want to delete this plot?',
        message: (
          <>
            <strong>Plot {row.plotNumber || row.plotId}</strong> will be permanently deleted from
            this stratum. This action cannot be undone.
          </>
        ),
      }))
    )
      return;
    setBusy(true);
    try {
      await API.protocolChecklist.deleteBioPlot(row.plotId, row.revisionCount ?? '');
      await loadPlots();
      onSaved?.();
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

  // Sub-table coded dropdowns (species / WT class / decay) display as "<code> - <description>",
  // except WT Class, which shows the bare code. The wildlife-tree decay descriptions returned by
  // get_wildlife_tree_decay_code describe SOFTWOODS (codes 1-8); hardwoods use only 1-5 and give
  // some of those codes a different meaning, so pairing one description list with every row
  // mislabels hardwood entries. Legacy rendered this dropdown code-only for the same reason
  // (frep212BIOPlots.jsp: `labelProperty="code"`).
  const codeOptionText = (o: CodeOption, kind: Col['kind']): string =>
    kind === 'select-wt' ? o.code : `${o.code} - ${o.description}`;

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
    if (readOnly) {
      return roField(label, get(key));
    }
    const inputProps = {
      id: `plot-${key}`,
      // Off across the checklist forms: every field keeps a stable id across strata / plots /
      // features, so the browser treats the next one as the same field and offers what was typed
      // last time. Accepting one suggestion then cascades into the rest of the group — these are
      // per-record evaluation values, never a repeat of the previous record.
      autoComplete: 'off',

      labelText: requiredLabel(label, required),
      value: get(key),
      maxLength,
      disabled,
      onChange: (e: { target: { value: string } }) => set(key, e.target.value),
      onBlur: () => markSettled(key),
      invalid: error !== '',
      invalidText: error,
    };
    const input = MULTILINE_KEYS.has(key) ? (
      <TextArea {...inputProps} rows={4} />
    ) : (
      <TextInput {...inputProps} />
    );
    // Length-limited free text carries a live counter; plotHeaderErrors already blocks the save and
    // supplies the error text, so the counter only reports the count.
    const limit = PLOT_TEXT_LIMITS[key];
    return limit === undefined ? (
      input
    ) : (
      <FieldWithCounter used={byteLength(get(key))} limit={limit}>
        {input}
      </FieldWithCounter>
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
        autoComplete="off"
        id={`plot-${key}`}
        labelText={requiredLabel(label, required)}
        value={get(key)}
        disabled={disabled}
        invalid={error !== ''}
        invalidText={error}
        onChange={(e) => set(key, e.target.value)}
      >
        <SelectItem value="" text="Choose an option" />
        {options.map((o) => (
          <SelectItem key={o.code} value={o.code} text={o.description} />
        ))}
      </Select>
    );
  };

  // "Evaluated by" — the plot's assessor. Defaults to the checklist Evaluator (see addPlot) and is
  // claimed via "Assign it to me" (mirrors the Opening Evaluator widget) rather than a team dropdown,
  // since team management was removed. Shown as "First Last (USERID)" like the checklist header:
  // the API resolves the saved assessor through FAM (assessorDisplayName). The checklist Evaluator
  // is the fallback for an unsaved assessor — a new plot, or one just claimed via "Assign it to me",
  // where nothing has round-tripped yet. Bare userid if neither is available.
  const evaluatedByField = (): ReactNode => {
    const currentId = get('assessorName');
    const resolved = get('assessorDisplayName');
    const displayName =
      resolved ||
      (sameEvaluator(currentId, evaluator.userid) ? evaluator.name || currentId : currentId);
    const error = plotFieldError('assessorName');
    if (readOnly) {
      return roField('Evaluated by', displayName);
    }
    return (
      <div className="protocol-checklist__field rip-form__cell--wide" key="evaluatedBy">
        <span className="protocol-checklist__label">{requiredLabel('Evaluated by', true)}</span>
        <span
          className="protocol-checklist__value"
          style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem' }}
        >
          <span>{displayName || '—'}</span>
          {me && !sameEvaluator(currentId, me) && (
            <Button kind="ghost" size="sm" disabled={busy} onClick={assignToMe}>
              Assign it to me
            </Button>
          )}
        </span>
        {error !== '' && <div className="protocol-checklist__field-error">{error}</div>}
      </div>
    );
  };

  const checkField = (key: string, label: string, disabled = false): ReactNode =>
    readOnly ? (
      roField(label, get(key) === 'Y' ? 'Yes' : 'No')
    ) : (
      <Checkbox
        id={`plot-${key}`}
        labelText={label}
        checked={get(key) === 'Y'}
        disabled={disabled}
        onChange={(_e, { checked }) => set(key, checked ? 'Y' : 'N')}
      />
    );

  // Render the inline cell error as a plain block under the control instead of Carbon's `invalidText`.
  // In this small/in-table context Carbon's `.cds--form-requirement` renders with zero layout height
  // and overflows the cell, so the row divider cuts straight through the message. A normal block grows
  // the cell so the divider sits below the whole input + message. (`invalid` still drives the red
  // border + icon.)
  const withError = (control: ReactNode, error: string): ReactNode => (
    <>
      {control}
      {error !== '' && <div className="rip-field-grid__cell-error">{error}</div>}
    </>
  );

  // One sub-table cell control, driven by the column's `kind`.
  const cell = (
    caption: string,
    col: Col,
    row: Record<string, string | undefined>,
    index: number,
    onChange: (index: number, key: string, value: string) => void,
    cellError: (index: number, colKey: string) => string,
  ): ReactNode => {
    if (col.kind === 'index') return String(index + 1); // read-only row number
    const value = row[col.key] ?? '';
    const error = cellError(index, col.key);
    if (col.kind) {
      const options = colOptions(col.kind);
      if (readOnly) {
        const match = options.find((o) => o.code === value);
        return match ? codeOptionText(match, col.kind) : value || '—';
      }
      return withError(
        <Select
          autoComplete="off"
          id={`${caption}-${index}-${col.key}`}
          labelText={col.label}
          hideLabel
          size="sm"
          value={value}
          invalid={error !== ''}
          onChange={(e) => onChange(index, col.key, e.target.value)}
        >
          <SelectItem value="" text="—" />
          {/* A saved code the list no longer offers (e.g. a retired code, or CWD decay class 5,
              which the API now filters out as unsampled) would otherwise leave the Select with
              no matching option — the browser falls back to the "—" placeholder and the next
              save silently rewrites the cell to blank. Keep the stored value selectable so it
              survives an edit the evaluator didn't intend to make. */}
          {value !== '' && !options.some((o) => o.code === value) && (
            <SelectItem value={value} text={value} />
          )}
          {options.map((o) => (
            <SelectItem key={o.code} value={o.code} text={codeOptionText(o, col.kind)} />
          ))}
        </Select>,
        error,
      );
    }
    if (readOnly) return value || '—';
    return withError(
      <TextInput
        autoComplete="off"
        id={`${caption}-${index}-${col.key}`}
        labelText={col.label}
        hideLabel
        size="sm"
        maxLength={col.maxLength}
        value={value}
        invalid={error !== ''}
        onBlur={() => markSettled(`${caption}-${index}-${col.key}`)}
        onChange={(e) => onChange(index, col.key, e.target.value)}
      />,
      error,
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
    cellError: (index: number, colKey: string) => string,
  ): ReactNode => (
    <>
      {/* No rows, no table. An empty grid was a full header rule over a single "None." cell — seven
          column names for a table the evaluator has not started, which reads as something that
          failed to load. The Add button below is the whole empty state; pressing it puts the first
          row on screen, and the header arrives with it. */}
      {items.length > 0 && (
        <table className="rip-field-grid rip-field-grid--inputs">
          <thead>
            <tr>
              {cols.map((c) => (
                <th scope="col" key={c.key}>
                  {/* The cells carry no label of their own — the header is the label, so the marker
                      belongs here. Not shown read-only, where nothing can be filled in. */}
                  {requiredLabel(c.label, !readOnly && Boolean(c.required))}
                </th>
              ))}
              {!readOnly && (
                <th scope="col" className="table-actions">
                  Action
                </th>
              )}
            </tr>
          </thead>
          <tbody>
            {items.map((row, index) => (
              <tr key={keyOf(index)}>
                {cols.map((c) => (
                  <td key={c.key}>{cell(caption, c, row, index, onChange, cellError)}</td>
                ))}
                {!readOnly && (
                  <td>
                    <Button
                      kind="danger--tertiary"
                      size="sm"
                      renderIcon={TrashCan}
                      onClick={() => removeRowAt(index)}
                    >
                      Delete
                    </Button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {!readOnly && (
        <Button kind="tertiary" size="lg" renderIcon={Add} onClick={addRow}>
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

  return (
    <div className="rip-form">
      <FormLock busy={busy}>
        <OutstandingPanel groups={outstanding} tone={tone} />
        {/* The plots table and the plot form are mutually exclusive — the table is hidden
            while a plot form is open (mirrors the Stratum summary tab). */}
        {!current && (
          <>
            {/* Two aligned columns: Stratum / Stratum type on the left, Add plot / # of plots
                completed on the right. */}
            <div className="bio-plot__header">
              <Select
                autoComplete="off"
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
                  renderIcon={Add}
                  disabled={busy}
                  onClick={addPlot}
                >
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
                      <TableHeader>Action</TableHeader>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {rows.map((row) => (
                      <TableRow key={row.plotId}>
                        <TableCell>{row.plotNumber || row.plotId}</TableCell>
                        <TableCell>{row.assessorDisplayName || row.assessorName}</TableCell>
                        <TableCell className="table-actions">
                          <Button
                            kind="ghost"
                            size="sm"
                            renderIcon={Edit}
                            disabled={busy}
                            onClick={() => void select(row.plotId)}
                          >
                            Edit
                          </Button>
                          {!readOnly && (
                            <Button
                              kind="danger--ghost"
                              size="sm"
                              renderIcon={TrashCan}
                              disabled={busy}
                              onClick={() => void deleteRow(row)}
                            >
                              Delete
                            </Button>
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
                <ActionButton busy={busy} onClick={() => void handleSave()} />
              )}
              <Button kind="ghost" size="lg" disabled={busy} onClick={() => setCurrent(null)}>
                Cancel
              </Button>
            </div>

            {/* Under the buttons, directly above the fields it describes — and only where fields are
                marked, which is the open form, not the list behind it. */}
            {!readOnly && <RequiredLegend />}

            {stratumInfo(true)}

            <fieldset className="rip-form__group">
              <legend>Plot identification</legend>
              <div className="rip-form__grid">
                {textField('plotNumber', 'Plot #', 3, false, true)}
                {evaluatedByField()}
              </div>
              {/* The checkbox sits between the plot's own details and the coordinates, because it is
                  the switch that governs the row beneath it: ticking it disables all three. Above the
                  lot, it read as a heading for the whole section. */}
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
                {selectField('utmZone', 'Zone', UTM_ZONE_OPTIONS, noUtmSignal, utmSignalled)}
                {textField('utmEasting', 'Easting', 6, noUtmSignal, utmSignalled)}
                {textField('utmNorthing', 'Northing', 7, noUtmSignal, utmSignalled)}
              </div>
            </fieldset>

            <fieldset className="rip-form__group">
              <legend>Plot information</legend>
              <div className="rip-form__grid">
                {/* "Trees exist" is allowed on every stratum type (including clear-cut) — no gating. */}
                {checkField('treeIndicator', 'Trees exist')}
              </div>
              <p className="rip-form__hint">Fill in one of:</p>
              <div className="rip-form__grid">
                {textField('basalAreaFactor', 'BAF', 2)}
                {/* A clear-cut plot must use the fixed-area radius — the other two methods are
                    refused for CC — so it is the one measurement field that is genuinely owed. */}
                {textField(
                  'fixedAreaRadius',
                  'Fixed area radius (m)',
                  6,
                  false,
                  stratumType === 'CC',
                )}
                {textField('fullCountArea', 'Full count area (ha)', 7)}
              </div>
              {/* Directly under the measurement row that sizes the plot, and under the "Trees exist"
                  box that calls for it — both tables used to sit below the whole section, so ticking a
                  box added rows a screen away from the control that asked for them. */}
              {/* Read-only with nothing recorded now has nothing to show — no table and no Add
                  button — so the section is skipped rather than left as a bare legend. */}
              {get('treeIndicator') === 'Y' &&
                (!readOnly || (current.standTable ?? []).length > 0) && (
                  <fieldset className="rip-form__group">
                    {/* The table itself is owed, not just its columns: the section only renders once
                      "Trees exist" is ticked, and the proc refuses the submit while it holds no rows
                      (`frep.submit.biodiversity.plot.notrees`). */}
                    <legend>{requiredLabel('Stand table (trees)', !readOnly)}</legend>
                    {/* Under the heading of the table it applies to, where the reader meets it before
                      entering a row. It repeats across both tables on purpose: each is filled in on
                      its own, and a note left at one of them is no use at the other. */}
                    <p className="rip-form__hint">* Decimal place means measured</p>
                    {childGrid(
                      'Stand',
                      STAND_COLS,
                      (current.standTable ?? []) as Array<Record<string, string | undefined>>,
                      (i) => current.standTable?.[i]?.standId ?? `stand-${i}`,
                      (index, key, value) => setStand(index, { [key]: value }),
                      removeStand,
                      addStand,
                      'Add stand table (tree)',
                      standError,
                    )}
                  </fieldset>
                )}

              <div className="rip-form__grid">
                {checkField('cwdTransectIndicator', 'CWD in transect')}
              </div>
              <div className="rip-form__grid">
                {textField('firstLegTransect', 'Bearing 1st leg', 3, false, true)}
                {textField('secondLegTransect', '2nd leg', 3, false, true)}
              </div>
              {/* Under the bearings that define the transect it measures. Those used to be repeated
                  read-only at the top of this block, which is redundant now they are the row above. */}
              {get('cwdTransectIndicator') === 'Y' &&
                (!readOnly || (current.cwdTable ?? []).length > 0) && (
                  <fieldset className="rip-form__group">
                    {/* Owed for the same reason as the stand table — `frep.submit.biodiversity.plot.nocwd`. */}
                    <legend>{requiredLabel('Coarse woody debris (30 m transect)', !readOnly)}</legend>
                    {/* Under the heading of the table it applies to, where the reader meets it before
                      entering a row. It repeats across both tables on purpose: each is filled in on
                      its own, and a note left at one of them is no use at the other. */}
                    <p className="rip-form__hint">* Decimal place means measured</p>
                    {childGrid(
                      'CWD',
                      CWD_COLS,
                      (current.cwdTable ?? []) as Array<Record<string, string | undefined>>,
                      (i) => current.cwdTable?.[i]?.cwdId ?? `cwd-${i}`,
                      (index, key, value) => setCwd(index, { [key]: value }),
                      removeCwd,
                      addCwd,
                      'Add CWD (log)',
                      cwdError,
                    )}
                  </fieldset>
                )}

              <div className="rip-form__grid">{textField('plotComment', 'Comments')}</div>
            </fieldset>
          </>
        )}
      </FormLock>
    </div>
  );
};

export default BioPlotsView;

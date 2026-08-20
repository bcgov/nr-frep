import {
  OPENING_REQUIRED_LABELS,
  openingRequiredErrors,
  openingTouched,
} from './openingValidation';

import type { BiodiversityOpening, BioPlot, BioStratum } from '@/types/protocolChecklist';

/**
 * Per-tab completion state for the checklist tab strip.
 *
 * The rules below mirror `FREP_TOMBSTONE.validate_biodiversity_chklst` — the proc that actually
 * blocks submit — so a tab only shows `complete` when it would not contribute a submit error. They
 * are deliberately a *read-only mirror*: the proc stays authoritative, and a drift between the two
 * shows up as a dot that disagrees with the submit panel, never as a blocked save.
 *
 * See slr-submit-validation-rules.local.md for the same rules in prose.
 */
export type TabStatus = 'empty' | 'partial' | 'complete' | 'errors';

/** One stratum with the plots that hang off it — the shape the plot rules need. */
export type StratumBundle = {
  stratum: BioStratum;
  plots: BioPlot[];
};

const has = (value?: string): boolean => value != null && `${value}`.trim() !== '';

const num = (value?: string): number | undefined => {
  if (!has(value)) return undefined;
  const parsed = Number(`${value}`.trim());
  return Number.isFinite(parsed) ? parsed : undefined;
};

// What an entirely blank Opening owes: the unconditional required fields (the two conditional
// comments are only owed once their indicator is set to Yes). Derived rather than hardcoded so it
// tracks the rules. Used when the record could not be read at all, so a failed load never reads as
// complete.
const OPENING_REQUIRED_COUNT = Object.keys(openingRequiredErrors({})).length;

/**
 * Required Opening fields still blank, named as the banner and the submit pre-flight list them.
 *
 * Read from the *saved* record, so it answers "what does this checklist still owe?" rather than
 * "what has the user typed so far?" — the two differ while a tab is mid-edit.
 */
export const openingOutstanding = (opening?: BiodiversityOpening | null): string[] => {
  const missing = opening ? openingRequiredErrors(opening) : openingRequiredErrors({});
  // Listed in tab order, so the list reads top-to-bottom the way the user works down the form.
  return Object.keys(OPENING_REQUIRED_LABELS)
    .filter((key) => key in missing)
    .map((key) => OPENING_REQUIRED_LABELS[key]);
};

/** How many required Opening fields are still blank — the tab's red count. */
export const openingMissingCount = (opening?: BiodiversityOpening | null): number =>
  opening ? Object.keys(openingRequiredErrors(opening)).length : OPENING_REQUIRED_COUNT;

/**
 * Opening info: `empty` until something has been saved, then either the outstanding count (red) or
 * complete (green check).
 *
 * The count is held back on an untouched tab on purpose. A brand-new checklist owes every required
 * field, and greeting it with a red 6 reads as a fault rather than as work not yet started — the
 * empty outline says the same thing without the alarm. Once the evaluator has saved, the count is
 * information they asked for.
 */
export const openingStatus = (opening?: BiodiversityOpening | null): TabStatus => {
  if (!openingTouched(opening)) return 'empty';
  return openingMissingCount(opening) === 0 ? 'complete' : 'errors';
};

/** Stratum types whose mapped size is capped by the NAR. */
const NAR_CAPPED_TYPES = new Set(['CC', 'DO', 'DR', 'DT', 'DW']);

/**
 * The size a stratum contributes to the combined-size rule: the mapped size when the stratum is
 * map-consistent, the estimated size otherwise (proc: `decode(stratum_consistent_map_ind, 'Y',
 * stratum_size, 'N'/'M', stratum_estimated_size)`).
 */
const effectiveSize = (stratum: BioStratum): number | undefined =>
  stratum.consistentMapInd === 'Y' ? num(stratum.size) : num(stratum.estimatedSize);

/** How a stratum is named in the banner: its number if it has one, else its position. */
const stratumLabel = (stratum: BioStratum, index: number): string =>
  `Stratum ${stratum.stratumNumber?.trim() || index + 1}`;

/**
 * Everything still outstanding on the Stratum summary tab, one line per rule, ready to count and to
 * list in the banner.
 *
 * Two sources, deliberately mixed: the rules
 * {@code FREP_TOMBSTONE.validate_biodiversity_chklst} blocks submit on (plot count vs actual plots,
 * combined size against the block's area cap, mapped size against the NAR), and the fields the tab
 * marks required but no longer refuses to save (stratum number, stratum type, the size). To an
 * evaluator both are just work still owed.
 */
export const stratumOutstanding = (
  bundles: StratumBundle[],
  opening?: BiodiversityOpening | null,
): string[] => {
  // An empty tab is not a quiet tab — `frep.submit.biodiversity.stratum.mandatory` blocks submit on
  // it. The tab still *reads* as untouched (see stratumStatus); the item is here so the submit
  // pre-flight can say what is wrong.
  if (bundles.length === 0) return ['No strata have been added — at least one is required'];
  const items: string[] = [];

  // Cap = the greater of the opening's gross area and the FREP override, so an override *below* the
  // gross area does not tighten the limit (proc: NVL(override,0) < gross => use gross).
  const gross = num(opening?.grossArea);
  const override = num(opening?.frepWtpOverride);
  const cap = Math.max(gross ?? 0, override ?? 0) || undefined;
  const nar = num(opening?.netArea);

  const total = bundles.reduce<number>((sum, b) => sum + (effectiveSize(b.stratum) ?? 0), 0);
  if (cap != null && total > cap) {
    items.push(`Combined stratum size (${total} ha) is over the ${cap} ha limit for this block`);
  }

  bundles.forEach(({ stratum, plots }, index) => {
    const label = stratumLabel(stratum, index);
    if (!has(stratum.stratumNumber)) items.push(`${label} — missing Stratum number`);
    if (!has(stratum.strataTypeCode)) items.push(`${label} — missing Stratum type`);
    if (stratum.consistentMapInd === 'Y' && !has(stratum.size)) {
      items.push(`${label} — missing Mapped size`);
    }
    if (
      (stratum.consistentMapInd === 'N' || stratum.consistentMapInd === 'M') &&
      !has(stratum.estimatedSize)
    ) {
      items.push(`${label} — missing Estimated size`);
    }

    const mapped = num(stratum.size);
    if (
      stratum.strataTypeCode != null &&
      NAR_CAPPED_TYPES.has(stratum.strataTypeCode) &&
      nar != null &&
      mapped != null &&
      mapped > nar
    ) {
      items.push(`${label} — mapped size (${mapped} ha) is over the NAR (${nar} ha)`);
    }

    const declared = num(stratum.plotCount);
    if (declared == null) {
      items.push(`${label} — missing “# of plots in stratum”`);
    } else if (declared !== plots.length) {
      items.push(
        `${label} — “# of plots in stratum” says ${declared}, but ${plots.length} ` +
          `${plots.length === 1 ? 'plot exists' : 'plots exist'}`,
      );
    }
  });

  return items;
};

/**
 * Stratum summary: `empty` until the tab holds a stratum, then the outstanding count or complete.
 * Held back on an untouched tab for the same reason as Opening info — see {@link openingStatus}.
 */
export const stratumStatus = (
  bundles: StratumBundle[],
  opening?: BiodiversityOpening | null,
): TabStatus => {
  if (bundles.length === 0) return 'empty';
  return stratumOutstanding(bundles, opening).length === 0 ? 'complete' : 'errors';
};

/** A plot counts toward the "at least one plot needs UTM" rule. */
const hasUtm = (plot: BioPlot): boolean =>
  plot.utmSignal !== 'N' && has(plot.utmZone) && has(plot.utmEasting) && has(plot.utmNorthing);

/**
 * The rules that apply only once a plot records a full-count area: its area has to stay under the
 * stratum size, and only the first such plot in a stratum may record trees. Split out of
 * {@link plotItems} to keep each readable on its own.
 */
const fullCountItems = (
  plot: BioPlot,
  stratum: BioStratum,
  label: string,
  isFirstFullCount: boolean,
  standRows: number,
): string[] => {
  if (!has(plot.fullCountArea)) return [];
  if (!isFirstFullCount) {
    return standRows > 0
      ? [`${label} — only the first full-count plot in a stratum may record trees`]
      : [];
  }
  const area = num(plot.fullCountArea);
  const size = num(stratum.size);
  return area != null && size != null && area > size
    ? [`${label} — full-count area (${area} ha) is not under the stratum size (${size} ha)`]
    : [];
};

/** Rules owed by one plot, named for the banner. */
const plotItems = (
  plot: BioPlot,
  stratum: BioStratum,
  label: string,
  isFirstFullCount: boolean,
) => {
  const items: string[] = [];
  const stand = plot.standTable ?? [];
  const cwd = plot.cwdTable ?? [];

  if (!has(plot.firstLegTransect) || !has(plot.secondLegTransect)) {
    items.push(`${label} — missing Bearing 1st leg / 2nd leg`);
  }
  if (!has(plot.basalAreaFactor) && !has(plot.fixedAreaRadius) && !has(plot.fullCountArea)) {
    items.push(`${label} — no BAF, fixed-area radius or full-count area entered`);
  }
  if (plot.treeIndicator === 'Y' && stand.length === 0) {
    items.push(`${label} — “Trees exist” is checked but the stand table is empty`);
  }
  if (plot.cwdTransectIndicator === 'Y' && cwd.length === 0) {
    items.push(`${label} — “CWD in transect” is checked but the CWD table is empty`);
  }
  if (stratum.harvestAreaCode === 'HNR' && stand.length > 0) {
    items.push(`${label} — has stand-table rows, but its stratum has no retention`);
  }
  items.push(...fullCountItems(plot, stratum, label, isFirstFullCount, stand.length));
  return items;
};

/**
 * Everything still outstanding on the Plots tab.
 *
 * One deliberate difference from the proc: it skips a stratum's plot rules entirely when that
 * stratum's plot count doesn't match, so plot problems stay hidden until the counts are fixed. Every
 * plot is checked here regardless — a count that goes quiet because of an unrelated mismatch on
 * another tab would be worse than useless. The mismatch itself is listed on Stratum summary, which
 * is where the submit error names it.
 */
export const plotsOutstanding = (bundles: StratumBundle[]): string[] => {
  const plots = bundles.flatMap((bundle) => bundle.plots);
  const items: string[] = [];
  if (!plots.some(hasUtm)) {
    // The submit rule is checklist-wide: one plot with coordinates is enough, and every other plot
    // may be recorded with "No UTM signal available" ticked. So this is counted once for the tab
    // rather than once per plot without coordinates.
    items.push('No plot has UTM coordinates — one plot needs Zone, Easting and Northing');
  }

  bundles.forEach(({ stratum, plots: stratumPlots }) => {
    // "First" = first by plot id, matching the proc's `ORDER BY biodiversity_plot_id ASC`.
    const ordered = [...stratumPlots].sort((a, b) => (num(a.plotId) ?? 0) - (num(b.plotId) ?? 0));
    const firstFullCount = ordered.find((plot) => has(plot.fullCountArea));
    ordered.forEach((plot, index) => {
      const label = `Plot ${plot.plotNumber?.trim() || index + 1} (${stratumLabel(stratum, index)})`;
      items.push(...plotItems(plot, stratum, label, plot === firstFullCount));
    });
  });

  return items;
};

/** Plots: `empty` until the tab holds a plot, then the outstanding count or complete. */
export const plotsStatus = (bundles: StratumBundle[]): TabStatus => {
  if (bundles.every((bundle) => bundle.plots.length === 0)) return 'empty';
  return plotsOutstanding(bundles).length === 0 ? 'complete' : 'errors';
};

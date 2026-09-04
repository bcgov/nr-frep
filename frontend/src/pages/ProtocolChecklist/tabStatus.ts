import {
  OPENING_REQUIRED_LABELS,
  OPENING_REQUIRED_SECTIONS,
  openingFormatErrors,
  openingRequiredErrors,
} from './openingValidation';
import { plotHeaderErrors } from './plotValidation';
import { STRATUM_TEXT_LIMITS } from './stratumLimits';

import type { BiodiversityOpening, BioPlot, BioStratum } from '@/types/protocolChecklist';

import { addTextLimitErrors } from '@/utils/textLimits';

/**
 * Per-tab completion state for the checklist tab strip.
 *
 * The rules below mirror `FREP_TOMBSTONE.validate_biodiversity_chklst` — the proc that actually
 * blocks submit — so a tab only shows `complete` when it would not contribute a submit error. They
 * are deliberately a *read-only mirror*: the proc stays authoritative, and a drift between the two
 * shows up as a count that disagrees with the submit panel, never as a blocked save.
 *
 * Counts are shown from the first render, on a brand-new checklist as readily as on one part-way
 * through. An earlier revision held them back until a tab had been saved, on the reasoning that
 * greeting a fresh checklist with a full count reads as a fault rather than as work not yet begun.
 * That was reversed deliberately: the count is the evaluator's map of what the checklist still owes,
 * and it is least useful precisely when they have done the least. `empty` therefore no longer means
 * "not started" — it means "not known yet", i.e. the read has not landed.
 *
 * A tab's count covers both kinds of rule: the ones the tab itself enforces when saving, and the
 * ones the proc enforces at submit. The two overlap but neither contains the other — a tab can save
 * happily with gaps that block submit, and can hold a malformed value that blocks the save while the
 * proc never sees it. Counting only one kind left the number disagreeing with what the evaluator
 * was actually stopped by.
 *
 * See slr-submit-validation-rules.local.md for the same rules in prose.
 */
export type TabStatus = 'none' | 'empty' | 'complete' | 'errors';

/**
 * One outstanding item, optionally attributed to the record it belongs to.
 *
 * The attribution is carried as its own field rather than baked into `text` because the panel
 * groups by it — parsing it back out of a sentence would misfire on the rules whose own wording
 * contains a dash ("No strata have been added — at least one is required").
 */
export type OutstandingItem = { group?: string; text: string };

/** Outstanding items collected under one heading, in the order the rules produced them. */
export type OutstandingGroup = { title?: string; items: string[] };

/**
 * How a required field reads in the outstanding list: "Rating, in the Evaluator opinion section".
 *
 * Naming the section is the point. These forms are long and sectioned, and a bare field label left
 * the reader scrolling to find out where — so the label says what is wanted and the section says
 * where to go. A field that sits outside every section keeps its label alone rather than being
 * given a section it is not in.
 *
 * A trailing question mark is dropped. Several labels are worded as questions because that is how
 * the form asks them ("Invasive plant species present?"), but here the label is the subject of a
 * sentence rather than a question being put to the reader, and leaving it produced "…present?, in
 * the Invasive plants section".
 */
export const inFormSection = (label: string, section?: string): string => {
  const name = label.replace(/\?$/, '');
  return section ? `${name}, in the ${section} section` : name;
};

/** The flat, prefixed strings the submit pre-flight and the server-error panel still speak in. */
export const flattenOutstanding = (items: OutstandingItem[]): string[] =>
  items.map((item) => (item.group ? `${item.group} — ${item.text}` : item.text));

/**
 * Collapse items into display groups, preserving rule order and merging runs that share a heading.
 *
 * Runs rather than a map on purpose: the rules already emit one record at a time, so a run is the
 * natural unit, and an item that reappears under an earlier heading keeps its place in the list
 * instead of jumping backwards.
 */
export const groupOutstanding = (items: OutstandingItem[]): OutstandingGroup[] => {
  const groups: OutstandingGroup[] = [];
  items.forEach((item) => {
    const last = groups.at(-1);
    if (last && last.title === item.group) last.items.push(item.text);
    else groups.push({ title: item.group, items: [item.text] });
  });
  return groups;
};

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
export const openingOutstandingItems = (
  opening?: BiodiversityOpening | null,
): OutstandingItem[] => {
  const missing = opening ? openingRequiredErrors(opening) : openingRequiredErrors({});
  // Listed in tab order, so the list reads top-to-bottom the way the user works down the form.
  // Ungrouped: the Opening tab is one form, so every item already belongs to the same record.
  const items = Object.keys(OPENING_REQUIRED_LABELS)
    .filter((key) => key in missing)
    .map((key) => ({
      text: inFormSection(OPENING_REQUIRED_LABELS[key], OPENING_REQUIRED_SECTIONS[key]),
    }));
  // Free text past its column limit blocks the save and never reaches the proc, so nothing else
  // would report it. Named by field, since the message alone does not say which one is over.
  const overLimit = opening ? openingFormatErrors(opening) : {};
  Object.entries(overLimit).forEach(([key, message]) => {
    items.push({
      text: `${inFormSection(OPENING_REQUIRED_LABELS[key] ?? key, OPENING_REQUIRED_SECTIONS[key])} — ${message}`,
    });
  });
  return items;
};

export const openingOutstanding = (opening?: BiodiversityOpening | null): string[] =>
  flattenOutstanding(openingOutstandingItems(opening));

/** How many required Opening fields are still blank — the tab's red count. */
export const openingMissingCount = (opening?: BiodiversityOpening | null): number =>
  opening ? Object.keys(openingRequiredErrors(opening)).length : OPENING_REQUIRED_COUNT;

/** Opening info: the outstanding count, or complete once nothing is owed. */
export const openingStatus = (opening?: BiodiversityOpening | null): TabStatus =>
  openingMissingCount(opening) === 0 ? 'complete' : 'errors';

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
export const stratumOutstandingItems = (
  bundles: StratumBundle[],
  opening?: BiodiversityOpening | null,
): OutstandingItem[] => {
  // An empty tab is not a quiet tab — `frep.submit.biodiversity.stratum.mandatory` blocks submit on
  // it. The tab still *reads* as untouched (see stratumStatus); the item is here so the submit
  // pre-flight can say what is wrong.
  if (bundles.length === 0) {
    return [{ text: 'No strata have been added — at least one is required' }];
  }
  const items: OutstandingItem[] = [];

  // Cap = the greater of the opening's gross area and the FREP override, so an override *below* the
  // gross area does not tighten the limit (proc: NVL(override,0) < gross => use gross).
  const gross = num(opening?.grossArea);
  const override = num(opening?.frepWtpOverride);
  const cap = Math.max(gross ?? 0, override ?? 0) || undefined;
  const nar = num(opening?.netArea);

  const total = bundles.reduce<number>((sum, b) => sum + (effectiveSize(b.stratum) ?? 0), 0);
  if (cap != null && total > cap) {
    items.push({
      text: `Combined stratum size (${total} ha) is over the ${cap} ha limit for this block`,
    });
  }

  bundles.forEach(({ stratum, plots }, index) => {
    const group = stratumLabel(stratum, index);
    if (!has(stratum.stratumNumber)) {
      items.push({ group, text: inFormSection('Stratum number', 'Stratum summary') });
    }
    if (!has(stratum.strataTypeCode)) {
      items.push({ group, text: inFormSection('Stratum type', 'Stratum summary') });
    }
    if (stratum.consistentMapInd === 'Y' && !has(stratum.size)) {
      items.push({ group, text: inFormSection('Mapped size', 'Map consistency') });
    }
    if (
      (stratum.consistentMapInd === 'N' || stratum.consistentMapInd === 'M') &&
      !has(stratum.estimatedSize)
    ) {
      items.push({ group, text: inFormSection('Estimated size', 'Map consistency') });
    }

    const mapped = num(stratum.size);
    if (
      stratum.strataTypeCode != null &&
      NAR_CAPPED_TYPES.has(stratum.strataTypeCode) &&
      nar != null &&
      mapped != null &&
      mapped > nar
    ) {
      items.push({
        group,
        text: inFormSection(
          `Mapped size (${mapped} ha) is over the NAR (${nar} ha)`,
          'Map consistency',
        ),
      });
    }

    // Over-limit free text: blocks the save, invisible to the proc.
    const overLimit: Record<string, string> = {};
    addTextLimitErrors(overLimit, stratum as Record<string, unknown>, STRATUM_TEXT_LIMITS);
    Object.entries(overLimit).forEach(([key, message]) =>
      items.push({ group, text: `${key}: ${message}` }),
    );

    const declared = num(stratum.plotCount);
    if (declared == null) {
      items.push({ group, text: inFormSection('# of plots in stratum', 'Stratum summary') });
    } else if (declared !== plots.length) {
      items.push({
        group,
        text: inFormSection(
          `“# of plots in stratum” says ${declared}, but ${plots.length} ` +
            `${plots.length === 1 ? 'plot exists' : 'plots exist'}`,
          'Stratum summary',
        ),
      });
    }
  });

  return items;
};

export const stratumOutstanding = (
  bundles: StratumBundle[],
  opening?: BiodiversityOpening | null,
): string[] => flattenOutstanding(stratumOutstandingItems(bundles, opening));

/** Stratum summary: the outstanding count, or complete once nothing is owed. */
export const stratumStatus = (
  bundles: StratumBundle[],
  opening?: BiodiversityOpening | null,
): TabStatus => (stratumOutstandingItems(bundles, opening).length === 0 ? 'complete' : 'errors');

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
  isFirstFullCount: boolean,
  standRows: number,
): string[] => {
  if (!has(plot.fullCountArea)) return [];
  if (!isFirstFullCount) {
    return standRows > 0 ? ['only the first full-count plot in a stratum may record trees'] : [];
  }
  const area = num(plot.fullCountArea);
  const size = num(stratum.size);
  return area != null && size != null && area > size
    ? [`full-count area (${area} ha) is not under the stratum size (${size} ha)`]
    : [];
};

/**
 * Rules owed by one plot, unprefixed — the caller attributes them to the plot.
 *
 * Two sources. {@link plotHeaderErrors} is the tab's own save validation, which is field-precise and
 * catches what the proc's checklist-wide rules cannot: this plot's own UTM, its plot number and its
 * uniqueness within the stratum, and any value entered in the wrong shape or out of range. The rules
 * after it are the submit mirror's, and are the ones only it can see — they read the plot's tables,
 * its stratum, or its position among the stratum's other plots.
 *
 * The save rules lead, and the two submit rules they already cover — bearings present, and a
 * measurement method entered — are deliberately not repeated below; `bearingErrors` and
 * `measurementMethodErrors` say the same thing about the same fields, and more precisely.
 */
const plotItems = (
  plot: BioPlot,
  stratum: BioStratum,
  isFirstFullCount: boolean,
  otherPlotNumbers: readonly string[],
): string[] => {
  const stand = plot.standTable ?? [];
  const cwd = plot.cwdTable ?? [];
  const items: string[] = Object.values(
    plotHeaderErrors(plot, stratum.strataTypeCode ?? '', otherPlotNumbers),
  );

  if (plot.treeIndicator === 'Y' && stand.length === 0) {
    items.push('“Trees exist” is checked but the stand table is empty');
  }
  if (plot.cwdTransectIndicator === 'Y' && cwd.length === 0) {
    items.push('“CWD in transect” is checked but the CWD table is empty');
  }
  if (stratum.harvestAreaCode === 'HNR' && stand.length > 0) {
    items.push('has stand-table rows, but its stratum has no retention');
  }
  items.push(...fullCountItems(plot, stratum, isFirstFullCount, stand.length));
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
export const plotsOutstandingItems = (bundles: StratumBundle[]): OutstandingItem[] => {
  const plots = bundles.flatMap((bundle) => bundle.plots);
  const items: OutstandingItem[] = [];
  if (!plots.some(hasUtm)) {
    // The submit rule is checklist-wide: one plot with coordinates is enough, and every other plot
    // may be recorded with "No UTM signal available" ticked. So this is counted once for the tab
    // rather than once per plot without coordinates.
    items.push({
      text: 'No plot has UTM coordinates — one plot needs Zone, Easting and Northing',
    });
  }

  bundles.forEach(({ stratum, plots: stratumPlots }) => {
    // "First" = first by plot id, matching the proc's `ORDER BY biodiversity_plot_id ASC`.
    const ordered = [...stratumPlots].sort((a, b) => (num(a.plotId) ?? 0) - (num(b.plotId) ?? 0));
    const firstFullCount = ordered.find((plot) => has(plot.fullCountArea));
    ordered.forEach((plot, index) => {
      const group = `Plot ${plot.plotNumber?.trim() || index + 1} (${stratumLabel(stratum, index)})`;
      // The other plots' numbers, so the uniqueness rule sees the same field the save does.
      const others = ordered
        .filter((other) => other !== plot)
        .map((other) => other.plotNumber ?? '');
      items.push(
        ...plotItems(plot, stratum, plot === firstFullCount, others).map((text) => ({
          group,
          text,
        })),
      );
    });
  });

  return items;
};

export const plotsOutstanding = (bundles: StratumBundle[]): string[] =>
  flattenOutstanding(plotsOutstandingItems(bundles));

/** Plots: the outstanding count, or complete once nothing is owed. */
export const plotsStatus = (bundles: StratumBundle[]): TabStatus =>
  plotsOutstandingItems(bundles).length === 0 ? 'complete' : 'errors';

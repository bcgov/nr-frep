import { useCallback, useEffect, useState } from 'react';

import {
  flattenOutstanding,
  openingMissingCount,
  openingOutstandingItems,
  openingStatus,
  plotsOutstandingItems,
  plotsStatus,
  stratumOutstandingItems,
  stratumStatus,
} from './tabStatus';

import type { OutstandingItem, StratumBundle, TabStatus } from './tabStatus';

import API from '@/services/APIs';

/**
 * Completion state for every tab in the checklist strip, keyed by the backend section id.
 *
 * Notes and Attachments carry no rules of either kind — nothing on them is required to save, and
 * nothing on them blocks submit — so they report `none` and draw no indicator at all. A tab that can
 * never be outstanding has no state worth a glyph.
 */
const OPTIONAL_SECTIONS: Record<string, TabStatus> = {
  notes: 'none',
  attachments: 'none',
};

export type TabStatusSnapshot = {
  statuses: Record<string, TabStatus>;
  counts: Record<string, number>;
  /** Grouped for the per-tab panel — stratum and plot rules carry their record as the heading. */
  items: Record<string, OutstandingItem[]>;
  /** The same items flattened, for the submit pre-flight. */
  outstanding: Record<string, string[]>;
};

const EMPTY_SNAPSHOT: TabStatusSnapshot = {
  statuses: {},
  counts: {},
  items: {},
  outstanding: {},
};

/**
 * Read the checklist and derive every tab's state from it.
 *
 * Cost note: mirroring the submit rules faithfully means reading every plot in full (the UTM,
 * bearing, stand-table and CWD rules all live on the plot record, not on its list row), so this
 * costs 1 + 2·strata + plots requests per run. That is fine for a POC on real-sized checklists — a
 * handful of strata with a handful of plots each — but it is the first thing to revisit if the
 * pattern ships: the natural fix is a small backend endpoint returning the statuses directly, which
 * would also let the proc's own rules be the single source of truth.
 */
const readChecklist = async (checklistId: string): Promise<TabStatusSnapshot> => {
  const opening = await API.protocolChecklist.getBiodiversityOpening(checklistId);
  const rows = await API.protocolChecklist.listBioStrata(checklistId);

  const bundles: StratumBundle[] = await Promise.all(
    rows
      .filter((row) => row.stratumId)
      .map(async (row): Promise<StratumBundle> => {
        const stratumId = row.stratumId as string;
        const [stratum, plotRows] = await Promise.all([
          API.protocolChecklist.getBioStratum(stratumId),
          API.protocolChecklist.listBioPlots(stratumId),
        ]);
        const plots = await Promise.all(
          plotRows
            .filter((plot) => plot.plotId)
            .map((plot) => API.protocolChecklist.getBioPlot(plot.plotId as string)),
        );
        return { stratum, plots };
      }),
  );

  const openingItems = openingOutstandingItems(opening);
  const stratumItems = stratumOutstandingItems(bundles, opening);
  const plotItems = plotsOutstandingItems(bundles);

  return {
    statuses: {
      ...OPTIONAL_SECTIONS,
      opening: openingStatus(opening),
      stratum: stratumStatus(bundles, opening),
      plots: plotsStatus(bundles),
    },
    counts: {
      opening: openingMissingCount(opening),
      stratum: stratumItems.length,
      plots: plotItems.length,
    },
    items: { opening: openingItems, stratum: stratumItems, plots: plotItems },
    outstanding: {
      opening: flattenOutstanding(openingItems),
      stratum: flattenOutstanding(stratumItems),
      plots: flattenOutstanding(plotItems),
    },
  };
};

/**
 * Per-tab completion state for the checklist strip, refreshed on demand.
 *
 * `evaluate()` is the same read, awaited and returned rather than just stored — Submit uses it to
 * pre-flight the whole checklist against current data instead of whatever the dots last cached.
 */
export const useTabStatuses = (checklistId: string, enabled: boolean) => {
  const [snapshot, setSnapshot] = useState<TabStatusSnapshot>(EMPTY_SNAPSHOT);
  const [version, setVersion] = useState(0);

  /** Re-read after a save so the dots move without a page reload. */
  const refresh = useCallback(() => setVersion((v) => v + 1), []);

  /** Re-read now and hand back the result, for callers that must act on it. */
  const evaluate = useCallback(async (): Promise<TabStatusSnapshot> => {
    const next = await readChecklist(checklistId);
    setSnapshot(next);
    return next;
  }, [checklistId]);

  useEffect(() => {
    if (!checklistId || !enabled) return;

    let cancelled = false;
    // A failed read leaves the previous dots in place rather than flashing everything back to
    // "empty" — the tabs themselves still work, and the page already surfaces load errors.
    void readChecklist(checklistId)
      .then((next) => {
        if (!cancelled) setSnapshot(next);
      })
      .catch(() => undefined);

    return () => {
      cancelled = true;
    };
  }, [checklistId, enabled, version]);

  return { ...snapshot, refresh, evaluate };
};

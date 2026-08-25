import { bioDb, type BioReferenceKey } from '@/services/offline/bioDb';

import type { ConfigurationService } from '@/services/configuration.service';
import type { BecRow, CodeOption } from '@/types/configuration';

/**
 * Reference data an SLR checklist needs on a device with no connectivity.
 *
 * CHR needs none of this — its code lists are hardcoded in the frontend, ported from legacy. The Bio
 * views read five lists from the server, so an offline copy is unusable without them: no species
 * dropdown, no decay classes, no BEC picker.
 *
 * **Cached app-wide, refreshed at take-offline.** These are static tables keyed to nothing, so a
 * per-checklist copy would store them once per checkout for no benefit. Refreshing only at
 * take-offline means one network cost at the moment the user is definitionally online, and no
 * separate sync path to maintain.
 */

/** The four code lists the Bio plot views read (`BioPlotsView.tsx:226-229`). */
const CODE_LISTS = ['species', 'wildlifeTreeDecay', 'cwdDecay', 'strataTypes'] as const;

/**
 * The BEC catalogue.
 *
 * Sized before deciding to ship it: eight short columns bounded by the DDL, so ~240 bytes/row worst
 * case — around 2.4 MB of JSON (~200 KB gzipped) at the high end of plausible row counts, and under
 * 12 MB even at 50k rows. A *single* SLR attachment may be 15 MB, so the whole catalogue costs less
 * than one photo of a stump. Degrading the field to free-text would have traded a working feature
 * for nothing.
 *
 * The parameterless search returns the entire set, so no new endpoint was needed.
 */
const BEC_KEY: BioReferenceKey = 'bec';

export const bioReferenceCache = {
  /**
   * Pull every reference list and store it. Call at take-offline, while the device is definitely
   * online.
   *
   * Sequential rather than parallel: on a field connection one request at a time fares better than
   * five, and the BEC catalogue is much the largest.
   */
  async refresh(client: ConfigurationService): Promise<void> {
    const [species, wildlifeTreeDecay, cwdDecay, strataTypes] = [
      await client.getSpecies(),
      await client.getWildlifeTreeDecay(),
      await client.getCwdDecay(),
      await client.getStrataTypes(),
    ];
    // Blank criteria match everything, so this is the whole catalogue.
    const bec = await client.searchBec({});

    const refreshedAt = Date.now();
    await bioDb.bioReference.bulkPut([
      { key: 'species', rows: species, refreshedAt },
      { key: 'wildlifeTreeDecay', rows: wildlifeTreeDecay, refreshedAt },
      { key: 'cwdDecay', rows: cwdDecay, refreshedAt },
      { key: 'strataTypes', rows: strataTypes, refreshedAt },
      { key: BEC_KEY, rows: bec, refreshedAt },
    ]);
  },

  /** A cached code list, or undefined when it has never been pulled. */
  async codeList(key: (typeof CODE_LISTS)[number]): Promise<CodeOption[] | undefined> {
    const row = await bioDb.bioReference.get(key);
    return row?.rows as CodeOption[] | undefined;
  },

  /** The cached BEC catalogue, or undefined when it has never been pulled. */
  async bec(): Promise<BecRow[] | undefined> {
    const row = await bioDb.bioReference.get(BEC_KEY);
    return row?.rows as BecRow[] | undefined;
  },

  /** When the cache was last refreshed, for a "reference data from …" hint. */
  async refreshedAt(): Promise<number | undefined> {
    return (await bioDb.bioReference.get(BEC_KEY))?.refreshedAt;
  },

  async clear(): Promise<void> {
    await bioDb.bioReference.clear();
  },
};

/**
 * Filter the cached catalogue the way `FREP_52_BGC_SEARCH` does: each criterion is an
 * `UPPER(col) LIKE '%x%'` contains-match, and a blank one matches everything.
 *
 * Reimplemented rather than approximated — a picker that matched differently offline than online
 * would have field staff selecting codes they then can't reproduce at their desk.
 */
export const filterBec = (
  rows: BecRow[],
  criteria: Partial<Record<string, string>>,
): BecRow[] => {
  const matches = (value: string | undefined, needle: string | undefined): boolean => {
    if (!needle || !needle.trim()) return true;
    return (value ?? '').toUpperCase().includes(needle.trim().toUpperCase());
  };
  return rows.filter(
    (row) =>
      matches(row.bgcZoneCode, criteria.bgcZoneCode)
      && matches(row.bgcSubzoneCode, criteria.bgcSubzoneCode)
      && matches(row.bgcVariant, criteria.bgcVariant)
      && matches(row.bgcPhase, criteria.bgcPhase)
      && matches(row.becSiteSeriesCd, criteria.becSiteSeriesCd)
      && matches(row.siteSeriesPhaseCd, criteria.siteSeriesPhaseCd)
      && matches(row.seral, criteria.seral),
  );
};

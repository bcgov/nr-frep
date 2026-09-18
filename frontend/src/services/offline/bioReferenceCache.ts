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

/**
 * The four code lists the Bio plot views read (`BioPlotsView.tsx:226-229`).
 *
 * A type, not a `const` array: it is only ever read in a type position, so the runtime value was
 * dead weight — `@typescript-eslint/no-unused-vars` flags exactly that ("assigned a value but only
 * used as a type"). `refresh` below calls a different client method per list, so there is nothing
 * to iterate it for.
 */
type CodeListKey = 'species' | 'wildlifeTreeDecay' | 'cwdDecay' | 'strataTypes';

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
  async codeList(key: CodeListKey): Promise<CodeOption[] | undefined> {
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
/**
 * Search-criteria name → the `BecRow` field it filters on.
 *
 * **The criteria names are the wire contract, not the row's field names.** The picker builds
 * `{zone, subzone, variant, phase, siteSeries, siteSeriesPhase, seral}` and the backend declares
 * exactly those `@RequestParam`s (`ConfigurationApiEndpoint#searchBec`), mapping them to columns on
 * its own side. The row shape is the *response*, and it uses different names.
 *
 * This filter used to read the criteria object with the ROW's names — `criteria.bgcZoneCode`,
 * `criteria.becSiteSeriesCd` and so on. None of those keys is ever present, so every needle read as
 * `undefined`, the empty-needle short-circuit returned `true`, and the offline search returned the
 * ENTIRE catalogue no matter what was typed. Only `seral` worked, being the one name spelled the
 * same on both sides — which is why it looked like a search that ignored its inputs rather than one
 * that was plainly broken.
 */
export const BEC_CRITERIA_FIELD: Record<string, keyof BecRow> = {
  zone: 'bgcZoneCode',
  subzone: 'bgcSubzoneCode',
  variant: 'bgcVariant',
  phase: 'bgcPhase',
  siteSeries: 'becSiteSeriesCd',
  siteSeriesPhase: 'siteSeriesPhaseCd',
  seral: 'seral',
};

/**
 * Filter the cached BEC catalogue the way the server would.
 *
 * Mirrors `FREP_52_BGC_SEARCH`: every criterion is an `UPPER(col) LIKE '%x%'` contains-match and a
 * blank one matches everything. A picker that matched differently offline would have field staff
 * selecting codes they cannot reproduce at their desk.
 */
export const filterBec = (
  rows: BecRow[],
  criteria: Partial<Record<string, string>>,
): BecRow[] => {
  const needles = Object.entries(BEC_CRITERIA_FIELD)
    .map(([key, field]) => [field, criteria[key]?.trim()] as const)
    .filter(([, needle]) => !!needle)
    .map(([field, needle]) => [field, (needle as string).toUpperCase()] as const);

  if (needles.length === 0) return rows;
  return rows.filter((row) =>
    needles.every(([field, needle]) => (row[field] ?? '').toUpperCase().includes(needle)));
};

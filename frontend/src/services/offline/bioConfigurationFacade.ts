import { bioReferenceCache, filterBec } from '@/services/offline/bioReferenceCache';

import type { ConfigurationService } from '@/services/configuration.service';
import type { BecRow, CodeOption } from '@/types/configuration';

/**
 * Offline routing for the reference data the Bio views read.
 *
 * **Network first, cache as the fallback — not the other way round.** These calls carry no checklist
 * id, so the facade cannot ask "is *this* checklist checked out?" the way the protocol facade does.
 * Trying the server first keeps an online user on live codes (a cache refreshed weeks ago at the last
 * take-offline would otherwise quietly become the source of truth), and falling back on failure is
 * exactly what a device that has lost connectivity needs.
 *
 * When the browser already knows it is offline, the network attempt is skipped rather than left to
 * time out — a field device on no signal shouldn't wait for five failures to render a dropdown.
 */

const isOffline = (): boolean =>
  typeof navigator !== 'undefined' && navigator.onLine === false;

/**
 * Serve from the network, falling back to the cache.
 *
 * A cache miss rethrows the original network error rather than returning an empty list: an empty
 * dropdown looks like "no species exist" and would have the evaluator conclude the data is wrong,
 * where an error at least says the app could not load it.
 */
const preferNetwork = async <T>(
  fromNetwork: () => Promise<T>,
  fromCache: () => Promise<T | undefined>,
): Promise<T> => {
  if (isOffline()) {
    const cached = await fromCache();
    if (cached !== undefined) return cached;
    throw new Error(
      "This reference data isn't available offline. Take a checklist offline while connected first.",
    );
  }
  try {
    return await fromNetwork();
  } catch (err) {
    const cached = await fromCache();
    if (cached !== undefined) return cached;
    throw err;
  }
};

/** Wrap the configuration client so the Bio views keep working with no connectivity. */
export const withBioReferenceCache = (client: ConfigurationService): ConfigurationService => {
  const facade = {
    getSpecies: (): Promise<CodeOption[]> =>
      preferNetwork(() => client.getSpecies(), () => bioReferenceCache.codeList('species')),

    getWildlifeTreeDecay: (): Promise<CodeOption[]> =>
      preferNetwork(
        () => client.getWildlifeTreeDecay(),
        () => bioReferenceCache.codeList('wildlifeTreeDecay'),
      ),

    getCwdDecay: (): Promise<CodeOption[]> =>
      preferNetwork(() => client.getCwdDecay(), () => bioReferenceCache.codeList('cwdDecay')),

    getStrataTypes: (): Promise<CodeOption[]> =>
      preferNetwork(() => client.getStrataTypes(), () => bioReferenceCache.codeList('strataTypes')),

    /**
     * BEC search. Offline this filters the cached catalogue locally, reimplementing the proc's
     * contains-match semantics so the picker behaves identically either way.
     */
    searchBec: (criteria: Partial<Record<string, string>>): Promise<BecRow[]> =>
      preferNetwork(
        () => client.searchBec(criteria),
        async () => {
          const rows = await bioReferenceCache.bec();
          return rows ? filterBec(rows, criteria) : undefined;
        },
      ),
  };

  return { ...client, ...facade } as ConfigurationService;
};

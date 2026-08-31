import { useEffect, useState } from 'react';

import type { CodeOption } from '@/types/configuration';

/**
 * A code list fetched once per session and shared by every component that asks for it.
 *
 * The checklist forms mount and unmount their editors constantly — a feature editor is rebuilt each
 * time a different feature is opened — so fetching inside the component would re-request the same
 * unchanging list on every switch. The lists are small, immutable code tables; one request each is
 * enough, and the cache is keyed by name so two components asking for the same list share it.
 *
 * Returns an empty list until the request resolves, and on failure. A dropdown that renders empty
 * is the same failure the SLR views already accept: the page still works, the field just cannot be
 * answered, and the save that follows is validated server-side anyway.
 */
const cache = new Map<string, CodeOption[]>();
const inFlight = new Map<string, Promise<CodeOption[]>>();

export const useCodeList = (key: string, fetcher: () => Promise<CodeOption[]>): CodeOption[] => {
  const [options, setOptions] = useState<CodeOption[]>(() => cache.get(key) ?? []);

  useEffect(() => {
    const cached = cache.get(key);
    if (cached) {
      setOptions(cached);
      return undefined;
    }
    let cancelled = false;
    // Share one request between components that mount together, rather than firing the same call
    // once per subscriber before any of them has resolved.
    let request = inFlight.get(key);
    if (!request) {
      request = fetcher().then((list) => {
        cache.set(key, list);
        return list;
      });
      inFlight.set(key, request);
    }
    request
      .catch(() => [] as CodeOption[])
      .then((list) => {
        if (!cancelled) setOptions(list);
      })
      .finally(() => inFlight.delete(key));
    return () => {
      cancelled = true;
    };
    // `fetcher` is a new closure each render; the key is what identifies the list.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  return options;
};

/** Test seam — drops the cached lists so a spec can serve different options per case. */
export const clearCodeListCache = (): void => {
  cache.clear();
  inFlight.clear();
};

export default useCodeList;

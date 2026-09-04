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

/**
 * Second-level cache, in localStorage, so the lists survive a page load — and a day in the field.
 *
 * The in-memory map above lives only as long as the page. Offline that is not enough: nothing else
 * on a device-local checklist calls the API, so these requests were the only ones going out, and
 * with no network they left every dropdown empty — a feature could not be given a class or an
 * information source. Storing them means a checklist taken offline still has real labels to pick
 * from, using the codes the tables actually hold rather than a copy hardcoded here that would drift.
 *
 * Not IndexedDB: that would need a schema version bump, and these are a handful of small immutable
 * lists, not records.
 */
const STORAGE_PREFIX = 'frep.codeList.';

const readStored = (key: string): CodeOption[] | undefined => {
  try {
    const raw = window.localStorage.getItem(STORAGE_PREFIX + key);
    if (!raw) return undefined;
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) && parsed.length > 0 ? (parsed as CodeOption[]) : undefined;
  } catch {
    // Unreadable or not JSON: treat as absent and let the fetch below replace it.
    return undefined;
  }
};

const writeStored = (key: string, list: CodeOption[]): void => {
  if (list.length === 0) return; // never cache a failed/empty read over a good one
  try {
    window.localStorage.setItem(STORAGE_PREFIX + key, JSON.stringify(list));
  } catch {
    // Private window, blocked storage, quota — the in-memory cache still serves this page.
  }
};

export const useCodeList = (key: string, fetcher: () => Promise<CodeOption[]>): CodeOption[] => {
  const [options, setOptions] = useState<CodeOption[]>(
    () => cache.get(key) ?? readStored(key) ?? [],
  );

  useEffect(() => {
    const cached = cache.get(key);
    if (cached) {
      setOptions(cached);
      return undefined;
    }
    // Show the stored copy straight away, but leave the in-memory cache empty so an online session
    // still refreshes the list once per page load.
    const stored = readStored(key);
    if (stored) setOptions(stored);
    // Offline there is nothing to fetch: the request cannot succeed, and every one of them used to
    // run the session check that redirected the user off the page (see auth/refreshSession).
    if (!navigator.onLine) return undefined;
    let cancelled = false;
    // Share one request between components that mount together, rather than firing the same call
    // once per subscriber before any of them has resolved.
    let request = inFlight.get(key);
    if (!request) {
      request = fetcher().then((list) => {
        cache.set(key, list);
        writeStored(key, list);
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

/**
 * Fetch one list and store it, outside React — for warming the cache before it is needed.
 *
 * Taking a checklist offline is the last moment the device is guaranteed to have a connection, so
 * it is the right moment to make sure the dropdowns it will need are on disk. A list already in the
 * in-memory cache has been stored too, so it is skipped. Failures are swallowed: a warm-up that
 * cannot reach the server must never fail the operation it is riding along with.
 */
export const primeCodeList = async (
  key: string,
  fetcher: () => Promise<CodeOption[]>,
): Promise<void> => {
  if (cache.has(key)) return;
  try {
    const list = await fetcher();
    cache.set(key, list);
    writeStored(key, list);
  } catch {
    // Nothing to store — the dropdown falls back to whatever was already there.
  }
};

/** Test seam — drops the cached lists so a spec can serve different options per case. */
export const clearCodeListCache = (): void => {
  cache.clear();
  inFlight.clear();
  try {
    Object.keys(window.localStorage)
      .filter((k) => k.startsWith(STORAGE_PREFIX))
      .forEach((k) => window.localStorage.removeItem(k));
  } catch {
    // Storage unavailable — nothing stored to clear.
  }
};

export default useCodeList;

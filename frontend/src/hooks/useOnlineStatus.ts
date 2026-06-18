import { useEffect, useRef, useState } from 'react';

/** How long the browser must stay offline before we report it (debounce). */
const OFFLINE_DEBOUNCE_MS = 3000;

/**
 * Tracks browser connectivity via navigator.onLine + the online/offline events.
 *
 * The transition to offline is debounced by {@link OFFLINE_DEBOUNCE_MS}: a brief connectivity drop
 * (which fires an `offline` then `online` in quick succession) won't flip the status, so UI gated
 * on connectivity (e.g. the offline-only dashboard/side nav) doesn't flicker. Reconnecting is
 * reflected immediately, and cancels any pending offline transition.
 */
export const useOnlineStatus = (): boolean => {
  const [online, setOnline] = useState<boolean>(() =>
    typeof navigator === 'undefined' ? true : navigator.onLine,
  );
  const offlineTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const clearOfflineTimer = () => {
      if (offlineTimer.current !== null) {
        clearTimeout(offlineTimer.current);
        offlineTimer.current = null;
      }
    };
    const goOnline = () => {
      clearOfflineTimer();
      setOnline(true);
    };
    const goOffline = () => {
      clearOfflineTimer();
      offlineTimer.current = setTimeout(() => setOnline(false), OFFLINE_DEBOUNCE_MS);
    };
    window.addEventListener('online', goOnline);
    window.addEventListener('offline', goOffline);
    return () => {
      window.removeEventListener('online', goOnline);
      window.removeEventListener('offline', goOffline);
      clearOfflineTimer();
    };
  }, []);

  return online;
};

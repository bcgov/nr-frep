import { useSyncExternalStore } from 'react';

/**
 * Connectivity state, decided by reachability probes rather than by `navigator.onLine` alone.
 *
 * `navigator.onLine` is not a connectivity signal — it means "the browser believes some network
 * interface is up", and it only changes when the OS emits an event the browser happens to catch. It
 * is wrong in both directions, and both were breaking us:
 *
 *  - **False offline.** Chrome on macOS can latch its NetworkChangeNotifier to "no connection"
 *    (a VPN tearing down its utun interface is the usual trigger) and then never fire the `online`
 *    event that would clear it. Every tab reports `false` indefinitely while the network is fine.
 *    Because the app shell is precached by the service worker, the page still loads — so the user
 *    sees a perfectly rendered landing page stuck in offline mode, with the login buttons hidden.
 *    Reloading does not help: the flag lives in the browser process, not the tab.
 *  - **False online.** `navigator.onLine` goes `true` the moment a device associates with any access
 *    point. A captive portal, a tower with no backhaul or a dead VPN all read as "online", so the
 *    app leaves offline mode exactly when field evaluators need the offline CHR editor.
 *
 * So browser events are treated as *hints that something may have changed* — worth spending a probe
 * on — and never as the answer. The published state is the result of the last probe.
 *
 * A single module-level monitor serves every consumer (eight components call this hook), so the app
 * makes one probe per interval, not one per mounted component.
 */

/** Same-origin endpoint the probe hits. Served by Caddy; see `frontend/Caddyfile`. */
const PROBE_URL = '/health';

/**
 * A probe that has not answered in this long counts as a failure. Deliberately short: a probe is a
 * question about reachability, and a request that hangs for seconds has already answered it.
 */
const PROBE_TIMEOUT_MS = 3000;

/**
 * Poll interval while we believe we are OFFLINE. Short, because this is the state where being wrong
 * is most costly (login hidden, online routes withheld) and where no other signal will arrive — the
 * whole point is to recover without the browser event that never came.
 */
const OFFLINE_POLL_MS = 20_000;

/**
 * Heartbeat while we believe we are ONLINE and the tab is in the foreground. Long, because failed
 * requests, browser events and tab focus already cover most transitions; this only catches
 * connectivity dying silently while the user reads a checklist without issuing any request. Two
 * bytes every two minutes is negligible even on a metered field connection.
 */
const ONLINE_POLL_MS = 120_000;

/**
 * A single failed probe does not flip us offline — it schedules a second one this soon. One dropped
 * request should not dump someone into offline mode mid-edit; two in a row is evidence.
 * (Recovery is not symmetric: one success flips us back online immediately.)
 */
const CONFIRM_OFFLINE_DELAY_MS = 1500;

/** Ignore external failure hints that arrive this soon after the last probe started. */
const HINT_THROTTLE_MS = 1000;

const listeners = new Set<() => void>();

let online = typeof navigator === 'undefined' ? true : navigator.onLine;
let consecutiveFailures = 0;
let pollTimer: ReturnType<typeof setTimeout> | null = null;
let probeInFlight = false;
let lastProbeStartedAt = 0;

const canProbe = (): boolean =>
  typeof fetch === 'function' && typeof AbortController === 'function';

/** Foreground check. A backgrounded tab needs no connectivity state, so it gets no probes. */
const isVisible = (): boolean => typeof document === 'undefined' || !document.hidden;

const publish = (next: boolean): void => {
  if (online === next) return;
  online = next;
  listeners.forEach((listener) => listener());
};

const clearPollTimer = (): void => {
  if (pollTimer !== null) {
    clearTimeout(pollTimer);
    pollTimer = null;
  }
};

const scheduleNextPoll = (): void => {
  clearPollTimer();
  if (listeners.size === 0) return;
  // A hidden tab is left alone entirely; the visibilitychange that brings it back triggers a probe.
  if (!isVisible()) return;
  pollTimer = setTimeout(() => void probe(), online ? ONLINE_POLL_MS : OFFLINE_POLL_MS);
};

/**
 * Ask the network a question. Any HTTP response — including a 404 or a 502 — proves the request
 * reached a server and is therefore success: we are testing reachability, not the health of the
 * thing that answered. Only a transport error or a timeout counts as offline.
 *
 * The URL must be one the service worker does not handle. `/config.js` and `/api/v1/*` are both
 * `NetworkFirst` (see the workbox config in `vite.config.ts`), so after their network timeout they
 * resolve from cache — a probe against either would report connectivity that does not exist.
 */
const isReachable = async (): Promise<boolean> => {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), PROBE_TIMEOUT_MS);
  try {
    await fetch(`${PROBE_URL}?_=${Date.now()}`, {
      method: 'GET',
      cache: 'no-store',
      credentials: 'omit',
      signal: controller.signal,
    });
    return true;
  } catch {
    return false;
  } finally {
    clearTimeout(timeout);
  }
};

const probe = async (): Promise<void> => {
  if (probeInFlight || !canProbe()) return;
  probeInFlight = true;
  lastProbeStartedAt = Date.now();
  clearPollTimer();

  const reachable = await isReachable();
  probeInFlight = false;

  if (reachable) {
    consecutiveFailures = 0;
    publish(true);
    scheduleNextPoll();
    return;
  }

  consecutiveFailures += 1;
  if (online && consecutiveFailures < 2) {
    // First failure while we believe we are online: confirm before flipping.
    clearPollTimer();
    pollTimer = setTimeout(() => void probe(), CONFIRM_OFFLINE_DELAY_MS);
    return;
  }

  publish(false);
  scheduleNextPoll();
};

/** A browser hint (connectivity event, tab focus) — reason enough to spend a probe now. */
const probeNow = (): void => {
  clearPollTimer();
  void probe();
};

const handleBrowserOnline = (): void => {
  // The browser says an interface came up. That is not proof of reachability, but it is a good
  // reason to look: reset the failure count so a single success restores us immediately.
  consecutiveFailures = 0;
  probeNow();
};

const handleVisibilityChange = (): void => {
  if (isVisible()) {
    // The classic stuck-flag moment: a laptop waking up, or a tab returning to the foreground after
    // the network changed underneath it.
    probeNow();
  } else {
    clearPollTimer();
  }
};

/**
 * Called by the API layer when a request fails with no HTTP response at all (see
 * `src/config/api/request.ts`). A real request failing is the strongest evidence available that
 * connectivity is gone, and it costs nothing when everything is working.
 */
export const reportNetworkFailure = (): void => {
  if (Date.now() - lastProbeStartedAt < HINT_THROTTLE_MS) return;
  probeNow();
};

const start = (): void => {
  window.addEventListener('online', handleBrowserOnline);
  window.addEventListener('offline', probeNow);
  window.addEventListener('focus', probeNow);
  document.addEventListener('visibilitychange', handleVisibilityChange);

  // Probe straight away only if we are starting out believing we are offline — the false-offline
  // case above, where nothing else will ever correct us. When we believe we are online we spend no
  // probe on page load and wait for the heartbeat or a hint, so the common path costs one request
  // every two minutes rather than one on every navigation.
  if (!online) probeNow();
  else scheduleNextPoll();
};

const stop = (): void => {
  window.removeEventListener('online', handleBrowserOnline);
  window.removeEventListener('offline', probeNow);
  window.removeEventListener('focus', probeNow);
  document.removeEventListener('visibilitychange', handleVisibilityChange);
  clearPollTimer();
};

const subscribe = (listener: () => void): (() => void) => {
  const isFirst = listeners.size === 0;
  listeners.add(listener);
  if (isFirst) start();
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) stop();
  };
};

const getSnapshot = (): boolean => online;

/** Server/prerender snapshot: assume online so the online UI is what gets hydrated. */
const getServerSnapshot = (): boolean => true;

/**
 * Tracks connectivity, verified by reachability probes. See the notes at the top of this file for
 * why `navigator.onLine` is only a hint here.
 */
export const useOnlineStatus = (): boolean =>
  useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

/** Test-only reset of the module-level monitor state. */
export const __resetOnlineStatusForTests = (): void => {
  stop();
  listeners.clear();
  online = typeof navigator === 'undefined' ? true : navigator.onLine;
  consecutiveFailures = 0;
  probeInFlight = false;
  lastProbeStartedAt = 0;
};

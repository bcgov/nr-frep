import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  __resetOnlineStatusForTests,
  reportNetworkFailure,
  useOnlineStatus,
} from './useOnlineStatus';

const PROBE_TIMEOUT_MS = 3000;
const OFFLINE_POLL_MS = 20_000;
const ONLINE_POLL_MS = 120_000;
const CONFIRM_OFFLINE_DELAY_MS = 1500;

/** `navigator.onLine` is read-only, so it has to be stubbed rather than assigned. */
const setBrowserOnLine = (value: boolean): void => {
  Object.defineProperty(navigator, 'onLine', { value, configurable: true });
};

const setHidden = (value: boolean): void => {
  Object.defineProperty(document, 'hidden', { value, configurable: true });
};

/**
 * Start a test in the false-offline state. The monitor seeds itself from `navigator.onLine` when it
 * is created, so the flag has to be set before the reset, not after.
 */
const startOffline = (): void => {
  setBrowserOnLine(false);
  __resetOnlineStatusForTests();
};

const fetchMock = vi.fn();

/** Let the probe's awaited fetch settle, then run whatever timers that scheduled. */
const settle = async (): Promise<void> => {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
};

const advance = async (ms: number): Promise<void> => {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
};

describe('useOnlineStatus', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    setBrowserOnLine(true);
    setHidden(false);
    fetchMock.mockReset();
    fetchMock.mockResolvedValue({ ok: true, status: 200 });
    vi.stubGlobal('fetch', fetchMock);
    __resetOnlineStatusForTests();
  });

  afterEach(() => {
    __resetOnlineStatusForTests();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('seeds from navigator.onLine and spends no probe on mount when it says online', () => {
    const { result } = renderHook(() => useOnlineStatus());

    expect(result.current).toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('recovers from a stuck false-offline flag without any browser event', async () => {
    // The reported bug: Chrome latched to "no connection", so the app starts out believing it is
    // offline, and no `online` event is ever fired to correct it.
    startOffline();

    const { result } = renderHook(() => useOnlineStatus());
    expect(result.current).toBe(false);

    await settle();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(result.current).toBe(true);
    // navigator.onLine is still lying; the probe result is what wins.
    expect(navigator.onLine).toBe(false);
  });

  it('probes the same-origin health endpoint, uncached and without credentials', async () => {
    startOffline();
    renderHook(() => useOnlineStatus());
    await settle();

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/^\/health\?_=\d+$/);
    expect(init).toMatchObject({ method: 'GET', cache: 'no-store', credentials: 'omit' });
    expect(init.signal).toBeInstanceOf(AbortSignal);
  });

  it('treats any HTTP response as reachable, including a 404', async () => {
    startOffline();
    fetchMock.mockResolvedValue({ ok: false, status: 404 });

    const { result } = renderHook(() => useOnlineStatus());
    await settle();

    // We are testing reachability, not the health of whatever answered.
    expect(result.current).toBe(true);
  });

  it('keeps polling while offline until connectivity returns', async () => {
    startOffline();
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    const { result } = renderHook(() => useOnlineStatus());
    await settle();
    expect(result.current).toBe(false);

    await advance(OFFLINE_POLL_MS);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(result.current).toBe(false);

    fetchMock.mockResolvedValue({ ok: true, status: 200 });
    await advance(OFFLINE_POLL_MS);
    expect(result.current).toBe(true);
  });

  it('requires two consecutive failures before flipping online to offline', async () => {
    const { result } = renderHook(() => useOnlineStatus());
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    await advance(ONLINE_POLL_MS);
    // One dropped request must not dump someone into offline mode mid-edit.
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(result.current).toBe(true);

    await advance(CONFIRM_OFFLINE_DELAY_MS);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(result.current).toBe(false);
  });

  it('stays online when a single failure is followed by a success', async () => {
    const { result } = renderHook(() => useOnlineStatus());

    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    await advance(ONLINE_POLL_MS);
    expect(result.current).toBe(true);

    await advance(CONFIRM_OFFLINE_DELAY_MS);
    expect(result.current).toBe(true);
  });

  it('counts a probe that never answers as a failure', async () => {
    startOffline();
    fetchMock.mockImplementation(
      (_url: string, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal?.addEventListener('abort', () => reject(new DOMException('Aborted')));
        }),
    );

    const { result } = renderHook(() => useOnlineStatus());
    await advance(PROBE_TIMEOUT_MS);

    expect(result.current).toBe(false);
  });

  it('detects a false-online state when a real request fails', async () => {
    const { result } = renderHook(() => useOnlineStatus());
    expect(result.current).toBe(true);

    // Associated with a captive portal: navigator.onLine is true, nothing is reachable.
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
    act(() => reportNetworkFailure());
    await settle();
    await advance(CONFIRM_OFFLINE_DELAY_MS);

    expect(result.current).toBe(false);
  });

  it('throttles a burst of failure hints into a single probe', async () => {
    renderHook(() => useOnlineStatus());

    act(() => {
      reportNetworkFailure();
      reportNetworkFailure();
      reportNetworkFailure();
    });
    await settle();

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('probes immediately when the browser reports connectivity returning', async () => {
    startOffline();
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
    const { result } = renderHook(() => useOnlineStatus());
    await settle();
    expect(result.current).toBe(false);

    fetchMock.mockResolvedValue({ ok: true, status: 200 });
    setBrowserOnLine(true);
    act(() => {
      window.dispatchEvent(new Event('online'));
    });
    await settle();

    expect(result.current).toBe(true);
  });

  it('verifies rather than trusts the browser offline event', async () => {
    const { result } = renderHook(() => useOnlineStatus());

    setBrowserOnLine(false);
    act(() => {
      window.dispatchEvent(new Event('offline'));
    });
    await settle();

    // The probe still answered, so the flag is what is wrong, not the network.
    expect(result.current).toBe(true);
  });

  it('stops probing while the tab is hidden and probes when it returns', async () => {
    renderHook(() => useOnlineStatus());

    setHidden(true);
    act(() => {
      document.dispatchEvent(new Event('visibilitychange'));
    });
    await advance(ONLINE_POLL_MS * 2);
    expect(fetchMock).not.toHaveBeenCalled();

    setHidden(false);
    act(() => {
      document.dispatchEvent(new Event('visibilitychange'));
    });
    await settle();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('runs one shared monitor no matter how many components subscribe', async () => {
    startOffline();
    const first = renderHook(() => useOnlineStatus());
    const second = renderHook(() => useOnlineStatus());
    const third = renderHook(() => useOnlineStatus());
    await settle();

    // Eight components call this hook; each must not bring its own probe loop.
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(first.result.current).toBe(true);
    expect(second.result.current).toBe(true);
    expect(third.result.current).toBe(true);
  });

  it('stops probing once the last subscriber unmounts', async () => {
    const { unmount } = renderHook(() => useOnlineStatus());
    unmount();

    await advance(ONLINE_POLL_MS * 2);

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('degrades to the browser flag when fetch is unavailable', async () => {
    startOffline();
    vi.stubGlobal('fetch', undefined);

    const { result } = renderHook(() => useOnlineStatus());
    await settle();

    expect(result.current).toBe(false);
  });
});

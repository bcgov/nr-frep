import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import SessionTimeout, { SESSION_EXPIRED_FLAG } from './index';

const logout = vi.fn();
const forceRefreshSession = vi.fn().mockResolvedValue(undefined);
const ensureFreshToken = vi.fn().mockResolvedValue('token');
vi.mock('@/context/auth/useAuth', () => ({
  useAuth: () => ({ logout, forceRefreshSession, ensureFreshToken }),
}));

const display = vi.fn();
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display }),
}));

const MINUTE = 60 * 1000;

// The timing constants are RE-STATED here rather than imported, on purpose: retuning the component
// against a different refresh-token TTL should fail loudly here, not quietly agree with itself.
// Keycloak's standard-realm refresh token is 30 minutes; these leave 5 minutes of headroom.
const IDLE_MINUTES = 25;
const WARNING_AT_MINUTES = 20; // 5:00 remaining

const advance = (ms: number) => act(() => vi.advanceTimersByTime(ms));

/** These tests run in a WebDriver-driven browser, which the component treats as automation. */
const setWebdriver = (value: boolean) =>
  Object.defineProperty(window.navigator, 'webdriver', { configurable: true, value });

describe('SessionTimeout', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    setWebdriver(false);
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
    setWebdriver(true);
  });

  it('shows the warning dialog 5 minutes before the 25-minute idle deadline', () => {
    render(<SessionTimeout />);
    expect(screen.queryByRole('alertdialog')).toBeNull();

    advance(WARNING_AT_MINUTES * MINUTE); // 5:00 remaining
    expect(screen.getByRole('alertdialog')).toBeTruthy();
    expect(screen.getByText(/about to be logged out/i)).toBeTruthy();
  });

  it('does not warn a minute early', () => {
    // Pins the lower edge: a component retuned to a longer warning would still pass the test above.
    render(<SessionTimeout />);
    advance((WARNING_AT_MINUTES - 1) * MINUTE);
    expect(screen.queryByRole('alertdialog')).toBeNull();
  });

  it('counts down in M:SS from 5:00', () => {
    render(<SessionTimeout />);
    advance(WARNING_AT_MINUTES * MINUTE);
    expect(screen.getByText('5:00')).toBeTruthy();

    advance(11 * 1000);
    expect(screen.getByText('4:49')).toBeTruthy();
  });

  it('turns the countdown red in the last 30 seconds', () => {
    render(<SessionTimeout />);
    advance(WARNING_AT_MINUTES * MINUTE);
    const countdown = screen.getByText('5:00');
    expect(countdown.classList.contains('session-timeout__count--danger')).toBe(false);

    advance(5 * MINUTE - 30 * 1000); // 0:30 remaining
    expect(countdown.classList.contains('session-timeout__count--danger')).toBe(true);
  });

  it('logs out and flags the session as expired at the deadline', () => {
    render(<SessionTimeout />);
    advance(IDLE_MINUTES * MINUTE);

    expect(logout).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(SESSION_EXPIRED_FLAG)).toBe('1');
  });

  it('"Stay logged in" renews the session, closes the dialog, and sets NO expiry flag', async () => {
    render(<SessionTimeout />);
    advance(WARNING_AT_MINUTES * MINUTE);

    fireEvent.click(screen.getByRole('button', { name: 'Stay logged in' }));
    // handleStay() awaits forceRefreshSession (resolved) then closes the dialog;
    // flush that pending microtask + resulting state update.
    await act(async () => {});

    expect(forceRefreshSession).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(sessionStorage.getItem(SESSION_EXPIRED_FLAG)).toBeNull();
    expect(logout).not.toHaveBeenCalled();
  });

  it('"Stay logged in" restarts the FULL idle window, not just the warning', async () => {
    render(<SessionTimeout />);
    advance(WARNING_AT_MINUTES * MINUTE);
    fireEvent.click(screen.getByRole('button', { name: 'Stay logged in' }));
    await act(async () => {});

    // Almost the whole window again with no warning — proves the clock restarted from zero rather
    // than resuming inside the 5-minute warning band.
    advance((WARNING_AT_MINUTES - 1) * MINUTE);
    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(logout).not.toHaveBeenCalled();
  });

  it('treats a failed renewal as a real expiry', async () => {
    // The refresh token is already gone; "Stay logged in" cannot keep its promise, and pretending
    // otherwise would leave the user on a page whose next request 401s.
    forceRefreshSession.mockRejectedValueOnce(new Error('refresh token expired'));
    render(<SessionTimeout />);
    advance(WARNING_AT_MINUTES * MINUTE);

    fireEvent.click(screen.getByRole('button', { name: 'Stay logged in' }));
    await act(async () => {});

    expect(logout).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(SESSION_EXPIRED_FLAG)).toBe('1');
  });

  it('"Log out" signs out without the expiry flag (deliberate logout)', () => {
    render(<SessionTimeout />);
    advance(WARNING_AT_MINUTES * MINUTE);

    fireEvent.click(screen.getByRole('button', { name: 'Log out' }));

    expect(logout).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(SESSION_EXPIRED_FLAG)).toBeNull();
  });

  it('activity before the warning resets the idle clock', () => {
    render(<SessionTimeout />);
    advance((WARNING_AT_MINUTES - 1) * MINUTE);
    // A click resets the clock; nearly the whole window again is still under the warning threshold.
    act(() => {
      window.dispatchEvent(new Event('mousedown'));
      vi.advanceTimersByTime(1000);
    });
    advance((WARNING_AT_MINUTES - 1) * MINUTE);

    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(logout).not.toHaveBeenCalled();
  });

  it('is inert under automation', () => {
    // The e2e suite shares one refresh token across serial specs, and Keycloak rotates it on every
    // renewal — a keepalive from here poisons the next spec's session. Long gaps with no synthetic
    // input would also read as an idle user and sign the suite out mid-run.
    setWebdriver(true);
    render(<SessionTimeout />);

    advance(IDLE_MINUTES * MINUTE);

    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(logout).not.toHaveBeenCalled();
    expect(ensureFreshToken).not.toHaveBeenCalled();
  });
});

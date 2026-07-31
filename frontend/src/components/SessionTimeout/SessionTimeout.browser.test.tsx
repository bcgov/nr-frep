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
const advance = (ms: number) => act(() => vi.advanceTimersByTime(ms));

describe('SessionTimeout', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows the warning dialog 5 minutes before the 30-minute idle deadline', () => {
    render(<SessionTimeout />);
    expect(screen.queryByRole('alertdialog')).toBeNull();

    advance(25 * MINUTE); // 5:00 remaining
    const dialog = screen.getByRole('alertdialog');
    expect(dialog).toBeTruthy();
    expect(screen.getByText(/about to be logged out/i)).toBeTruthy();
  });

  it('logs out and flags the session as expired at the deadline', () => {
    render(<SessionTimeout />);
    advance(30 * MINUTE);

    expect(logout).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(SESSION_EXPIRED_FLAG)).toBe('1');
  });

  it('"Stay logged in" refreshes the session, closes the dialog, and sets NO expiry flag', async () => {
    render(<SessionTimeout />);
    advance(25 * MINUTE);

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Stay logged in' }));
    });

    expect(forceRefreshSession).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(sessionStorage.getItem(SESSION_EXPIRED_FLAG)).toBeNull();
    expect(logout).not.toHaveBeenCalled();
  });

  it('"Log out" signs out without the expiry flag (deliberate logout)', () => {
    render(<SessionTimeout />);
    advance(25 * MINUTE);

    fireEvent.click(screen.getByRole('button', { name: 'Log out' }));

    expect(logout).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(SESSION_EXPIRED_FLAG)).toBeNull();
  });

  it('activity before the warning resets the idle clock', () => {
    render(<SessionTimeout />);
    advance(24 * MINUTE);
    // A click resets the clock; 24 more minutes = only 24 idle, so no warning yet.
    act(() => {
      window.dispatchEvent(new Event('mousedown'));
      vi.advanceTimersByTime(1000);
    });
    advance(24 * MINUTE);

    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(logout).not.toHaveBeenCalled();
  });
});

import { act, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const { getUserMock, signOutRedirectMock, clearLocalSessionMock } = vi.hoisted(() => ({
  getUserMock: vi.fn(),
  signOutRedirectMock: vi.fn().mockResolvedValue(undefined),
  clearLocalSessionMock: vi.fn().mockResolvedValue(undefined),
}));
vi.mock('@/services/keycloak', () => ({
  getUserManager: () => ({ getUser: getUserMock, signinCallback: vi.fn() }),
  signIn: vi.fn(),
  signOutRedirect: signOutRedirectMock,
  clearLocalSession: clearLocalSessionMock,
  ensureFreshUser: vi.fn(),
  forceRenew: vi.fn(),
}));
vi.mock('@/env', () => ({
  env: {
    VITE_BASE_PATH: '',
    VITE_KEYCLOAK_URL: 'https://test.loginproxy.gov.bc.ca/auth/realms/standard',
    VITE_KEYCLOAK_CLIENT_ID: 'frep-app',
    VITE_ZONE: 'TEST',
  },
}));

import { AuthProvider } from './AuthProvider';
import { OFFLINE_SIGNOUT_FLAG } from './authUtils';
import { useAuth } from './useAuth';

/** Clicks logout and shows whether a user is present, so the test can assert both. */
const Probe = () => {
  const { logout, user } = useAuth();
  return (
    <button type="button" onClick={logout}>
      {user ? 'signed-in' : 'signed-out'}
    </button>
  );
};

let assigned: string[] = [];

const setOnline = (online: boolean) =>
  Object.defineProperty(window.navigator, 'onLine', { configurable: true, value: online });

beforeEach(() => {
  vi.clearAllMocks();
  assigned = [];
  sessionStorage.clear();
  // No session: hydrate resolves with no user, so the provider settles quickly.
  getUserMock.mockResolvedValue(null);
  Object.defineProperty(window, 'location', {
    configurable: true,
    value: {
      origin: 'https://frep.example',
      assign: (value: string) => assigned.push(value),
    },
  });
});

const renderProvider = async () => {
  await act(async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );
  });
};

describe('logout while offline', () => {
  it('signs out locally instead of starting a redirect that cannot complete', async () => {
    // signoutRedirect is a full-page navigation. Offline it lands on the browser's
    // ERR_INTERNET_DISCONNECTED page — outside the app, session already cleared.
    setOnline(false);
    await renderProvider();

    await act(async () => screen.getByRole('button').click());

    expect(signOutRedirectMock).not.toHaveBeenCalled();
    expect(clearLocalSessionMock).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button').textContent).toBe('signed-out');
  });

  it('moves to the landing so signing out is visible', async () => {
    // Without this the screen never changed: the offline route set still serves the page the user
    // was on (a CHR checklist, the offline list, the dashboard), so its catch-all never fires and
    // logout reads as a dead control.
    setOnline(false);
    // The harness stubs window.location, so the move is observed on history instead.
    const replaceState = vi.spyOn(window.history, 'replaceState');
    await renderProvider();

    await act(async () => screen.getByRole('button').click());

    expect(replaceState).toHaveBeenCalledWith({}, '', '/');
    // Still no page load — a real navigation offline depends on the service worker being active.
    expect(assigned).toEqual([]);
    replaceState.mockRestore();
  });

  it('flags the local sign-out so the landing page can explain it', async () => {
    setOnline(false);
    await renderProvider();

    await act(async () => screen.getByRole('button').click());

    // The upstream IDIR / Keycloak sessions are untouched and unreachable from here.
    expect(sessionStorage.getItem(OFFLINE_SIGNOUT_FLAG)).toBe('1');
  });

  it('drives the realm sign-out when online, leaving the stored user for it to read', async () => {
    setOnline(true);
    await renderProvider();

    await act(async () => screen.getByRole('button').click());

    expect(signOutRedirectMock).toHaveBeenCalledTimes(1);
    // NOT cleared first: signoutRedirect() needs the stored user to build id_token_hint, without
    // which Keycloak cannot attribute the logout and the realm session survives it.
    expect(clearLocalSessionMock).not.toHaveBeenCalled();
    expect(sessionStorage.getItem(OFFLINE_SIGNOUT_FLAG)).toBeNull();
  });
});

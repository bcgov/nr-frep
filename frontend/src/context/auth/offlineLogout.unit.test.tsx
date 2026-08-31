import { act, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const { fetchAuthSessionMock, signOutMock } = vi.hoisted(() => ({
  fetchAuthSessionMock: vi.fn(),
  signOutMock: vi.fn().mockResolvedValue(undefined),
}));
vi.mock('aws-amplify/auth', () => ({
  fetchAuthSession: fetchAuthSessionMock,
  signInWithRedirect: vi.fn(),
  signOut: signOutMock,
}));
vi.mock('@/env', () => ({
  env: {
    VITE_BASE_PATH: '',
    VITE_USER_POOLS_WEB_CLIENT_ID: 'cognito-client',
    VITE_LOGOUT_SITEMINDER_URL: 'https://sm/logoff',
    VITE_LOGOUT_KEYCLOAK_URL: 'https://kc/logout',
    VITE_LOGOUT_KEYCLOAK_CLIENT_ID: 'kc-client',
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
  // No session: hydrate resolves with no tokens, so the provider settles quickly.
  fetchAuthSessionMock.mockResolvedValue({ tokens: undefined });
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
  it('signs out locally instead of navigating to an unreachable logout chain', async () => {
    // The chain is a full-page navigation. Offline it lands on the browser's
    // ERR_INTERNET_DISCONNECTED page — outside the app, tokens already cleared.
    setOnline(false);
    await renderProvider();

    await act(async () => screen.getByRole('button').click());

    expect(assigned).toEqual([]);
    expect(screen.getByRole('button').textContent).toBe('signed-out');
  });

  it('flags the local sign-out so the landing page can explain it', async () => {
    setOnline(false);
    await renderProvider();

    await act(async () => screen.getByRole('button').click());

    // The upstream IDIR/Keycloak/Cognito sessions are untouched and unreachable from here.
    expect(sessionStorage.getItem(OFFLINE_SIGNOUT_FLAG)).toBe('1');
  });

  it('still drives the full federated chain when online', async () => {
    setOnline(true);
    await renderProvider();

    await act(async () => screen.getByRole('button').click());

    expect(assigned).toHaveLength(1);
    expect(assigned[0]).toContain('https://sm/logoff');
    expect(sessionStorage.getItem(OFFLINE_SIGNOUT_FLAG)).toBeNull();
  });
});

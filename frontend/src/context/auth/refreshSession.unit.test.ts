import { beforeEach, describe, expect, it, vi } from 'vitest';

const { fetchAuthSessionMock, signOutMock } = vi.hoisted(() => ({
  fetchAuthSessionMock: vi.fn(),
  signOutMock: vi.fn().mockResolvedValue(undefined),
}));
vi.mock('aws-amplify/auth', () => ({
  fetchAuthSession: fetchAuthSessionMock,
  signOut: signOutMock,
}));
vi.mock('@/env', () => ({ env: { VITE_BASE_PATH: '' } }));

const withSession = () => ({ tokens: { accessToken: { payload: { exp: 9_999_999_999 } } } });
const withoutSession = () => ({ tokens: undefined });

/** The module keeps its "already redirecting" flag in module scope, so each case needs a fresh copy. */
const loadModule = async () => {
  vi.resetModules();
  return import('./refreshSession');
};

let assigned: string[] = [];

const setOnline = (online: boolean) =>
  Object.defineProperty(window.navigator, 'onLine', { configurable: true, value: online });

beforeEach(() => {
  vi.clearAllMocks();
  assigned = [];
  setOnline(true);
  // jsdom refuses a real navigation; capture the assignment instead.
  Object.defineProperty(window, 'location', {
    configurable: true,
    value: {
      origin: 'https://frep.example',
      set href(value: string) {
        assigned.push(value);
      },
      get href() {
        return assigned[assigned.length - 1] ?? '';
      },
    },
  });
});

describe('handleUnauthorized', () => {
  it('ends the session and redirects when the user still has one', async () => {
    // The 401 case this exists for: the server rejected a token the client believed was valid.
    fetchAuthSessionMock.mockResolvedValue(withSession());
    const { handleUnauthorized } = await loadModule();

    await expect(handleUnauthorized()).resolves.toBe(true);
    expect(signOutMock).toHaveBeenCalledTimes(1);
    expect(assigned).toEqual(['https://frep.example/']);
  });

  it('does nothing when there is no session to end', async () => {
    // Not a timeout — an unauthenticated call from a signed-out user. Redirecting would bounce them
    // to the root they are already on, and loop if that page also calls the API.
    fetchAuthSessionMock.mockResolvedValue(withoutSession());
    const { handleUnauthorized } = await loadModule();

    await expect(handleUnauthorized()).resolves.toBe(false);
    expect(signOutMock).not.toHaveBeenCalled();
    expect(assigned).toEqual([]);
  });

  it('redirects once when several requests 401 together', async () => {
    // A page typically has several requests in flight; without the guard each would sign out and
    // assign location separately.
    fetchAuthSessionMock.mockResolvedValue(withSession());
    const { handleUnauthorized } = await loadModule();

    await Promise.all([handleUnauthorized(), handleUnauthorized(), handleUnauthorized()]);

    expect(signOutMock).toHaveBeenCalledTimes(1);
    expect(assigned).toHaveLength(1);
  });

  it('still redirects when sign-out itself fails', async () => {
    // Getting the user back to login matters more than a clean Amplify sign-out.
    fetchAuthSessionMock.mockResolvedValue(withSession());
    signOutMock.mockRejectedValueOnce(new Error('network'));
    const { handleUnauthorized } = await loadModule();

    await handleUnauthorized();

    expect(assigned).toEqual(['https://frep.example/']);
  });

  it('treats an unreadable session as "no session" rather than redirecting', async () => {
    fetchAuthSessionMock.mockRejectedValue(new Error('storage unavailable'));
    const { handleUnauthorized } = await loadModule();

    await expect(handleUnauthorized()).resolves.toBe(false);
    expect(assigned).toEqual([]);
  });
});

/**
 * Offline the app deliberately runs signed out, on device-local checklists. A redirect to the login
 * page there is both useless (IDIR login needs a network) and self-perpetuating: it reloads the
 * page, the page calls the API, and the redirect fires again — the loop reported from the field.
 */
describe('while offline', () => {
  it('does not redirect when there is no token to refresh', async () => {
    setOnline(false);
    fetchAuthSessionMock.mockResolvedValue(withoutSession());
    const { ensureSessionFresh } = await loadModule();

    await ensureSessionFresh();

    expect(assigned).toEqual([]);
    expect(signOutMock).not.toHaveBeenCalled();
  });

  it('does not redirect when the refresh itself cannot reach the network', async () => {
    setOnline(false);
    fetchAuthSessionMock.mockRejectedValue(new Error('Network request failed'));
    const { ensureSessionFresh } = await loadModule();

    await ensureSessionFresh();

    expect(assigned).toEqual([]);
  });

  it('still redirects once back online', async () => {
    setOnline(true);
    fetchAuthSessionMock.mockResolvedValue(withoutSession());
    const { ensureSessionFresh } = await loadModule();

    await ensureSessionFresh();

    expect(assigned).toEqual(['https://frep.example/']);
  });
});

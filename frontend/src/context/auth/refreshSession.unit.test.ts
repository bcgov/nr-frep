import { beforeEach, describe, expect, it, vi } from 'vitest';

const { ensureFreshUserMock, clearLocalSessionMock, getUserMock } = vi.hoisted(() => ({
  ensureFreshUserMock: vi.fn(),
  clearLocalSessionMock: vi.fn().mockResolvedValue(undefined),
  getUserMock: vi.fn(),
}));
vi.mock('@/services/keycloak', () => ({
  REFRESH_MARGIN_SECONDS: 60,
  ensureFreshUser: ensureFreshUserMock,
  clearLocalSession: clearLocalSessionMock,
  getUserManager: () => ({ getUser: getUserMock }),
}));
vi.mock('@/env', () => ({ env: { VITE_BASE_PATH: '' } }));

const withSession = () => ({ access_token: 'a.b.c', expires_at: 9_999_999_999 });

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
    getUserMock.mockResolvedValue(withSession());
    const { handleUnauthorized } = await loadModule();

    await expect(handleUnauthorized()).resolves.toBe(true);
    expect(clearLocalSessionMock).toHaveBeenCalledTimes(1);
    expect(assigned).toEqual(['https://frep.example/']);
  });

  it('does nothing when there is no session to end', async () => {
    // Not a timeout — an unauthenticated call from a signed-out user. Redirecting would bounce them
    // to the root they are already on, and loop if that page also calls the API.
    getUserMock.mockResolvedValue(null);
    const { handleUnauthorized } = await loadModule();

    await expect(handleUnauthorized()).resolves.toBe(false);
    expect(clearLocalSessionMock).not.toHaveBeenCalled();
    expect(assigned).toEqual([]);
  });

  it('redirects once when several requests 401 together', async () => {
    // A page typically has several requests in flight; without the guard each would sign out and
    // assign location separately.
    getUserMock.mockResolvedValue(withSession());
    const { handleUnauthorized } = await loadModule();

    await Promise.all([handleUnauthorized(), handleUnauthorized(), handleUnauthorized()]);

    expect(clearLocalSessionMock).toHaveBeenCalledTimes(1);
    expect(assigned).toHaveLength(1);
  });

  it('treats an unreadable session as "no session" rather than redirecting', async () => {
    getUserMock.mockRejectedValue(new Error('storage unavailable'));
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
  it('does not redirect when there is no token to renew', async () => {
    setOnline(false);
    ensureFreshUserMock.mockResolvedValue(null);
    const { ensureSessionFresh } = await loadModule();

    await ensureSessionFresh();

    expect(assigned).toEqual([]);
    expect(clearLocalSessionMock).not.toHaveBeenCalled();
  });

  it('still redirects once back online', async () => {
    setOnline(true);
    ensureFreshUserMock.mockResolvedValue(null);
    const { ensureSessionFresh } = await loadModule();

    await ensureSessionFresh();

    expect(assigned).toEqual(['https://frep.example/']);
  });

  it('leaves a still-valid session alone', async () => {
    ensureFreshUserMock.mockResolvedValue(withSession());
    const { ensureSessionFresh } = await loadModule();

    await ensureSessionFresh();

    expect(assigned).toEqual([]);
    expect(clearLocalSessionMock).not.toHaveBeenCalled();
  });
});

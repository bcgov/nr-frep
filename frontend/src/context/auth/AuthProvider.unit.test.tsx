import { render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthContext } from './AuthContext';
import { AuthProvider } from './AuthProvider';

const fetchAuthSession = vi.fn();
const signInWithRedirect = vi.fn();
const signOut = vi.fn();

vi.mock('aws-amplify/auth', () => ({
  fetchAuthSession: (...args: unknown[]) => fetchAuthSession(...args),
  signInWithRedirect: (...args: unknown[]) => signInWithRedirect(...args),
  signOut: (...args: unknown[]) => signOut(...args),
}));

vi.mock('@/env', () => ({
  env: { VITE_ZONE: 'dev', VITE_USER_POOLS_WEB_CLIENT_ID: 'test-client-id' },
}));

type Ctx = NonNullable<React.ContextType<typeof AuthContext>>;

const sessionWithIdToken = (overrides: Partial<{ exp: number }> = {}) => ({
  tokens: {
    idToken: {
      payload: {
        'custom:idp_display_name': 'Doe, John',
        'custom:idp_username': 'jdoe',
        'custom:idp_name': 'IDIR',
        'email': 'john.doe@gov.bc.ca',
        'cognito:groups': ['FREP_VIEW_ONLY'],
      },
    },
    accessToken: {
      payload: { exp: overrides.exp ?? Math.floor(Date.now() / 1000) + 3600 },
      toString: () => 'access-token-value',
    },
  },
});

const renderWithContext = async (): Promise<Ctx> => {
  let captured: Ctx | undefined;
  render(
    <AuthProvider>
      <AuthContext.Consumer>
        {(value) => {
          captured = value;
          return null;
        }}
      </AuthContext.Consumer>
    </AuthProvider>,
  );
  await waitFor(() => {
    expect(captured?.isLoading).toBe(false);
  });
  return captured!;
};

beforeEach(() => {
  fetchAuthSession.mockReset();
  signInWithRedirect.mockReset();
  signOut.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('AuthProvider (Cognito / IDIR)', () => {
  it('hydrates the user from the Cognito session on mount', async () => {
    fetchAuthSession.mockResolvedValue(sessionWithIdToken());

    const ctx = await renderWithContext();

    expect(ctx.isLoggedIn).toBe(true);
    expect(ctx.user?.userName).toBe('jdoe');
    expect(ctx.user?.roles).toContain('FREP_VIEW_ONLY');
    expect(ctx.user?.idpProvider).toBe('IDIR');
  });

  it('marks the user as logged out when fetchAuthSession rejects', async () => {
    fetchAuthSession.mockRejectedValue(new Error('no session'));

    const ctx = await renderWithContext();

    expect(ctx.isLoggedIn).toBe(false);
    expect(ctx.user).toBeUndefined();
  });

  it('login triggers signInWithRedirect with the env-scoped IDIR provider', async () => {
    fetchAuthSession.mockResolvedValue({});

    const ctx = await renderWithContext();
    ctx.login();

    expect(signInWithRedirect).toHaveBeenCalledWith({
      provider: { custom: 'dev-IDIR' },
    });
  });

  it('logout signs out and redirects to the landing page', async () => {
    fetchAuthSession.mockResolvedValue(sessionWithIdToken());
    signOut.mockResolvedValue(undefined);
    const assign = vi.spyOn(window.location, 'assign').mockImplementation(() => undefined);

    const ctx = await renderWithContext();
    ctx.logout();
    await waitFor(() => expect(assign).toHaveBeenCalledWith('/'));
    expect(signOut).toHaveBeenCalled();

    assign.mockRestore();
  });

  it('ensureFreshToken force-refreshes when the access token is near expiry', async () => {
    const expiringSoon = sessionWithIdToken({
      exp: Math.floor(Date.now() / 1000) + 5,
    });
    const refreshed = {
      tokens: {
        accessToken: { toString: () => 'refreshed-access-token' },
      },
    };
    fetchAuthSession
      .mockResolvedValueOnce(expiringSoon) // initial hydrate
      .mockResolvedValueOnce(expiringSoon) // ensureFreshToken inspect
      .mockResolvedValueOnce(refreshed); // forceRefresh:true call

    const ctx = await renderWithContext();
    const token = await ctx.ensureFreshToken();

    expect(token).toBe('refreshed-access-token');
    expect(fetchAuthSession).toHaveBeenLastCalledWith({ forceRefresh: true });
  });
});

import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from './AuthProvider';
import { useAuth } from './useAuth';

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

const TestComponent = () => {
  const { user, isLoggedIn, isLoading } = useAuth();
  return (
    <>
      <span data-testid="is-logged-in">{isLoggedIn ? 'yes' : 'no'}</span>
      <span data-testid="is-loading">{isLoading ? 'yes' : 'no'}</span>
      <span data-testid="user">{user ? user.displayName || user.userName : 'none'}</span>
    </>
  );
};

beforeEach(() => {
  fetchAuthSession.mockReset();
  signInWithRedirect.mockReset();
  signOut.mockReset();
});

describe('AuthContext', () => {
  it('exposes the hydrated Cognito user', async () => {
    fetchAuthSession.mockResolvedValue({
      tokens: {
        idToken: {
          payload: {
            'custom:idp_display_name': 'Doe, John',
            'custom:idp_username': 'jdoe',
            'custom:idp_name': 'IDIR',
            'cognito:groups': ['FREP_VIEW_ONLY'],
          },
        },
      },
    });

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('is-loading').textContent).toBe('no');
    });

    expect(screen.getByTestId('is-logged-in').textContent).toBe('yes');
    expect(screen.getByTestId('user').textContent).toBe('Doe, John');
  });

  it('reports a logged-out state when no session is available', async () => {
    fetchAuthSession.mockRejectedValue(new Error('no session'));

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('is-loading').textContent).toBe('no');
    });

    expect(screen.getByTestId('is-logged-in').textContent).toBe('no');
    expect(screen.getByTestId('user').textContent).toBe('none');
  });

  it('throws if useAuth is used outside of AuthProvider', () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const Broken = () => {
      useAuth();
      return null;
    };
    expect(() => render(<Broken />)).toThrow('useAuth must be used within an AuthProvider');
    errorSpy.mockRestore();
  });
});

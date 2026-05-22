import { render, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';

import { AuthContext } from './AuthContext';
import { AuthProvider } from './AuthProvider';

describe('AuthProvider (local dev mock)', () => {
  it('provides a logged-in local user', async () => {
    let context;
    render(
      <AuthProvider>
        <AuthContext.Consumer>
          {(value) => {
            context = value;
            return null;
          }}
        </AuthContext.Consumer>
      </AuthProvider>,
    );
    await waitFor(() => expect(context).toBeDefined());
    expect(context.isLoggedIn).toBe(true);
    expect(context.user?.roles).toContain('FREP_VIEW_ONLY');
  });

  it('login navigates to the dashboard', async () => {
    const assign = vi.spyOn(window.location, 'assign').mockImplementation(() => undefined);
    let context;
    render(
      <AuthProvider>
        <AuthContext.Consumer>
          {(value) => {
            context = value;
            return null;
          }}
        </AuthContext.Consumer>
      </AuthProvider>,
    );
    await waitFor(() => expect(context).toBeDefined());
    await act(() => context.login());
    expect(assign).toHaveBeenCalledWith('/dashboard');
    assign.mockRestore();
  });

  it('userToken returns undefined in local dev', async () => {
    let context;
    render(
      <AuthProvider>
        <AuthContext.Consumer>
          {(value) => {
            context = value;
            return null;
          }}
        </AuthContext.Consumer>
      </AuthProvider>,
    );
    await waitFor(() => expect(context).toBeDefined());
    expect(context.userToken()).toBeUndefined();
  });
});

/*
 * --- Cognito auth tests (re-enable with AuthProvider cognito implementation) ---
 *
 * vi.mock('aws-amplify/auth', () => ({ fetchAuthSession, signInWithRedirect, signOut }));
 * ... login with IDIR, logout, userToken from cookie ...
 */

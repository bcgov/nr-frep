import { useMemo, type ReactNode } from 'react';

import { AuthContext, type AuthContextType } from './AuthContext';
import { type FamLoginUser } from './types';

// LOCAL DEV: Cognito / IDIR auth is disabled. Re-enable the block at the bottom
// of this file and restore main.tsx Amplify setup before deploying.

const LOCAL_DEV_USER: FamLoginUser = {
  userName: 'local-dev',
  displayName: 'Local Developer',
  idpProvider: 'IDIR',
  roles: ['FREP_VIEWER'],
  privileges: {},
};

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const contextValue: AuthContextType = useMemo(
    () => ({
      user: LOCAL_DEV_USER,
      isLoggedIn: true,
      isLoading: false,
      login: () => {
        window.location.assign('/dashboard');
      },
      logout: () => {
        window.location.assign('/');
      },
      userToken: () => undefined,
      ensureFreshToken: async () => undefined,
    }),
    [],
  );

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>;
};

/*
 * --- Cognito / IDIR auth (re-enable for deployed environments) ---
 *
 * import { fetchAuthSession, signInWithRedirect, signOut } from 'aws-amplify/auth';
 * import { useEffect, useMemo, useState, useCallback, useRef, type ReactNode } from 'react';
 * import { env } from '@/env';
 * import { AuthContext, type AuthContextType } from './AuthContext';
 * import { parseToken, getAccessTokenFromCookie } from './authUtils';
 * import { type FamLoginUser } from './types';
 *
 * const REFRESH_MARGIN_SECONDS = 30;
 * const MIN_REFRESH_GAP_MS = 5_000;
 *
 * export const AuthProvider = ({ children }: { children: ReactNode }) => {
 *   ... original implementation ...
 *   login via signInWithRedirect({ provider: { custom: `${appEnv}-IDIR` } })
 * };
 */

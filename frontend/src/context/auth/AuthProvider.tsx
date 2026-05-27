import { fetchAuthSession, signInWithRedirect, signOut } from 'aws-amplify/auth';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import { AuthContext, type AuthContextType } from './AuthContext';
import { getAccessTokenFromCookie, parseToken } from './authUtils';

import type { FamLoginUser } from './types';

import { env } from '@/env';

/** Refresh the session this many seconds before access-token expiry. */
const REFRESH_MARGIN_SECONDS = 30;

/** Minimum time between two `ensureFreshToken` refreshes (debounce). */
const MIN_REFRESH_GAP_MS = 5_000;

/**
 * AuthProvider — wires AWS Amplify Cognito (FAM IDIR) into the React tree.
 *
 * <p>Login uses the FAM custom IDP {@code <env>-IDIR} so the user lands on the
 * BCGov SSO page; sign-out clears the Amplify session and redirects to the
 * configured landing URL.</p>
 *
 * <p>Tokens are read directly from the Amplify-managed cookies via
 * {@link getAccessTokenFromCookie}; this avoids the async {@link fetchAuthSession}
 * call on the hot path. {@link ensureFreshToken} is the async fallback that
 * proactively refreshes the access token when it is close to expiring.</p>
 */
export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<FamLoginUser | undefined>(undefined);
  const [isLoading, setIsLoading] = useState(true);
  const lastRefreshAtRef = useRef<number>(0);

  // Hydrate the user from the current Amplify session on mount.
  useEffect(() => {
    let cancelled = false;

    const hydrate = async () => {
      try {
        const session = await fetchAuthSession();
        if (cancelled) return;

        const idTokenObject = session.tokens?.idToken;
        const parsed = idTokenObject ? parseToken(idTokenObject) : undefined;
        setUser(parsed);
      } catch {
        if (cancelled) return;
        setUser(undefined);
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    };

    void hydrate();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(() => {
    const appEnv = (env.VITE_ZONE || 'dev').toLowerCase();
    void signInWithRedirect({
      provider: { custom: `${appEnv}-IDIR` },
    });
  }, []);

  const logout = useCallback(() => {
    void (async () => {
      try {
        await signOut();
      } finally {
        window.location.assign('/');
      }
    })();
  }, []);

  /**
   * Synchronously read the cached access token from the Amplify cookie store.
   * Used by code paths that cannot await (e.g. axios interceptors that bridge
   * to header builders).
   */
  const userToken = useCallback((): string | undefined => {
    return getAccessTokenFromCookie();
  }, []);

  /**
   * Returns a non-expired access token, refreshing via the refresh token if
   * the current access token is close to expiry. Returns {@code undefined} if
   * the session cannot be refreshed (caller should treat as signed-out).
   */
  const ensureFreshToken = useCallback(async (): Promise<string | undefined> => {
    const now = Date.now();

    // Debounce: avoid hitting Cognito on every request.
    const skipForceRefresh = now - lastRefreshAtRef.current < MIN_REFRESH_GAP_MS;

    try {
      const session = await fetchAuthSession({ forceRefresh: false });
      const exp = session.tokens?.accessToken?.payload?.exp;
      const nowSeconds = Math.floor(now / 1000);

      const expiringSoon = typeof exp === 'number' && exp - nowSeconds <= REFRESH_MARGIN_SECONDS;

      if (expiringSoon && !skipForceRefresh) {
        const refreshed = await fetchAuthSession({ forceRefresh: true });
        lastRefreshAtRef.current = Date.now();
        return refreshed.tokens?.accessToken?.toString();
      }

      return session.tokens?.accessToken?.toString();
    } catch {
      return undefined;
    }
  }, []);

  const contextValue: AuthContextType = useMemo(
    () => ({
      user,
      isLoggedIn: !!user,
      isLoading,
      login,
      logout,
      userToken,
      ensureFreshToken,
    }),
    [user, isLoading, login, logout, userToken, ensureFreshToken],
  );

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>;
};

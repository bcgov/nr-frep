import { fetchAuthSession, signInWithRedirect, signOut } from 'aws-amplify/auth';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import { AuthContext, type AuthContextType } from './AuthContext';
import { OFFLINE_SIGNOUT_FLAG, clearStoredTokens, parseToken } from './authUtils';
import { buildFederatedLogoutUrl } from './logoutChain';

import type { FamLoginUser, LoginProvider } from './types';

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
 * <p>Access tokens for API calls are read via {@link fetchAuthSession} (storage-agnostic — see
 * {@code services/http/headers.ts}), not from cookies: Amplify's {@code configure()} keeps tokens
 * in its default {@code localStorage} store regardless of the CookieStorage override in main.tsx.
 * {@link ensureFreshToken} proactively refreshes the access token when it is close to expiring.</p>
 */
export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<FamLoginUser | undefined>(undefined);
  const [isLoading, setIsLoading] = useState(true);
  const lastRefreshAtRef = useRef<number>(0);

  // VITE_ZONE drives the Cognito identity_provider prefix (e.g. DEV-IDIR,
  // TEST-IDIR, IDIR). Numeric zones (PR previews) fall back to TEST so we
  // don't try to call a non-existent <PR>-IDIR provider. Mirrors nr-fspts.
  const appEnv = isNaN(Number(env.VITE_ZONE)) ? env.VITE_ZONE || 'TEST' : 'TEST';

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

  const login = useCallback(
    (provider: LoginProvider) => {
      // Cognito identity_provider names follow `<ENV>-<PROVIDER>` (e.g. DEV-IDIR,
      // TEST-BCEIDBUSINESS); the env prefix is omitted in PROD. Mirrors nr-fspts.
      const prefix = appEnv === 'PROD' ? '' : `${appEnv.toUpperCase()}-`;
      const providerName = provider === 'idir' ? `${prefix}IDIR` : `${prefix}BCEIDBUSINESS`;
      void signInWithRedirect({ provider: { custom: providerName } });
    },
    [appEnv],
  );

  const logout = useCallback(() => {
    // Offline the federated chain cannot be reached. window.location.assign() lands on the browser's
    // ERR_INTERNET_DISCONNECTED page — outside the app, with the local tokens already cleared — and
    // the user has to find their own way back. Sign out locally instead and stay put: clearing the
    // user swaps AppRoutes to the offline route set, whose catch-all returns to the landing.
    //
    // This clears the app session only. The upstream IDIR / Keycloak / Cognito sessions are
    // untouched and cannot be reached from here, so signing in again once back online may not
    // re-prompt for credentials — the landing page says so (OFFLINE_SIGNOUT_FLAG).
    if (!navigator.onLine) {
      sessionStorage.setItem(OFFLINE_SIGNOUT_FLAG, '1');
      clearStoredTokens();
      setUser(undefined);
      return;
    }

    // Primary path: drive the BC-Gov federated logout chain ourselves (Siteminder → Keycloak →
    // Cognito → app) so the upstream IDIR/Keycloak/Cognito sessions are cleared — not just the local
    // app tokens (which is all Amplify's signOut() reliably does here). We clear the Amplify token
    // cookies up front so the return trip reads as logged out, then navigate; the chain's final
    // Cognito /logout hop clears the Cognito session cookie server-side.
    //
    // Deliberately NOT setUser(undefined): a full-page navigation is imminent, and clearing state
    // could momentarily mount the Landing page, whose effect reads-and-clears the SessionTimeout
    // "session expired" flag before the round-trip returns — consuming the notice signal early.
    const chainUrl = buildFederatedLogoutUrl(window.location.origin);
    if (chainUrl) {
      clearStoredTokens();
      window.location.assign(chainUrl);
      return;
    }
    // Fallback (chain env not configured): Amplify hosted-UI sign-out, which redirects through
    // Cognito /logout to the configured redirectSignOut. No trailing assign('/') — that would clobber
    // Amplify's redirect and leave the SSO session intact.
    void (async () => {
      await signOut();
      setUser(undefined);
    })();
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

  /**
   * Unconditional refresh (vs {@link ensureFreshToken}, which only refreshes near expiry): forces
   * Cognito to mint a fresh rotated refresh token, sliding the session, and re-parses the user.
   * Throws if the refresh token has expired (Amplify rejects the refresh, or returns no tokens) so
   * the caller can treat it as a real expiry. Used by the SessionTimeout "Stay logged in" action.
   */
  const forceRefreshSession = useCallback(async (): Promise<void> => {
    const session = await fetchAuthSession({ forceRefresh: true });
    const idTokenObject = session.tokens?.idToken;
    if (!idTokenObject) {
      throw new Error('Session refresh failed — no tokens.');
    }
    setUser(parseToken(idTokenObject));
  }, []);

  const contextValue: AuthContextType = useMemo(
    () => ({
      user,
      isLoggedIn: !!user,
      isLoading,
      login,
      logout,
      ensureFreshToken,
      forceRefreshSession,
    }),
    [user, isLoading, login, logout, ensureFreshToken, forceRefreshSession],
  );

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>;
};

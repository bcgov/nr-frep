import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

import { AuthContext, type AuthContextType } from './AuthContext';
import { OFFLINE_SIGNOUT_FLAG, parseToken } from './authUtils';

import type { FamLoginUser, LoginProvider } from './types';

import { env } from '@/env';
import {
  clearLocalSession,
  ensureFreshUser,
  forceRenew,
  getUserManager,
  signIn,
  signOutRedirect,
} from '@/services/keycloak';

/**
 * AuthProvider — wires BC Gov SSO (Keycloak standard realm) into the React tree.
 *
 * <p>Almost all of the work lives in `services/keycloak.ts`: `oidc-client-ts` discovers every
 * endpoint from the issuer, drives PKCE, stores the tokens in sessionStorage and renews from the
 * refresh token. What is left here is React state — hydrating the user on mount, and exposing the
 * four operations the app actually performs.
 *
 * <p>Profile claims ride the **access token**, so the user is parsed from it directly; there is no
 * ID-token step and no userinfo round trip.
 */
export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<FamLoginUser | undefined>(undefined);
  const [isLoading, setIsLoading] = useState(true);

  // Hydrate from the stored session on mount.
  useEffect(() => {
    let cancelled = false;

    const hydrate = async () => {
      try {
        const current = await getUserManager().getUser();
        if (cancelled) return;
        setUser(
          current?.access_token ? parseToken(decodeAccessToken(current.access_token)) : undefined,
        );
      } catch {
        if (!cancelled) setUser(undefined);
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    };

    void hydrate();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback((provider: LoginProvider) => {
    void signIn(provider);
  }, []);

  /**
   * Completes the authorization-code exchange and adopts the resulting session. Called only by the
   * /authCallback route; rejects so that page can show its own error rather than stranding the user
   * on a spinner.
   */
  const completeSignIn = useCallback(async (): Promise<void> => {
    const signedIn = await getUserManager().signinCallback();
    setUser(
      signedIn?.access_token ? parseToken(decodeAccessToken(signedIn.access_token)) : undefined,
    );
    setIsLoading(false);
  }, []);

  const logout = useCallback(() => {
    // Offline the realm cannot be reached. A redirect would land on the browser's
    // ERR_INTERNET_DISCONNECTED page — outside the app, with the local session already gone — and the
    // user would have to find their own way back. Sign out locally and stay put instead: clearing the
    // user swaps AppRoutes to the offline route set, whose catch-all returns to the landing.
    //
    // This clears the app session only. The upstream IDIR / Keycloak sessions are untouched and
    // cannot be reached from here, so signing in again once back online may not re-prompt for
    // credentials — the landing page says so (OFFLINE_SIGNOUT_FLAG).
    if (!navigator.onLine) {
      sessionStorage.setItem(OFFLINE_SIGNOUT_FLAG, '1');
      // Move to the landing *before* clearing the user, and without a page load.
      //
      // Clearing the user alone left the screen untouched, so signing out looked like a dead
      // control: the offline route set still serves the page you were most likely on when you
      // pressed it (/protocol-checklists/chr/:id, /chr/offline, /dashboard), so its catch-all never
      // fires and nothing moves. The "signed out on this device" notice lives on the landing page,
      // so it was never seen either.
      //
      // history.replaceState rather than location.assign: a real navigation offline depends on the
      // service worker being active to serve index.html from cache, and being wrong about that
      // strands the user on the browser's error page — the exact failure this branch exists to
      // avoid. AppRoutes rebuilds the router when `user` changes, and the new router reads
      // window.location, so the swap below lands on the landing with no network involved.
      window.history.replaceState({}, '', env.VITE_BASE_PATH || '/');
      void clearLocalSession();
      setUser(undefined);
      return;
    }

    // Online: one redirect ends the realm session and comes home. Note what is NOT here — the
    // stored user is left in place for signoutRedirect() to read `id_token_hint` from and clear
    // itself. See services/keycloak.ts: clearing first leaves the realm session alive.
    //
    // Deliberately NOT setUser(undefined) either: a full-page navigation is imminent, and clearing
    // state could momentarily mount the Landing page, whose effect reads-and-clears the
    // SessionTimeout "session expired" flag before the round-trip returns — consuming the notice
    // signal early.
    void signOutRedirect();
  }, []);

  /**
   * Returns a non-expired access token, renewing first if it is close to expiry. Returns
   * {@code undefined} if the session cannot be renewed (caller should treat as signed-out).
   */
  const ensureFreshToken = useCallback(async (): Promise<string | undefined> => {
    const fresh = await ensureFreshUser();
    return fresh?.access_token;
  }, []);

  /**
   * Unconditional renewal (vs {@link ensureFreshToken}, which only renews near expiry): rotates the
   * refresh token, sliding the session, and re-parses the user. Throws if the refresh token has
   * expired, so the caller can treat it as a real expiry. Used by the SessionTimeout "Stay logged
   * in" action.
   */
  const forceRefreshSession = useCallback(async (): Promise<void> => {
    const renewed = await forceRenew();
    if (!renewed?.access_token) {
      throw new Error('Session refresh failed — no tokens.');
    }
    setUser(parseToken(decodeAccessToken(renewed.access_token)));
  }, []);

  const contextValue: AuthContextType = useMemo(
    () => ({
      user,
      isLoggedIn: !!user,
      isLoading,
      login,
      logout,
      completeSignIn,
      ensureFreshToken,
      forceRefreshSession,
    }),
    [user, isLoading, login, logout, completeSignIn, ensureFreshToken, forceRefreshSession],
  );

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>;
};

/**
 * Reads the claims out of an access token.
 *
 * <p>Decode only — the signature is verified by the API on every request, which is the only place
 * that verification means anything. A client that "validated" its own token would be asking the
 * token whether to trust the token.
 */
const decodeAccessToken = (accessToken: string): Record<string, unknown> | undefined => {
  try {
    const payload = accessToken.split('.')[1];
    if (!payload) return undefined;
    // base64url → base64, re-padded: JWT segments drop the '=' padding that atob() requires.
    const base64 = payload
      .replaceAll('-', '+')
      .replaceAll('_', '/')
      .padEnd(payload.length + ((4 - (payload.length % 4)) % 4), '=');
    // Decode as UTF-8 rather than trusting atob's latin-1 output — display names carry accents
    // (and a ministry suffix like "WLRS:EX"), and mangling them here would put the mojibake
    // straight into the header.
    //
    // codePointAt is exact rather than merely adequate here: atob returns a binary string whose
    // every character is a single code unit <= 0xFF, so there are no surrogate pairs for it and
    // charCodeAt to disagree about. The `?? 0` is for the type signature only — the callback is
    // never handed an empty string.
    const bytes = Uint8Array.from(atob(base64), (char) => char.codePointAt(0) ?? 0);
    return JSON.parse(new TextDecoder().decode(bytes)) as Record<string, unknown>;
  } catch {
    return undefined;
  }
};

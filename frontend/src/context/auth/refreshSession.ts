import { fetchAuthSession, signOut } from 'aws-amplify/auth';

import { env } from '@/env';

/**
 * Seconds before access-token expiry at which we consider it "stale" and
 * force a refresh via the refresh token.
 */
const REFRESH_MARGIN_SECONDS = 30;

/**
 * Minimum gap (ms) between two consecutive refresh attempts to prevent
 * concurrent calls from each triggering their own refresh.
 */
const MIN_REFRESH_GAP_MS = 5_000;

let refreshInFlight = false;
let lastRefreshTime = 0;

/** Sign out and hard-redirect to the app root (re-entering the IDIR login flow). */
async function signOutAndRedirect(): Promise<void> {
  try {
    await signOut();
  } catch {
    // Swallow secondary sign-out errors; we're going to hard-redirect anyway.
  }
  const basePath = (env.VITE_BASE_PATH ?? '').replace(/\/$/, '');
  window.location.href = window.location.origin + (basePath || '/');
}

/**
 * Ensures the Cognito access token is fresh before making an API call.
 *
 * <ul>
 *   <li>If the token has more than {@link REFRESH_MARGIN_SECONDS} remaining, returns immediately.</li>
 *   <li>If the token is about to expire, uses the refresh token to mint a new one.</li>
 *   <li>If there is no session, or the refresh token itself has expired (user idle too long),
 *       signs the user out and redirects to the login page.</li>
 * </ul>
 *
 * Concurrent calls are de-duplicated ({@link refreshInFlight}) and throttled
 * ({@link MIN_REFRESH_GAP_MS}). Call this at the top of every API request.
 */
export async function ensureSessionFresh(): Promise<void> {
  try {
    const { tokens } = (await fetchAuthSession({ forceRefresh: false })) ?? {};
    const accessToken = tokens?.accessToken;

    if (!accessToken) {
      // No session — sign out and redirect.
      await signOutAndRedirect();
      return;
    }

    const exp = accessToken.payload?.exp;
    if (!exp) {
      return;
    }

    const secondsRemaining = exp - Math.floor(Date.now() / 1000);
    if (secondsRemaining > REFRESH_MARGIN_SECONDS) {
      // Token is still fresh.
      return;
    }

    // Token is stale — needs a refresh.
    if (refreshInFlight) {
      // Another refresh is in progress; wait briefly and return.
      await new Promise((resolve) => setTimeout(resolve, 1_500));
      return;
    }

    const now = Date.now();
    if (now - lastRefreshTime < MIN_REFRESH_GAP_MS) {
      // Recently refreshed — skip.
      return;
    }

    refreshInFlight = true;
    lastRefreshTime = now;
    try {
      await fetchAuthSession({ forceRefresh: true });
    } finally {
      refreshInFlight = false;
    }
  } catch {
    // Refresh token expired or revoked — the session is over.
    refreshInFlight = false;
    await signOutAndRedirect();
  }
}

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
/**
 * Set once a sign-out redirect is under way. A page typically has several requests in flight, so
 * without this every one of them would fire its own signOut() and its own location assignment.
 */
let endingSession = false;

/**
 * Sign out and hard-redirect to the app root (re-entering the IDIR login flow).
 *
 * <p>Never while offline. Every API call runs {@link ensureSessionFresh} first, and offline that
 * finds either no token (the user signed out locally — see AuthProvider's offline branch) or a
 * refresh it cannot perform, because {@code fetchAuthSession} needs the network. Redirecting on
 * either would drag the user out of the device-local workflow the offline route set exists to serve,
 * and straight back again on the next request: the redirect reloads the page, the reloaded page
 * calls the API, and round it goes. There is also nowhere useful to send them — IDIR login cannot
 * run without a connection.
 *
 * <p>Offline the request simply proceeds and fails as a network error, which the callers already
 * present as an offline state.
 */
async function signOutAndRedirect(): Promise<void> {
  if (endingSession) return;
  if (!navigator.onLine) return;
  endingSession = true;
  try {
    await signOut();
  } catch {
    // Swallow secondary sign-out errors; we're going to hard-redirect anyway.
  }
  const basePath = (env.VITE_BASE_PATH ?? '').replace(/\/$/, '');
  window.location.href = window.location.origin + (basePath || '/');
}

/**
 * React to a 401 from the API: end the session and send the user back through login.
 *
 * <p>{@link ensureSessionFresh} is proactive — it inspects the token before a request goes out — so
 * it cannot catch a token the SERVER rejects while the client still believes it is valid: clock
 * skew, a revoked session, a rotated signing key, or simply a token with more than
 * {@link REFRESH_MARGIN_SECONDS} left that the backend refuses. Without this, those surfaced as an
 * ordinary error toast and the user sat on a dead page with no hint to sign in again.
 *
 * <p>Does nothing when there is no session to end. That case is not a timeout — it is an
 * unauthenticated call from a signed-out user, and redirecting would bounce them to the root they
 * are already on, potentially in a loop if that page calls the API too. The error surfaces normally
 * instead.
 *
 * @returns true when the session was ended and a redirect is under way.
 */
export async function handleUnauthorized(): Promise<boolean> {
  if (endingSession) return true;
  try {
    const { tokens } = (await fetchAuthSession({ forceRefresh: false })) ?? {};
    if (!tokens?.accessToken) return false;
  } catch {
    return false;
  }
  await signOutAndRedirect();
  return true;
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

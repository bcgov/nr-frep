import { env } from '@/env';
import { clearLocalSession, ensureFreshUser, getUserManager } from '@/services/keycloak';

/**
 * Set once a sign-out redirect is under way. A page typically has several requests in flight, so
 * without this every one of them would fire its own sign-out and its own location assignment.
 */
let endingSession = false;

/**
 * End the local session and hard-redirect to the app root (re-entering the login flow).
 *
 * <p>Never while offline. Every API call runs {@link ensureSessionFresh} first, and offline that
 * finds either no token (the user signed out locally — see AuthProvider's offline branch) or a
 * renewal it cannot perform, because the token endpoint needs the network. Redirecting on either
 * would drag the user out of the device-local workflow the offline route set exists to serve, and
 * straight back again on the next request: the redirect reloads the page, the reloaded page calls
 * the API, and round it goes. There is also nowhere useful to send them — IDIR login cannot run
 * without a connection.
 *
 * <p>Offline the request simply proceeds and fails as a network error, which the callers already
 * present as an offline state.
 *
 * <p>This is the expiry path, not the user-initiated one: the realm session is already gone (the
 * refresh token expired), so there is nothing to attribute a federated logout to and the local
 * clear is the whole job. Deliberate sign-out goes through `signOutRedirect` instead.
 */
async function signOutAndRedirect(): Promise<void> {
  if (endingSession) return;
  if (!navigator.onLine) return;
  endingSession = true;
  await clearLocalSession();
  const basePath = (env.VITE_BASE_PATH ?? '').replace(/\/$/, '');
  window.location.href = window.location.origin + (basePath || '/');
}

/**
 * React to a 401 from the API: end the session and send the user back through login.
 *
 * <p>{@link ensureSessionFresh} is proactive — it inspects the token before a request goes out — so
 * it cannot catch a token the SERVER rejects while the client still believes it is valid: clock
 * skew, a revoked session, a rotated signing key, or simply a token with more than the renewal
 * margin left that the backend refuses. Without this, those surfaced as an
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
    const current = await getUserManager().getUser();
    if (!current?.access_token) return false;
  } catch {
    return false;
  }
  await signOutAndRedirect();
  return true;
}

/**
 * Ensures the access token is fresh before making an API call.
 *
 * <ul>
 *   <li>If the token has more than the renewal margin remaining, returns immediately.</li>
 *   <li>If the token is about to expire, uses the refresh token to mint a new one.</li>
 *   <li>If there is no session, or the refresh token itself has expired (user idle too long),
 *       signs the user out and redirects to the login page.</li>
 * </ul>
 *
 * Concurrent calls share one in-flight renewal and are throttled — see `services/keycloak.ts`.
 * Call this at the top of every API request.
 */
export async function ensureSessionFresh(): Promise<void> {
  const fresh = await ensureFreshUser();
  if (!fresh?.access_token) {
    await signOutAndRedirect();
  }
}

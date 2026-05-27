import { fetchAuthSession, signOut } from 'aws-amplify/auth';

/**
 * Number of seconds before access-token expiry at which we refresh proactively.
 * Keeps the SPA from racing a 401 when a long-running request fires after the
 * tab has been idle.
 */
const REFRESH_MARGIN_SECONDS = 30;

/**
 * Ensure the Cognito session is fresh. If the access token is missing or
 * within {@link REFRESH_MARGIN_SECONDS} of its expiry, the refresh token is
 * used to mint a new pair. If refresh fails (refresh token also expired), the
 * user is signed out and redirected to the landing page.
 */
export async function ensureSessionFresh(): Promise<void> {
  try {
    const session = await fetchAuthSession({ forceRefresh: false });
    const exp = session.tokens?.accessToken?.payload?.exp;
    const nowSeconds = Math.floor(Date.now() / 1000);

    if (typeof exp === 'number' && exp - nowSeconds <= REFRESH_MARGIN_SECONDS) {
      // Token expires soon — force a refresh.
      await fetchAuthSession({ forceRefresh: true });
    }
  } catch {
    // Refresh failed (refresh token expired or network failure) — sign out
    // cleanly so the next render funnels the user back through IDIR.
    try {
      await signOut();
    } catch {
      // Swallow secondary sign-out errors; we're going to hard-redirect anyway.
    }
    window.location.assign('/');
  }
}

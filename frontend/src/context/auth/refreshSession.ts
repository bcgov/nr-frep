// LOCAL DEV: Cognito session refresh disabled.
export async function ensureSessionFresh(): Promise<void> {
  return;
}

/*
 * --- Cognito session refresh (re-enable before deploying) ---
 *
 * import { fetchAuthSession, signOut } from 'aws-amplify/auth';
 * ... original ensureSessionFresh implementation ...
 */

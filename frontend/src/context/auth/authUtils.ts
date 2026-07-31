import {
  AVAILABLE_ROLES,
  validIdpProviders,
  type FamLoginUser,
  type IdpProviderType,
  type JWT,
  type ROLE_TYPE,
  type USER_PRIVILEGE_TYPE,
} from './types';

import { env } from '@/env';

// ── Cookie helpers ───────────────────────────────────────────────────

/** Reads a browser cookie value by name. Returns '' if not found. */
export const getCookie = (name: string): string => {
  const cookie = document.cookie
    .split(';')
    .find((cookieValue) => cookieValue.trim().startsWith(name));
  return cookie ? (cookie.split('=')[1] ?? '') : '';
};

/**
 * Note on token reads: Amplify's {@code configure()} re-seeds the token store to its default
 * {@code localStorage} (see {@link clearStoredTokens} and main.tsx), so Cognito tokens are NOT
 * available as DOM-visible cookies. Read them via {@code fetchAuthSession} (storage-agnostic)
 * instead — see {@code services/http/headers.ts}. The only cookie we read directly is the
 * backend-set XSRF token, via {@link getCookie} above.
 */

/**
 * Removes every Amplify token/session entry for the configured app client. Used by the federated
 * logout path ({@code buildFederatedLogoutUrl}), which drives the sign-out redirect chain itself
 * (Siteminder → Keycloak → Cognito → app) instead of Amplify's {@code signOut()}: clearing the local
 * tokens up front means the browser lands back on the app with no session and renders the logged-out
 * Landing. The chain's final Cognito /logout hop clears the Cognito session cookie server-side.
 *
 * <p>Storage note: despite the {@code CookieStorage} override in main.tsx, Amplify's
 * {@code Amplify.configure()} re-seeds the token store to its default {@code localStorage} on first
 * call (aws-amplify initSingleton.mjs, the {@code !Amplify.libraryOptions.Auth} branch), so tokens
 * actually live in localStorage — reads go through {@code fetchAuthSession}, which is storage-agnostic.
 * We therefore clear the {@code CognitoIdentityServiceProvider.<clientId>.*} keys from localStorage;
 * clearing cookies here would be a silent no-op and leave the local session intact after logout.
 */
export const clearStoredTokens = (): void => {
  const prefix = `CognitoIdentityServiceProvider.${env.VITE_USER_POOLS_WEB_CLIENT_ID}`;
  try {
    const keys: string[] = [];
    for (let i = 0; i < window.localStorage.length; i++) {
      const key = window.localStorage.key(i);
      if (key && key.startsWith(prefix)) keys.push(key);
    }
    keys.forEach((key) => window.localStorage.removeItem(key));
  } catch {
    /* storage disabled / unavailable — nothing to clear */
  }
};

/**
 * Parses a Cognito ID token JWT into the app's FamLoginUser shape.
 * Extracts display name, IDP provider, Cognito groups → roles.
 *
 * NOTE: This must be called with the **ID token**, not the access token,
 * because only the ID token carries the `custom:idp_*` profile claims.
 */
export const parseToken = (idToken: JWT | undefined): FamLoginUser | undefined => {
  if (!idToken) return undefined;
  const decodedIdToken = idToken?.payload;
  const displayName = (decodedIdToken?.['custom:idp_display_name'] as string) || '';
  const idpProvider = validIdpProviders.includes(
    (decodedIdToken?.['custom:idp_name'] as string)?.toUpperCase() as IdpProviderType,
  )
    ? ((decodedIdToken?.['custom:idp_name'] as string).toUpperCase() as IdpProviderType)
    : undefined;
  const hasComma = displayName.includes(',');
  let [lastName, firstName] = hasComma ? displayName.split(', ') : displayName.split(' ');
  if (!hasComma) [lastName, firstName] = [firstName, lastName];
  const sanitizedFirstName = hasComma ? firstName?.split(' ')[0]?.trim() : firstName || '';
  const userName = (decodedIdToken?.['custom:idp_username'] as string) || '';
  const email = (decodedIdToken?.['email'] as string) || '';
  const cognitoGroups = extractGroups(decodedIdToken);
  const privileges = parsePrivileges(cognitoGroups);
  const derivedRoles = Object.keys(privileges) as ROLE_TYPE[];
  // The backend (JwtPrincipalUtil) stores userids with the legacy WebADE source-directory prefix,
  // normalizing FAM's "BCEIDBUSINESS" to "BCEID". Mirror that here so providerUsername matches the
  // backend-stored userid (e.g. the assessedBy "Assign it to me" comparison). idpProvider keeps the
  // accurate FAM provider name for display.
  const userIdPrefix = idpProvider === 'BCEIDBUSINESS' ? 'BCEID' : idpProvider;
  return {
    userName,
    displayName,
    email,
    idpProvider,
    privileges,
    roles: derivedRoles,
    firstName: sanitizedFirstName,
    lastName,
    providerUsername: `${userIdPrefix}\\${userName}`,
  };
};

/**
 * Parses Cognito group strings into a user privilege object.
 *
 * Recognizes groups that exactly match {@link AVAILABLE_ROLES}
 * (e.g. "FREP_ADMIN", "FREP_EDITOR", "FREP_VIEW_ONLY").
 * Unrecognized groups are silently ignored.
 *
 * @param {string[]} input - Array of group strings from Cognito.
 * @returns {USER_PRIVILEGE_TYPE} The parsed privilege object.
 */
export function parsePrivileges(input: string[]): USER_PRIVILEGE_TYPE {
  const result: USER_PRIVILEGE_TYPE = {};
  for (const item of input) {
    // Direct match against known Cognito groups (legacy WebADE role names)
    if (AVAILABLE_ROLES.includes(item as ROLE_TYPE)) {
      result[item as ROLE_TYPE] = null; // null = global (non-scoped) role
    }
  }
  return result;
}

/**
 * Extracts Cognito groups from a decoded JWT payload.
 * @param {object | undefined} decodedIdToken - The decoded JWT payload.
 * @returns {string[]} Array of group strings, or empty array if none found.
 */
export function extractGroups(decodedIdToken: object | undefined): string[] {
  if (!decodedIdToken) return [];
  if ('cognito:groups' in decodedIdToken) {
    return decodedIdToken['cognito:groups'] as string[];
  }
  return [];
}

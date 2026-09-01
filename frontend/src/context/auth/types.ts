import type { JWT as AmplifyJWT } from '@aws-amplify/core';

export type JWT = AmplifyJWT;

/**
 * Application roles derived from Cognito groups.
 *
 * FREP_ADMIN / FREP_EDITOR are raw Cognito groups (renamed from FREP_SYS_ADMIN / FREP_UPDATE). The
 * legacy read-only group FREP_VIEW_ONLY has been retired from FREP. FREP_CHR_EDITOR is a
 * **synthetic aggregate** — there is no raw group of that name; it is derived when the user holds one
 * or more per-district {@link CHR_DISTRICT_EDITOR_PREFIX} groups, and its privilege value carries the
 * district codes (a scoped role: `string[]` rather than `null`).
 */
export const AVAILABLE_ROLES = ['FREP_ADMIN', 'FREP_EDITOR', 'FREP_CHR_EDITOR'] as const;

export type ROLE_TYPE = (typeof AVAILABLE_ROLES)[number];

/** Prefix of the per-district CHR editor Cognito groups (FAM V92): `FREP_CHR_EDITOR_DISTRICT_<code>`. */
export const CHR_DISTRICT_EDITOR_PREFIX = 'FREP_CHR_EDITOR_DISTRICT_';

type RoleValue = string[] | null;

export type USER_PRIVILEGE_TYPE = Partial<Record<ROLE_TYPE, RoleValue>>;

export const validIdpProviders = ['IDIR', 'BCEIDBUSINESS'] as const;

export type IdpProviderType = (typeof validIdpProviders)[number];

/** Identity provider the user picks at login. Mirrors nr-fspts. */
export type LoginProvider = 'idir' | 'bceid';

export type FamLoginUser = {
  providerUsername?: string;
  userName?: string;
  displayName?: string;
  email?: string;
  idpProvider?: IdpProviderType;
  roles?: ROLE_TYPE[];
  authToken?: string;
  exp?: number;
  privileges: USER_PRIVILEGE_TYPE;
  firstName?: string;
  lastName?: string;
};

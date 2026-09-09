/** Decoded JWT claims, as `oidc-client-ts` hands them over (`User.profile` / the access token). */
export type JwtClaims = Record<string, unknown>;

/**
 * Application roles derived from the roles on the access token.
 *
 * FREP_ADMINISTRATOR / FREP_EDITOR are raw CSS role names. The admin role has been renamed twice —
 * WebADE FREP_SYS_ADMIN, then FAM/Cognito FREP_ADMIN, now CSS FREP_ADMINISTRATOR; FREP_EDITOR was
 * WebADE FREP_UPDATE. The
 * legacy read-only role FREP_VIEW_ONLY has been retired from FREP. FREP_CHR_EDITOR is a
 * **synthetic aggregate** — there is no raw role of that name; it is derived when the user holds one
 * or more per-district {@link CHR_DISTRICT_EDITOR_PREFIX} groups, and its privilege value carries the
 * district codes (a scoped role: `string[]` rather than `null`).
 */
export const AVAILABLE_ROLES = ['FREP_ADMINISTRATOR', 'FREP_EDITOR', 'FREP_CHR_EDITOR'] as const;

export type ROLE_TYPE = (typeof AVAILABLE_ROLES)[number];

/**
 * Prefix of the per-district CHR editor roles: `FREP_CHR_EDITOR_DISTRICT-<code>`.
 *
 * **The trailing separator is a HYPHEN, not an underscore, and it is not a typo.** CSS defines one
 * role, `FREP_CHR_EDITOR`, with the district as a *scope* on the grant; FAM flattens the pair into a
 * single Keycloak role string joined with `-`, so a user scoped to DCC arrives as
 * `client_roles: ["FREP_CHR_EDITOR_DISTRICT-DCC", "FREP_EDITOR"]`. Verified against a real DEV token
 * on 2026-09-09. The legacy FAM/Cognito roles used underscores throughout
 * (`FREP_CHR_EDITOR_DISTRICT_DCC`), which is why this reads as a mistake — changing it back silently
 * removes every district editor's CHR access. Mirrored by `RoleConstants` on the backend.
 */
export const CHR_DISTRICT_EDITOR_PREFIX = 'FREP_CHR_EDITOR_DISTRICT-';

type RoleValue = string[] | null;

export type USER_PRIVILEGE_TYPE = Partial<Record<ROLE_TYPE, RoleValue>>;

/**
 * Identity providers, in the normalized form the app uses. The realm reports IDIR - MFA as
 * `azureidir` and BCeID Business as `bceidbusiness`; both are folded to these before anything else
 * sees them — see `normalizeProvider` in authUtils.
 */
export const validIdpProviders = ['IDIR', 'BCEIDBUSINESS'] as const;

export type IdpProviderType = (typeof validIdpProviders)[number];

/** Identity provider the user picks at login; maps to a `kc_idp_hint` in services/keycloak.ts. */
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

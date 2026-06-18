import type { JWT as AmplifyJWT } from '@aws-amplify/core';

export type JWT = AmplifyJWT;

/**
 * Recognized Cognito groups that map to application roles.
 * FREP_VIEW_ONLY matches legacy WebADE verbatim; admin and editor roles were
 * renamed in Cognito from FREP_SYS_ADMIN → FREP_ADMIN and FREP_UPDATE → FREP_EDITOR.
 */
export const AVAILABLE_ROLES = ['FREP_ADMIN', 'FREP_EDITOR', 'FREP_VIEW_ONLY'] as const;

export type ROLE_TYPE = (typeof AVAILABLE_ROLES)[number];

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

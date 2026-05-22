import type { JWT as AmplifyJWT } from '@aws-amplify/core';

export type JWT = AmplifyJWT;

/**
 * Recognized Cognito groups that map to application roles.
 * Names match legacy WebADE roles (FrepUser.SYSADMIN / UPDATE / VIEW_ONLY).
 */
export const AVAILABLE_ROLES = ['FREP_SYS_ADMIN', 'FREP_UPDATE', 'FREP_VIEW_ONLY'] as const;

export type ROLE_TYPE = (typeof AVAILABLE_ROLES)[number];

type RoleValue = string[] | null;

export type USER_PRIVILEGE_TYPE = Partial<Record<ROLE_TYPE, RoleValue>>;

export const validIdpProviders = ['IDIR'] as const;

export type IdpProviderType = (typeof validIdpProviders)[number];

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

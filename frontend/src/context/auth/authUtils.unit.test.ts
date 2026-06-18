import { describe, expect, it } from 'vitest';

import { parseToken } from './authUtils';

import type { JWT } from './types';

/** Build a minimal JWT stand-in — parseToken only reads `.payload`. */
const jwt = (payload: Record<string, unknown>): JWT => ({ payload }) as unknown as JWT;

describe('parseToken', () => {
  it('returns undefined when no token is provided', () => {
    expect(parseToken(undefined)).toBeUndefined();
  });

  it('parses an IDIR token: provider, username, and roles from cognito:groups', () => {
    const user = parseToken(
      jwt({
        'custom:idp_name': 'idir',
        'custom:idp_username': 'JSMITH',
        'custom:idp_display_name': 'Smith, John',
        'email': 'john.smith@gov.bc.ca',
        'cognito:groups': ['FREP_EDITOR'],
      }),
    );

    expect(user?.idpProvider).toBe('IDIR');
    expect(user?.providerUsername).toBe(String.raw`IDIR\JSMITH`);
    expect(user?.firstName).toBe('John');
    expect(user?.lastName).toBe('Smith');
    expect(user?.email).toBe('john.smith@gov.bc.ca');
    expect(user?.roles).toEqual(['FREP_EDITOR']);
  });

  it('parses a BCeID Business token: provider kept as FAM name, username prefix normalized to BCEID', () => {
    const user = parseToken(
      jwt({
        'custom:idp_name': 'bceidbusiness',
        'custom:idp_username': 'CONTRACTOR1',
        'custom:idp_display_name': 'Doe, Jane',
        'email': 'jane@example.com',
        'cognito:groups': ['FREP_VIEW_ONLY'],
      }),
    );

    // idpProvider keeps the accurate FAM provider for display...
    expect(user?.idpProvider).toBe('BCEIDBUSINESS');
    // ...but providerUsername mirrors the backend-stored userid (BCEIDBUSINESS normalized to BCEID).
    expect(user?.providerUsername).toBe(String.raw`BCEID\CONTRACTOR1`);
    expect(user?.roles).toEqual(['FREP_VIEW_ONLY']);
  });

  it('leaves idpProvider undefined for an unrecognized provider', () => {
    const user = parseToken(
      jwt({
        'custom:idp_name': 'somethingelse',
        'custom:idp_username': 'X',
      }),
    );
    expect(user?.idpProvider).toBeUndefined();
  });
});

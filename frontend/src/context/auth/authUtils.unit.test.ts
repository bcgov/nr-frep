import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/env', () => ({ env: { VITE_USER_POOLS_WEB_CLIENT_ID: 'test-client' } }));

import { clearStoredTokens, parseToken } from './authUtils';

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

describe('clearStoredTokens', () => {
  const prefix = 'CognitoIdentityServiceProvider.test-client';

  beforeEach(() => window.localStorage.clear());
  afterEach(() => window.localStorage.clear());

  it('removes every Amplify token entry for the configured client', () => {
    window.localStorage.setItem(`${prefix}.LastAuthUser`, 'idir\\jsmith');
    window.localStorage.setItem(`${prefix}.idir\\jsmith.accessToken`, 'a.b.c');
    window.localStorage.setItem(`${prefix}.idir\\jsmith.idToken`, 'd.e.f');
    window.localStorage.setItem(`${prefix}.idir\\jsmith.refreshToken`, 'ghi');
    window.localStorage.setItem(`${prefix}.idir\\jsmith.clockDrift`, '0');

    clearStoredTokens();

    const remaining = Object.keys(window.localStorage).filter((k) => k.startsWith(prefix));
    expect(remaining).toEqual([]);
  });

  it("leaves unrelated keys (and another client's tokens) untouched", () => {
    window.localStorage.setItem(`${prefix}.LastAuthUser`, 'idir\\jsmith');
    window.localStorage.setItem('theme', 'dark');
    window.localStorage.setItem('CognitoIdentityServiceProvider.other-client.LastAuthUser', 'x');

    clearStoredTokens();

    expect(window.localStorage.getItem(`${prefix}.LastAuthUser`)).toBeNull();
    expect(window.localStorage.getItem('theme')).toBe('dark');
    expect(
      window.localStorage.getItem('CognitoIdentityServiceProvider.other-client.LastAuthUser'),
    ).toBe('x');
  });

  it('is a no-op when there is nothing to clear', () => {
    expect(() => clearStoredTokens()).not.toThrow();
    expect(window.localStorage).toHaveLength(0);
  });
});

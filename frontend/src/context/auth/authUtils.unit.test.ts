import { describe, expect, it, vi } from 'vitest';

vi.mock('@/env', () => ({ env: { VITE_KEYCLOAK_CLIENT_ID: 'frep-app' } }));

import { extractRoles, normalizeProvider, parseToken } from './authUtils';

describe('parseToken', () => {
  it('returns undefined when there are no claims', () => {
    expect(parseToken(undefined)).toBeUndefined();
  });

  it('parses an IDIR token: provider, username, and roles from client_roles', () => {
    const user = parseToken({
      identity_provider: 'azureidir',
      idir_username: 'JSMITH',
      display_name: 'Smith, John',
      email: 'john.smith@gov.bc.ca',
      client_roles: ['FREP_EDITOR'],
    });

    expect(user?.idpProvider).toBe('IDIR');
    expect(user?.providerUsername).toBe(String.raw`IDIR\JSMITH`);
    expect(user?.firstName).toBe('John');
    expect(user?.lastName).toBe('Smith');
    expect(user?.email).toBe('john.smith@gov.bc.ca');
    expect(user?.roles).toEqual(['FREP_EDITOR']);
  });

  it('parses a BCeID Business token, normalizing the username prefix to BCEID', () => {
    const user = parseToken({
      identity_provider: 'bceidbusiness',
      bceid_username: 'CONTRACTOR1',
      display_name: 'Doe, Jane',
      email: 'jane@example.com',
      client_roles: ['FREP_EDITOR'],
    });

    // idpProvider keeps the accurate provider for display...
    expect(user?.idpProvider).toBe('BCEIDBUSINESS');
    // ...but providerUsername mirrors the backend-stored userid (BCEIDBUSINESS -> BCEID).
    expect(user?.providerUsername).toBe(String.raw`BCEID\CONTRACTOR1`);
    expect(user?.roles).toEqual(['FREP_EDITOR']);
  });

  it('leaves idpProvider undefined for an unrecognized provider', () => {
    expect(
      parseToken({ identity_provider: 'somethingelse', idir_username: 'X' })?.idpProvider,
    ).toBeUndefined();
  });

  it('ignores a bare FREP_CHR_EDITOR rather than granting every district', () => {
    // The role with no district scope flattened onto it — reachable if someone is granted it in FAM
    // without picking a district. It must NOT become a global role: the backend only counts prefixed
    // district roles, so "all districts" in the UI would mean 403 on every one of them.
    const user = parseToken({
      identity_provider: 'azureidir',
      idir_username: 'JSMITH',
      client_roles: ['FREP_CHR_EDITOR', 'FREP_EDITOR'],
    });

    expect(user?.privileges.FREP_CHR_EDITOR).toBeUndefined();
    expect(user?.roles).toEqual(['FREP_EDITOR']);
  });

  it('reads the real token shape: one role string per district, hyphen-separated', () => {
    // Verbatim from a TEST token on 2026-09-09, for an account holding CHR in TWO districts.
    // FAM flattens each (role, scope) pair into its own Keycloak role string rather than
    // combining the scopes into one entry — so a multi-district grant is several roles, not one
    // role carrying a list. A combined "…DISTRICT-DCC,DCS" would parse as a single district named
    // "DCC,DCS", match no org unit, and lock the user out of both.
    const user = parseToken({
      identity_provider: 'azureidir',
      idir_username: 'ASODHI',
      display_name: 'Sodhi, Avisha WLRS:EX',
      client_roles: [
        'FREP_CHR_EDITOR_DISTRICT-DCC',
        'FREP_CHR_EDITOR_DISTRICT-DCS',
        'FREP_EDITOR',
      ],
    });

    expect(user?.privileges.FREP_CHR_EDITOR).toEqual(['DCC', 'DCS']);
    expect(user?.roles).toEqual(expect.arrayContaining(['FREP_EDITOR', 'FREP_CHR_EDITOR']));
    // The ministry suffix on display_name must not leak into the first name.
    expect(user?.firstName).toBe('Avisha');
    expect(user?.lastName).toBe('Sodhi');
  });

  it('collapses per-district CHR roles into a scoped FREP_CHR_EDITOR role', () => {
    const user = parseToken({
      identity_provider: 'azureidir',
      idir_username: 'JSMITH',
      client_roles: ['FREP_CHR_EDITOR_DISTRICT-DCK', 'FREP_CHR_EDITOR_DISTRICT-DCC'],
    });

    // Surfaces as the synthetic FREP_CHR_EDITOR role (so hasAnyRole works) with the district codes.
    expect(user?.roles).toEqual(['FREP_CHR_EDITOR']);
    expect(user?.privileges.FREP_CHR_EDITOR).toEqual(['DCC', 'DCK']); // de-duped + sorted
  });
});

describe('normalizeProvider — the azureidir trap', () => {
  it('folds every IDIR alias, in any case, onto IDIR', () => {
    // azureidir is what the realm actually reports: FREP's CSS integration selects IDIR - MFA.
    // Mapping it verbatim would build userids matching nothing the backend has stored.
    expect(normalizeProvider('azureidir')).toBe('IDIR');
    expect(normalizeProvider('AzureIDIR')).toBe('IDIR');
    // The non-MFA broker folds identically, so switching the integration changes no stored string.
    expect(normalizeProvider('idir')).toBe('IDIR');
  });

  it('folds the BCeID aliases onto BCEIDBUSINESS', () => {
    expect(normalizeProvider('bceidbusiness')).toBe('BCEIDBUSINESS');
    expect(normalizeProvider('BCEIDBASIC')).toBe('BCEIDBUSINESS');
  });

  it('returns undefined for an empty or unknown provider', () => {
    expect(normalizeProvider(undefined)).toBeUndefined();
    expect(normalizeProvider('')).toBeUndefined();
    expect(normalizeProvider('twitter')).toBeUndefined();
  });
});

describe('username resolution — GUIDs are case-folded, usernames are not', () => {
  it('prefers idir_username and passes it through exactly as issued', () => {
    const user = parseToken({
      identity_provider: 'azureidir',
      idir_username: 'JSmith',
      idir_user_guid: '0a1b2c3d',
      preferred_username: '0a1b2c3d@azureidir',
    });
    expect(user?.providerUsername).toBe(String.raw`IDIR\JSmith`);
  });

  it('upper-cases the GUID whichever claim it falls back to, so one person is one identity', () => {
    const fromPreferred = parseToken({
      identity_provider: 'azureidir',
      preferred_username: '0a1b2c3d4e5f60718293a4b5c6d7e8f9@azureidir',
    });
    const fromGuidClaim = parseToken({
      identity_provider: 'azureidir',
      idir_user_guid: '0A1B2C3D4E5F60718293A4B5C6D7E8F9',
    });

    expect(fromPreferred?.providerUsername).toBe(String.raw`IDIR\0A1B2C3D4E5F60718293A4B5C6D7E8F9`);
    expect(fromGuidClaim?.providerUsername).toBe(fromPreferred?.providerUsername);
  });
});

describe('extractRoles', () => {
  it('reads the flat client_roles claim that CSS emits', () => {
    expect(extractRoles({ client_roles: ['FREP_ADMINISTRATOR'] })).toEqual(['FREP_ADMINISTRATOR']);
  });

  it('reads resource_access.<client>.roles, which stock Keycloak uses instead', () => {
    expect(extractRoles({ resource_access: { 'frep-app': { roles: ['FREP_EDITOR'] } } })).toEqual([
      'FREP_EDITOR',
    ]);
  });

  it('ignores roles belonging to another client', () => {
    expect(extractRoles({ resource_access: { account: { roles: ['manage-account'] } } })).toEqual(
      [],
    );
  });

  it('merges both locations without duplicating', () => {
    expect(
      extractRoles({
        client_roles: ['FREP_ADMINISTRATOR'],
        resource_access: { 'frep-app': { roles: ['FREP_ADMINISTRATOR', 'FREP_EDITOR'] } },
      }),
    ).toEqual(['FREP_ADMINISTRATOR', 'FREP_EDITOR']);
  });

  it("drops FAM's per-grant expiry bookkeeping roles", () => {
    // FAM assigns expiry as a role on the person; it is not a role anyone holds.
    expect(
      extractRoles({ client_roles: ['FREP_EDITOR', 'FAM:EXPIRES:2026-09-30:FREP_EDITOR'] }),
    ).toEqual(['FREP_EDITOR']);
  });

  it('returns an empty array when there are no roles at all', () => {
    expect(extractRoles(undefined)).toEqual([]);
    expect(extractRoles({})).toEqual([]);
  });
});

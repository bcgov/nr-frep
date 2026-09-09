import { act, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const { getUserMock } = vi.hoisted(() => ({ getUserMock: vi.fn() }));
vi.mock('@/services/keycloak', () => ({
  getUserManager: () => ({ getUser: getUserMock, signinCallback: vi.fn() }),
  signIn: vi.fn(),
  signOutRedirect: vi.fn(),
  clearLocalSession: vi.fn(),
  ensureFreshUser: vi.fn(),
  forceRenew: vi.fn(),
}));
vi.mock('@/env', () => ({
  env: { VITE_BASE_PATH: '', VITE_KEYCLOAK_CLIENT_ID: 'forest-and-range-evaluation-program-6538' },
}));

import { AuthProvider } from './AuthProvider';
import { useAuth } from './useAuth';

/**
 * Covers the token → user path, including the base64url and UTF-8 handling inside AuthProvider's
 * private `decodeAccessToken`. That function is not exported and never throws — a decode bug
 * surfaces as "signed out" or as mojibake in the header, so nothing else would catch it.
 */

/** Encodes claims the way a real JWT segment is encoded: base64url, no padding. */
const jwt = (claims: Record<string, unknown>): string => {
  const b64url = (o: unknown) =>
    btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(o))))
      .replaceAll('+', '-')
      .replaceAll('/', '_')
      .replace(/=+$/, '');
  return `${b64url({ alg: 'RS256' })}.${b64url(claims)}.signature`;
};

const Probe = () => {
  const { user } = useAuth();
  return (
    <div>
      <span data-testid="provider">{user?.idpProvider ?? '-'}</span>
      <span data-testid="userid">{user?.providerUsername ?? '-'}</span>
      <span data-testid="first">{user?.firstName ?? '-'}</span>
      <span data-testid="last">{user?.lastName ?? '-'}</span>
      <span data-testid="districts">{user?.privileges.FREP_CHR_EDITOR?.join(',') ?? '-'}</span>
      <span data-testid="roles">{user?.roles?.join(',') ?? '-'}</span>
    </div>
  );
};

const renderProvider = async () => {
  await act(async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );
  });
};

beforeEach(() => vi.clearAllMocks());

describe('AuthProvider hydration from a real access token', () => {
  it('decodes the DEV token shape into a user', async () => {
    // The exact claim set a DEV token carried on 2026-09-09.
    getUserMock.mockResolvedValue({
      access_token: jwt({
        iss: 'https://dev.loginproxy.gov.bc.ca/auth/realms/standard',
        azp: 'forest-and-range-evaluation-program-6538',
        identity_provider: 'azureidir',
        idir_username: 'ASODHI',
        idir_user_guid: '5029B0D70AE24DD39D64A2CA10BFBA59',
        preferred_username: '5029b0d70ae24dd39d64a2ca10bfba59@azureidir',
        display_name: 'Sodhi, Avisha WLRS:EX',
        email: 'avisha.sodhi@gov.bc.ca',
        client_roles: ['FREP_CHR_EDITOR_DISTRICT-DCC', 'FREP_EDITOR'],
      }),
    });

    await renderProvider();

    expect(screen.getByTestId('provider').textContent).toBe('IDIR');
    expect(screen.getByTestId('userid').textContent).toBe(String.raw`IDIR\ASODHI`);
    // The ministry suffix must not leak into the first name.
    expect(screen.getByTestId('first').textContent).toBe('Avisha');
    expect(screen.getByTestId('last').textContent).toBe('Sodhi');
    expect(screen.getByTestId('districts').textContent).toBe('DCC');
    expect(screen.getByTestId('roles').textContent).toContain('FREP_EDITOR');
  });

  it('survives base64url padding and non-ASCII in the claims', async () => {
    // A JWT segment drops its '=' padding and uses -/_ instead of +//, and BC Gov display names
    // carry accents. Mishandling either yields a signed-out user or mojibake in the header.
    getUserMock.mockResolvedValue({
      access_token: jwt({
        identity_provider: 'azureidir',
        idir_username: 'JBOHM',
        display_name: 'Böhm, Café — ÿþ',
        client_roles: ['FREP_EDITOR'],
      }),
    });

    await renderProvider();

    expect(screen.getByTestId('last').textContent).toBe('Böhm');
    expect(screen.getByTestId('first').textContent).toBe('Café');
  });

  it('renders signed out when there is no session', async () => {
    getUserMock.mockResolvedValue(null);
    await renderProvider();
    expect(screen.getByTestId('provider').textContent).toBe('-');
  });

  it('renders signed out rather than throwing on a malformed token', async () => {
    getUserMock.mockResolvedValue({ access_token: 'not-a-jwt' });
    await renderProvider();
    expect(screen.getByTestId('provider').textContent).toBe('-');
  });
});

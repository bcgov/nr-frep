import { describe, expect, it, vi } from 'vitest';

vi.mock('@/config/fam/config', () => ({ COGNITO_HOSTED_UI_DOMAIN: 'cognito.example.com' }));

const { envMock } = vi.hoisted(() => ({ envMock: {} as Record<string, string> }));
vi.mock('@/env', () => ({ env: envMock }));

import { buildFederatedLogoutUrl } from './logoutChain';

const setEnv = (over: Record<string, string>) => {
  for (const key of Object.keys(envMock)) delete envMock[key];
  Object.assign(envMock, over);
};

const ALL_ENV = {
  VITE_LOGOUT_SITEMINDER_URL: 'https://sm/logoff.cgi',
  VITE_LOGOUT_KEYCLOAK_URL: 'https://kc/logout',
  VITE_LOGOUT_KEYCLOAK_CLIENT_ID: 'kc-client',
  VITE_USER_POOLS_WEB_CLIENT_ID: 'cognito-client',
};

describe('buildFederatedLogoutUrl', () => {
  it('returns null when any required env var is missing', () => {
    setEnv({ VITE_LOGOUT_SITEMINDER_URL: 'https://sm/logoff.cgi' }); // the rest are absent
    expect(buildFederatedLogoutUrl('https://app')).toBeNull();
  });

  it('nests Siteminder → Keycloak → Cognito → app, encoding each layer once', () => {
    setEnv(ALL_ENV);
    const url = buildFederatedLogoutUrl('https://app/');

    // Outermost hop is Siteminder logoff with retnow=1 and the (encoded) Keycloak URL as returl.
    expect(url).toMatch(/^https:\/\/sm\/logoff\.cgi\?retnow=1&returl=/);

    // Peel one layer → the Keycloak end-session URL, carrying its client_id + the Cognito URL.
    const keycloak = decodeURIComponent(url!.split('returl=')[1]!);
    expect(keycloak).toContain('https://kc/logout?client_id=kc-client');
    expect(keycloak).toContain('post_logout_redirect_uri=');

    // Peel the next layer → the Cognito /logout, with the app as logout_uri (Cognito fires LAST).
    const cognito = decodeURIComponent(keycloak.split('post_logout_redirect_uri=')[1]!);
    expect(cognito).toContain('https://cognito.example.com/logout?client_id=cognito-client');
    expect(cognito).toContain(`logout_uri=${encodeURIComponent('https://app/')}`);
  });

  it('passes the return URL through byte-for-byte, trailing slash included', () => {
    // Cognito matches allowed sign-out URLs by EXACT string and answers an unregistered value with
    // "Required parameters missing" — the error that broke logout in TEST when the caller sent a
    // bare window.location.origin while FAM had the trailing-slash form registered. Anything that
    // normalises, trims or re-encodes this value reintroduces that bug.
    setEnv(ALL_ENV);

    /** Peel the two outer layers and read the logout_uri Cognito will actually receive. */
    const logoutUriIn = (chain: string): string => {
      const keycloak = decodeURIComponent(chain.split('returl=')[1]!);
      const cognito = decodeURIComponent(keycloak.split('post_logout_redirect_uri=')[1]!);
      return decodeURIComponent(cognito.split('logout_uri=')[1]!);
    };

    for (const appUrl of [
      'https://app.example/',
      'https://app.example',
      'https://app.example/base/',
      'http://localhost:3000/',
    ]) {
      expect(logoutUriIn(buildFederatedLogoutUrl(appUrl)!)).toBe(appUrl);
    }
  });
});

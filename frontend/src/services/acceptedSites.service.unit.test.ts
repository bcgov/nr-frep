import { describe, expect, it, vi } from 'vitest';

import { buildLegacyAcceptedSitesUrl } from './acceptedSites.service';

vi.mock('@/env', () => ({
  env: {
    VITE_LEGACY_APP_URL: '/ext/frep',
  },
}));

describe('acceptedSites.service', () => {
  it('builds legacy accepted sites URL', () => {
    expect(buildLegacyAcceptedSitesUrl()).toBe(
      '/ext/frep/frep200AcceptedSitesAction.do?isMenuPick=true',
    );
  });
});

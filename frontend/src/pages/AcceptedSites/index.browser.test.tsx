import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import AcceptedSitesPage from './index';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    configuration: {
      getMasterListYears: vi.fn(),
      getOrgUnits: vi.fn(),
      getProtocols: vi.fn(),
    },
    acceptedSites: { getAcceptedSites: vi.fn() },
  },
}));

// Stable display fn — a fresh fn each render would re-fire the config-load effect (keyed on display).
const { displayMock } = vi.hoisted(() => ({ displayMock: vi.fn() }));
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: displayMock }),
}));

// Admin-like access so the district/protocol dropdowns aren't scoped in these (filter-persistence)
// tests. The object is hoisted + stable (the real hook memoizes it), so the config-load effect —
// which depends on canEdit/chrDistricts — doesn't re-fire every render.
const { authMock } = vi.hoisted(() => ({
  authMock: { canEdit: true, canAnyChr: true, chrDistricts: [] as string[] },
}));
vi.mock('@/hooks/useAuthorization', () => ({
  useAuthorization: () => authMock,
}));

// The table reads the signed-in user's identity provider to build the SILVA Opening ID deep link;
// these tests render the page outside an AuthProvider, so stub the hook rather than wrap each case.
vi.mock('@/context/auth/useAuth', () => ({
  useAuth: () => ({ user: { idpProvider: 'IDIR', privileges: {} } }),
}));

const config = API.configuration as unknown as {
  getMasterListYears: ReturnType<typeof vi.fn>;
  getOrgUnits: ReturnType<typeof vi.fn>;
  getProtocols: ReturnType<typeof vi.fn>;
};
const acceptedSites = API.acceptedSites as unknown as {
  getAcceptedSites: ReturnType<typeof vi.fn>;
};

// 26/27 is the current master list; 25/26 is the prior year the tester was working in.
const YEARS = [
  { effectiveYear: '2026', label: '26/27', current: true },
  { effectiveYear: '2025', label: '25/26', current: false },
];
const ORG_UNITS = [
  { orgUnitNo: '5', orgUnitCode: 'DAA', orgUnitName: 'First District' },
  { orgUnitNo: '7', orgUnitCode: 'DBB', orgUnitName: 'Second District' },
];
const PROTOCOLS = [
  { code: 'CHR', name: 'Cultural Heritage' },
  { code: 'SLR', name: 'Biodiversity' },
];

const LocationProbe = () => {
  const loc = useLocation();
  return <div data-testid="loc-search">{loc.search}</div>;
};

const renderPage = (initialUrl: string) =>
  render(
    <MemoryRouter initialEntries={[initialUrl]}>
      <Routes>
        <Route path="/accepted-sites" element={<AcceptedSitesPage />} />
      </Routes>
      <LocationProbe />
    </MemoryRouter>,
  );

describe('AcceptedSitesPage — filters persist via the URL', () => {
  afterEach(() => vi.clearAllMocks());

  const primeConfig = () => {
    config.getMasterListYears.mockResolvedValue(YEARS);
    config.getOrgUnits.mockResolvedValue(ORG_UNITS);
    config.getProtocols.mockResolvedValue(PROTOCOLS);
    acceptedSites.getAcceptedSites.mockResolvedValue([]);
  };

  it('seeds the filters from the URL instead of defaulting to the current year', async () => {
    primeConfig();
    // Simulates returning via Back to the URL the user had while working in 25/26.
    renderPage('/accepted-sites?year=2025&orgUnit=7&protocol=CHR');

    const yearSelect = (await screen.findByLabelText('Master list year')) as HTMLSelectElement;
    await waitFor(() => expect(yearSelect.value).toBe('2025'));
    expect((screen.getByLabelText('Org unit') as HTMLSelectElement).value).toBe('7');
    expect((screen.getByLabelText('Protocol') as HTMLSelectElement).value).toBe('CHR');
  });

  it('defaults to the current year on a fresh visit and writes it to the URL', async () => {
    primeConfig();
    renderPage('/accepted-sites');

    const yearSelect = (await screen.findByLabelText('Master list year')) as HTMLSelectElement;
    await waitFor(() => expect(yearSelect.value).toBe('2026'));
    // The default filters are mirrored into the URL so a later checklist link carries them.
    await waitFor(() => {
      const search = screen.getByTestId('loc-search').textContent ?? '';
      expect(search).toContain('year=2026');
      expect(search).toContain('orgUnit=5');
    });
  });

  it('updates the URL when the user changes the year (so Back can restore it)', async () => {
    primeConfig();
    renderPage('/accepted-sites');

    const yearSelect = (await screen.findByLabelText('Master list year')) as HTMLSelectElement;
    await waitFor(() => expect(yearSelect.value).toBe('2026'));

    await userEvent.selectOptions(yearSelect, '2025');

    await waitFor(() =>
      expect(screen.getByTestId('loc-search').textContent ?? '').toContain('year=2025'),
    );
  });
});

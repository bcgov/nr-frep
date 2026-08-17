import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import AddTargetSitePage from './index';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    configuration: {
      getBlockStatusCodes: vi.fn(),
      getOpenCategoryCodes: vi.fn(),
      getOpeningStatusCodes: vi.fn(),
    },
    acceptedSites: { searchOpenings: vi.fn(), validateTargetedSite: vi.fn() },
    search: { searchClients: vi.fn() },
  },
}));

const { displayMock } = vi.hoisted(() => ({ displayMock: vi.fn() }));
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: displayMock }),
}));

// The Opening ID cell builds a SILVA deep link from the signed-in provider; these tests render
// outside an AuthProvider, so stub the hook rather than wrap each case.
vi.mock('@/context/auth/useAuth', () => ({
  useAuth: () => ({ user: { idpProvider: 'IDIR', privileges: {} } }),
}));

const config = API.configuration as unknown as {
  getBlockStatusCodes: ReturnType<typeof vi.fn>;
  getOpenCategoryCodes: ReturnType<typeof vi.fn>;
  getOpeningStatusCodes: ReturnType<typeof vi.fn>;
};
const sites = API.acceptedSites as unknown as {
  searchOpenings: ReturnType<typeof vi.fn>;
  validateTargetedSite: ReturnType<typeof vi.fn>;
};

const opening = {
  openingId: '115874',
  openingNumber: '93B 048 0.0 207',
  forestFileId: 'A20015',
  cuttingPermitId: '14',
  timberMark: 'AB1234',
  cutBlockId: '02-13-5',
  grossArea: '12.5',
  openCategoryCode: 'FTML',
  openingStatusCode: 'APP',
  amendmentInd: 'N',
  licenseeOpeningId: '',
  adminDistrictNo: '',
};

/** Shows the query string, so a test can assert what the page wrote into the URL. */
const LocationProbe = () => <span data-testid="search">{useLocation().search}</span>;

const renderAt = (search: string) =>
  render(
    <MemoryRouter initialEntries={[`/add-target-site${search}`]}>
      <Routes>
        <Route
          path="/add-target-site"
          element={
            <>
              <AddTargetSitePage />
              <LocationProbe />
            </>
          }
        />
      </Routes>
    </MemoryRouter>,
  );

beforeEach(() => {
  vi.clearAllMocks();
  config.getBlockStatusCodes.mockResolvedValue([]);
  config.getOpenCategoryCodes.mockResolvedValue([]);
  config.getOpeningStatusCodes.mockResolvedValue([]);
  sites.searchOpenings.mockResolvedValue({
    content: [opening],
    totalElements: 1,
    pageNumber: 0,
    pageSize: 100,
  });
});

const CONTEXT = '?orgUnit=DCC&orgUnitName=Cariboo&year=2024';

describe('AddTargetSite — the search survives leaving the page', () => {
  it('does not search on arrival when the URL carries only the targeting context', async () => {
    renderAt(CONTEXT);

    await waitFor(() => expect(config.getBlockStatusCodes).toHaveBeenCalled());
    expect(sites.searchOpenings).not.toHaveBeenCalled();
  });

  it('writes the search into the URL, keeping the targeting context', async () => {
    renderAt(CONTEXT);

    await userEvent.type(screen.getByLabelText('Licence'), 'A20015');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => expect(sites.searchOpenings).toHaveBeenCalledTimes(1));
    const url = await screen.findByTestId('search');
    // The filter is what makes Back restorable; the context must survive alongside it.
    expect(url.textContent).toContain('forestFileId=A20015');
    expect(url.textContent).toContain('orgUnit=DCC');
    expect(url.textContent).toContain('year=2024');
  });

  it('re-runs a search carried in the URL — the Back case', async () => {
    // What the browser restores after Add → Back: the URL the page wrote before navigating away.
    renderAt(`${CONTEXT}&forestFileId=A20015&page=0&size=100`);

    await waitFor(() => expect(sites.searchOpenings).toHaveBeenCalledTimes(1));
    // The form is repopulated, not just the results.
    expect((screen.getByLabelText('Licence') as HTMLInputElement).value).toBe('A20015');
    expect(await screen.findByText('93B 048 0.0 207')).toBeTruthy();
  });

  it('restores the page the user was on, not page one', async () => {
    renderAt(`${CONTEXT}&forestFileId=A20015&page=2&size=25`);

    await waitFor(() => expect(sites.searchOpenings).toHaveBeenCalledTimes(1));
    const query = sites.searchOpenings.mock.calls[0][0];
    expect(query.pageNumber).toBe(2);
    expect(query.pageSize).toBe(25);
  });

  it('restores it exactly once, even though the search rewrites the URL', async () => {
    // runSearch calls setSearchParams; if the restore effect watched the params it would loop.
    renderAt(`${CONTEXT}&forestFileId=A20015`);

    await waitFor(() => expect(sites.searchOpenings).toHaveBeenCalledTimes(1));
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(sites.searchOpenings).toHaveBeenCalledTimes(1);
  });

  it('Clear empties the URL back to the targeting context', async () => {
    renderAt(`${CONTEXT}&forestFileId=A20015`);
    await waitFor(() => expect(sites.searchOpenings).toHaveBeenCalledTimes(1));

    await userEvent.click(screen.getByRole('button', { name: 'Clear' }));

    const url = await screen.findByTestId('search');
    expect(url.textContent).not.toContain('forestFileId');
    expect(url.textContent).toContain('orgUnit=DCC');
  });
});

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import ChecklistSearchPage from './index';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    configuration: {
      getMasterListYears: vi.fn(),
      getOrgUnits: vi.fn(),
      getProtocols: vi.fn(),
    },
    search: { searchChecklistsPaged: vi.fn(), searchClients: vi.fn() },
  },
  // services/reports.ts (the CSV export) imports this from the same module, so the mock has to
  // provide it or the whole import chain fails to resolve.
  BackendApiConfig: { BASE: '', TOKEN: undefined },
}));

// The CSV export is not under test here and would otherwise pull in the real fetch stack.
vi.mock('@/services/reports', () => ({
  requestChecklistSearchCsv: vi.fn(),
  triggerBrowserDownload: vi.fn(),
}));

const { displayMock } = vi.hoisted(() => ({ displayMock: vi.fn() }));
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: displayMock }),
}));

vi.mock('@/context/auth/useAuth', () => ({
  useAuth: () => ({ user: { idpProvider: 'IDIR', privileges: {} } }),
}));

const config = API.configuration as unknown as {
  getMasterListYears: ReturnType<typeof vi.fn>;
  getOrgUnits: ReturnType<typeof vi.fn>;
  getProtocols: ReturnType<typeof vi.fn>;
};
const search = API.search as unknown as { searchChecklistsPaged: ReturnType<typeof vi.fn> };

const row = {
  checklistId: '1015',
  protocolCode: 'SLR',
  effectiveYear: '2024',
  orgUnitCode: 'DCC',
  licenceId: 'A20015',
  cuttingPermitId: '14',
  cutBlockId: '02-13-5',
  openingId: '115874',
  clientNumber: '00066838',
  evaluationDate: '2026-06-10',
  evaluatorName: 'A Sodhi',
  statusCode: 'ACT',
};

/** Shows the query string, so a test can assert what the page wrote into the URL. */
const LocationProbe = () => <span data-testid="search">{useLocation().search}</span>;

const renderAt = (search_ = '') =>
  render(
    <MemoryRouter initialEntries={[`/checklist-search${search_}`]}>
      <Routes>
        <Route
          path="/checklist-search"
          element={
            <>
              <ChecklistSearchPage />
              <LocationProbe />
            </>
          }
        />
      </Routes>
    </MemoryRouter>,
  );

beforeEach(() => {
  vi.clearAllMocks();
  config.getMasterListYears.mockResolvedValue([{ effectiveYear: '2024' }]);
  config.getOrgUnits.mockResolvedValue([]);
  config.getProtocols.mockResolvedValue([{ code: 'SLR', description: 'Stand Level Retention' }]);
  search.searchChecklistsPaged.mockResolvedValue({
    content: [row],
    totalElements: 1,
    totalPages: 1,
    pageNumber: 0,
    pageSize: 20,
  });
});

describe('ChecklistSearch — the search survives leaving the page', () => {
  it('searches on arrival with no filters when the URL is bare', async () => {
    // This page has always run an opening search on arrival (scoped to the defaulted latest year);
    // the restore rides on that same effect rather than adding a second one.
    renderAt();

    await waitFor(() => expect(search.searchChecklistsPaged).toHaveBeenCalledTimes(1));
    const query = search.searchChecklistsPaged.mock.calls[0][0];
    expect(query.effectiveYear).toBe('2024');
    expect(query.openingId).toBeUndefined();
  });

  it('writes the search into the URL', async () => {
    renderAt();
    // Wait for the arrival search to SETTLE, not just to start: Search is disabled while loading, so
    // clicking too early is a no-op.
    await waitFor(() => expect(search.searchChecklistsPaged).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Search' })).toBeEnabled());

    await userEvent.type(screen.getByLabelText('Opening ID'), '115874');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    // Poll the URL rather than reading it once. findByTestId resolves the moment the probe exists —
    // which is immediately — so a single read sees whatever the URL held at that instant, still the
    // arrival search's params on a slower runner. That is what failed in CI and passed locally.
    await waitFor(() => {
      expect(screen.getByTestId('search').textContent).toContain('openingId=115874');
    });
    expect(screen.getByTestId('search').textContent).toContain('page=0');
  });

  it('re-runs a search carried in the URL — the Back case', async () => {
    // What the browser restores after opening a checklist and coming back.
    renderAt('?openingId=115874&page=0&size=20');

    await waitFor(() => expect(search.searchChecklistsPaged).toHaveBeenCalledTimes(1));
    // The form is repopulated, not just the results.
    expect((screen.getByLabelText('Opening ID') as HTMLInputElement).value).toBe('115874');
    expect(await screen.findByText('A20015')).toBeTruthy();
  });

  it('restores the page the user was on, not page one', async () => {
    renderAt('?openingId=115874&page=3&size=50');

    await waitFor(() => expect(search.searchChecklistsPaged).toHaveBeenCalledTimes(1));
    const query = search.searchChecklistsPaged.mock.calls[0][0];
    expect(query.pageNumber).toBe(3);
    expect(query.pageSize).toBe(50);
  });

  it('restores it exactly once, even though the search rewrites the URL', async () => {
    // runSearch calls setSearchParams; if the mount effect watched the params it would loop.
    renderAt('?openingId=115874');

    await waitFor(() => expect(search.searchChecklistsPaged).toHaveBeenCalledTimes(1));
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(search.searchChecklistsPaged).toHaveBeenCalledTimes(1);
  });

  it('keeps a restored year instead of overwriting it with the latest', async () => {
    // The config load defaults effectiveYear to the newest master list year, but only when the
    // filter is unset — a restored search must not be silently re-scoped to another year.
    config.getMasterListYears.mockResolvedValue([
      { effectiveYear: '2024' },
      { effectiveYear: '2026' },
    ]);
    renderAt('?effectiveYear=2024&openingId=115874');

    await waitFor(() => expect(search.searchChecklistsPaged).toHaveBeenCalledTimes(1));
    expect(search.searchChecklistsPaged.mock.calls[0][0].effectiveYear).toBe('2024');
  });

  it('refuses to search on a client term that matched no client', async () => {
    // The reported bug: typing "lakepaced" set no clientNumber, so the search ran with the client
    // filter simply absent and returned every checklist in the system.
    renderAt();
    await waitFor(() => expect(search.searchChecklistsPaged).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Search' })).toBeEnabled());

    await userEvent.type(screen.getByRole('combobox', { name: /Client name/ }), 'lakepaced');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('Select a client from the list of suggestions.')).toBeTruthy();
    // Still just the arrival search — no unfiltered second one.
    expect(search.searchChecklistsPaged).toHaveBeenCalledTimes(1);
  });

  it('clears the client error as soon as the user edits the field', async () => {
    renderAt();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Search' })).toBeEnabled());

    const field = screen.getByRole('combobox', { name: /Client name/ });
    await userEvent.type(field, 'lakepaced');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    await screen.findByText('Select a client from the list of suggestions.');

    await userEvent.type(field, 'x');

    await waitFor(() =>
      expect(screen.queryByText('Select a client from the list of suggestions.')).toBeNull(),
    );
  });

  it('Clear empties the URL', async () => {
    renderAt('?openingId=115874');
    await waitFor(() => expect(search.searchChecklistsPaged).toHaveBeenCalledTimes(1));

    await userEvent.click(screen.getByRole('button', { name: 'Clear' }));

    await waitFor(() => {
      expect(screen.getByTestId('search').textContent).not.toContain('openingId');
    });
  });
});

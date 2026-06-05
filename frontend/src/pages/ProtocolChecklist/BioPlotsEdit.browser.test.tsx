import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import BioPlotsEditPage from './BioPlotsEdit';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      listBioPlots: vi.fn(),
      getChecklist: vi.fn(),
      getBioPlot: vi.fn(),
      saveBioPlot: vi.fn(),
      deleteBioPlot: vi.fn(),
    },
  },
}));

vi.mock('@/hooks/useAuthorization', () => ({ useAuthorization: () => ({ canEdit: true }) }));

vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: vi.fn() }),
}));

const api = API.protocolChecklist as unknown as Record<string, ReturnType<typeof vi.fn>>;

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/protocol-checklists/biodiversity/1001/strata/900/plots']}>
      <Routes>
        <Route
          path="/protocol-checklists/biodiversity/:id/strata/:stratumId/plots"
          element={<BioPlotsEditPage />}
        />
      </Routes>
    </MemoryRouter>,
  );

describe('BioPlotsEditPage', () => {
  afterEach(() => vi.clearAllMocks());

  it('lists plots, opens one, and saves edits incl. stand rows', async () => {
    api.listBioPlots.mockResolvedValue([{ plotId: '500', plotNumber: '1' }]);
    api.getChecklist.mockResolvedValue({ statusCode: 'ACT' });
    api.getBioPlot.mockResolvedValue({
      plotId: '500',
      stratumId: '900',
      plotNumber: '1',
      revisionCount: '2',
      treeIndicator: 'Y',
      standTable: [{ standId: '7', speciesCode: 'FD', treeNumber: '1' }],
      cwdTable: [],
    });
    api.saveBioPlot.mockResolvedValue({ plotId: '500', stratumId: '900', revisionCount: '3' });

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: '1' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Save plot' }));

    expect(api.saveBioPlot).toHaveBeenCalledTimes(1);
    expect(api.saveBioPlot.mock.calls[0][0]).toBe('900');
  });

  it('is read-only when the checklist is submitted', async () => {
    api.listBioPlots.mockResolvedValue([{ plotId: '500', plotNumber: '1' }]);
    api.getChecklist.mockResolvedValue({ statusCode: 'SUB' });

    renderPage();

    expect(await screen.findByText('Submitted — read only')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Add plot' })).toBeNull();
  });
});

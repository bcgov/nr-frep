import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import BioStrataEditPage from './BioStrataEdit';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      listBioStrata: vi.fn(),
      getChecklist: vi.fn(),
      getBioStratum: vi.fn(),
      saveBioStratum: vi.fn(),
      deleteBioStratum: vi.fn(),
      nextStratumNumber: vi.fn(),
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
    <MemoryRouter initialEntries={['/protocol-checklists/biodiversity/1001/strata']}>
      <Routes>
        <Route
          path="/protocol-checklists/biodiversity/:id/strata"
          element={<BioStrataEditPage />}
        />
      </Routes>
    </MemoryRouter>,
  );

describe('BioStrataEditPage', () => {
  afterEach(() => vi.clearAllMocks());

  it('lists strata, opens one, and saves edits', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: '900', stratumNumber: '1' }]);
    api.getChecklist.mockResolvedValue({ statusCode: 'ACT' });
    api.getBioStratum.mockResolvedValue({
      stratumId: '900',
      checklistId: '1001',
      stratumNumber: '1',
      revisionCount: '2',
      windthrowTreatments: [],
    });
    api.saveBioStratum.mockResolvedValue({ stratumId: '900', revisionCount: '3' });

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: '1' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Save stratum' }));

    expect(api.saveBioStratum).toHaveBeenCalledTimes(1);
    expect(api.saveBioStratum.mock.calls[0][0]).toBe('1001');
  });

  it('is read-only when the checklist is submitted', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: '900', stratumNumber: '1' }]);
    api.getChecklist.mockResolvedValue({ statusCode: 'SUB' });

    renderPage();

    expect(await screen.findByText('Submitted — read only')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Add stratum' })).toBeNull();
  });
});

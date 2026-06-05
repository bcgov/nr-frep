import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import BioOpeningEditPage from './BioOpeningEdit';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      getBiodiversityOpening: vi.fn(),
      saveBiodiversityOpening: vi.fn(),
    },
  },
}));

vi.mock('@/hooks/useAuthorization', () => ({ useAuthorization: () => ({ canEdit: true }) }));

vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: vi.fn() }),
}));

const api = API.protocolChecklist as unknown as {
  getBiodiversityOpening: ReturnType<typeof vi.fn>;
  saveBiodiversityOpening: ReturnType<typeof vi.fn>;
};

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/protocol-checklists/biodiversity/9001/edit']}>
      <Routes>
        <Route path="/protocol-checklists/biodiversity/:id/edit" element={<BioOpeningEditPage />} />
      </Routes>
    </MemoryRouter>,
  );

describe('BioOpeningEditPage', () => {
  afterEach(() => vi.clearAllMocks());

  it('loads the opening and saves edits back', async () => {
    api.getBiodiversityOpening.mockResolvedValue({
      checklistId: '9001',
      statusCode: 'ACT',
      locationDescription: 'old',
      revisionCount: '3',
    });
    api.saveBiodiversityOpening.mockResolvedValue({ checklistId: '9001', revisionCount: '4' });

    renderPage();

    expect(await screen.findByText('Biodiversity opening — checklist 9001')).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(api.saveBiodiversityOpening).toHaveBeenCalledTimes(1);
    expect(api.saveBiodiversityOpening.mock.calls[0][0]).toBe('9001');
  });

  it('is read-only for a submitted checklist (no Save)', async () => {
    api.getBiodiversityOpening.mockResolvedValue({
      checklistId: '9001',
      statusCode: 'SUB',
      locationDescription: 'final',
      revisionCount: '5',
    });

    renderPage();

    expect(await screen.findByText('Submitted — read only')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
  });
});

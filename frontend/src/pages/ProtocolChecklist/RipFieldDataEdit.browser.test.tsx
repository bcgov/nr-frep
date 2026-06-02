import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import RipFieldDataEditPage from './RipFieldDataEdit';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      getRipFieldData: vi.fn(),
      getChecklist: vi.fn(),
      saveRipFieldData: vi.fn(),
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
    <MemoryRouter initialEntries={['/protocol-checklists/riparian/2002/field-data']}>
      <Routes>
        <Route
          path="/protocol-checklists/riparian/:id/field-data"
          element={<RipFieldDataEditPage />}
        />
      </Routes>
    </MemoryRouter>,
  );

describe('RipFieldDataEditPage', () => {
  afterEach(() => vi.clearAllMocks());

  it('loads grids and saves the round-tripped data', async () => {
    api.getRipFieldData.mockResolvedValue({
      checklistId: '2002',
      fieldDataStreamReachDry: 'N',
      points: [{ pointIndType: 'PT', mean: '3.5' }],
      continuous: [],
    });
    api.getChecklist.mockResolvedValue({ statusCode: 'ACT' });
    api.saveRipFieldData.mockResolvedValue({ checklistId: '2002' });

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Save' }));

    expect(api.saveRipFieldData).toHaveBeenCalledTimes(1);
    expect(api.saveRipFieldData.mock.calls[0][0]).toBe('2002');
  });

  it('is read-only when submitted', async () => {
    api.getRipFieldData.mockResolvedValue({ checklistId: '2002', points: [], continuous: [] });
    api.getChecklist.mockResolvedValue({ statusCode: 'SUB' });

    renderPage();

    expect(await screen.findByText('Submitted — read only')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
  });
});

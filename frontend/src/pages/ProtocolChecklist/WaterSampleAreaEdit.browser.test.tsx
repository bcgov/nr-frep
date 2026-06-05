import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import WaterSampleAreaEditPage from './WaterSampleAreaEdit';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      getWaterSampleArea: vi.fn(),
      getChecklist: vi.fn(),
      saveWaterSampleArea: vi.fn(),
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
    <MemoryRouter initialEntries={['/protocol-checklists/water/700/sample-area']}>
      <Routes>
        <Route
          path="/protocol-checklists/water/:id/sample-area"
          element={<WaterSampleAreaEditPage />}
        />
      </Routes>
    </MemoryRouter>,
  );

describe('WaterSampleAreaEditPage', () => {
  afterEach(() => vi.clearAllMocks());

  it('loads and saves the round-tripped sample area', async () => {
    api.getWaterSampleArea.mockResolvedValue({
      waterChecklistId: '700',
      siteAccessCode: 'A',
      revisionCount: '2',
      disturbances: [{ disturbanceCode: 'D1' }],
      accessRoads: [],
    });
    api.getChecklist.mockResolvedValue({ statusCode: 'ACT' });
    api.saveWaterSampleArea.mockResolvedValue({ waterChecklistId: '700', revisionCount: '3' });

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Save sample area' }));

    expect(api.saveWaterSampleArea).toHaveBeenCalledTimes(1);
    expect(api.saveWaterSampleArea.mock.calls[0][0]).toBe('700');
  });

  it('is read-only when submitted', async () => {
    api.getWaterSampleArea.mockResolvedValue({
      waterChecklistId: '700',
      disturbances: [],
      accessRoads: [],
    });
    api.getChecklist.mockResolvedValue({ statusCode: 'SUB' });

    renderPage();

    expect(await screen.findByText('Submitted — read only')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Save sample area' })).toBeNull();
  });
});

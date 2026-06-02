import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import RipStreamOpeningEditPage from './RipStreamOpeningEdit';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      getRipStreamOpening: vi.fn(),
      getChecklist: vi.fn(),
      saveRipStreamOpening: vi.fn(),
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
    <MemoryRouter initialEntries={['/protocol-checklists/riparian/2002/stream-opening']}>
      <Routes>
        <Route
          path="/protocol-checklists/riparian/:id/stream-opening"
          element={<RipStreamOpeningEditPage />}
        />
      </Routes>
    </MemoryRouter>,
  );

describe('RipStreamOpeningEditPage', () => {
  afterEach(() => vi.clearAllMocks());

  it('loads, edits a field, and saves the round-tripped opening', async () => {
    api.getRipStreamOpening.mockResolvedValue({
      checklistId: '2002',
      sampleNumber: '1',
      revisionCount: '2',
      streamEdge: [{ measureType: 'LFT', measurement: '2.5' }],
    });
    api.getChecklist.mockResolvedValue({ statusCode: 'ACT' });
    api.saveRipStreamOpening.mockResolvedValue({ checklistId: '2002', revisionCount: '3' });

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Save stream opening' }));

    expect(api.saveRipStreamOpening).toHaveBeenCalledTimes(1);
    expect(api.saveRipStreamOpening.mock.calls[0][0]).toBe('2002');
  });

  it('is read-only when submitted', async () => {
    api.getRipStreamOpening.mockResolvedValue({ checklistId: '2002', streamEdge: [] });
    api.getChecklist.mockResolvedValue({ statusCode: 'SUB' });

    renderPage();

    expect(await screen.findByText('Submitted — read only')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Save stream opening' })).toBeNull();
  });
});

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import RipFinalCommentsEditPage from './RipFinalCommentsEdit';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      getRipFinalComments: vi.fn(),
      getChecklist: vi.fn(),
      saveRipFinalComments: vi.fn(),
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
    <MemoryRouter initialEntries={['/protocol-checklists/riparian/2002/final-comments']}>
      <Routes>
        <Route
          path="/protocol-checklists/riparian/:id/final-comments"
          element={<RipFinalCommentsEditPage />}
        />
      </Routes>
    </MemoryRouter>,
  );

describe('RipFinalCommentsEditPage', () => {
  afterEach(() => vi.clearAllMocks());

  it('loads and saves the comments', async () => {
    api.getRipFinalComments.mockResolvedValue({
      checklistId: '2002',
      conclusionComment: 'ok',
      revisionCount: '2',
    });
    api.getChecklist.mockResolvedValue({ statusCode: 'ACT' });
    api.saveRipFinalComments.mockResolvedValue({ checklistId: '2002', revisionCount: '3' });

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Save final comments' }));

    expect(api.saveRipFinalComments).toHaveBeenCalledTimes(1);
    expect(api.saveRipFinalComments.mock.calls[0][0]).toBe('2002');
  });

  it('is read-only when submitted', async () => {
    api.getRipFinalComments.mockResolvedValue({ checklistId: '2002' });
    api.getChecklist.mockResolvedValue({ statusCode: 'SUB' });

    renderPage();

    expect(await screen.findByText('Submitted — read only')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Save final comments' })).toBeNull();
  });
});

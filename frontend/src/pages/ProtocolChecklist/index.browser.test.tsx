import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ProtocolChecklistPage from './index';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      getChecklist: vi.fn(),
      submit: vi.fn(),
      unsubmit: vi.fn(),
    },
    configuration: {
      getStreamClasses: vi.fn(() => Promise.resolve([])),
      getChecklistAnswers: vi.fn(() => Promise.resolve([])),
    },
  },
}));

vi.mock('@/hooks/useAuthorization', () => ({ useAuthorization: () => ({ canEdit: true }) }));

vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: vi.fn() }),
}));

const api = API.protocolChecklist as unknown as {
  getChecklist: ReturnType<typeof vi.fn>;
  submit: ReturnType<typeof vi.fn>;
};

const activeChecklist = {
  checklistId: '9001',
  protocolType: 'BIO',
  protocolName: 'Biodiversity',
  statusCode: 'ACT',
  statusLabel: 'Active',
  openingNumber: 'A1',
  effectiveYear: '2024',
  evaluatorUserid: 'u',
  evaluatorName: 'Active User (u)',
  evaluationDate: '2024-06-01',
  sections: [],
};

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/protocol-checklists/slr/9001']}>
      <Routes>
        <Route path="/protocol-checklists/slr/:id" element={<ProtocolChecklistPage />} />
      </Routes>
    </MemoryRouter>,
  );

describe('ProtocolChecklistPage submit', () => {
  afterEach(() => vi.clearAllMocks());

  it('submits an active checklist with the backend protocol code', async () => {
    api.getChecklist.mockResolvedValue({ ...activeChecklist });
    api.submit.mockResolvedValue(undefined);

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Submit' }));

    expect(api.submit).toHaveBeenCalledWith('bio', '9001');
  });

  it('renders validation messages when submit returns 400', async () => {
    api.getChecklist.mockResolvedValue({ ...activeChecklist });
    api.submit.mockRejectedValue({ body: { validationErrors: ['frep.submit.common.teamlead'] } });

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Submit' }));

    // frep.submit.common.teamlead is mapped to friendly text (title "Administration tab" + detail).
    expect(await screen.findByText('Team Lead is mandatory for submit.')).toBeTruthy();
  });
});

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

// The page reads the signed-in user's identity provider to build the SILVA Opening ID deep link;
// these tests render it outside an AuthProvider, so stub the hook rather than wrap every case.
vi.mock('@/context/auth/useAuth', () => ({
  useAuth: () => ({ user: { idpProvider: 'IDIR', privileges: {} } }),
}));

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

    // frep.submit.common.teamlead is mapped to friendly text (title "Opening info" + detail).
    expect(await screen.findByText('an evaluator is required.')).toBeTruthy();
  });

  it('renders a historical SLB record read-only with no submit/unsubmit controls', async () => {
    // SLB is the legacy biodiversity code — view-only in the new app (SLR is go-forward).
    api.getChecklist.mockResolvedValue({
      ...activeChecklist,
      protocolType: 'SLB',
      statusCode: 'SUB',
    });

    renderPage();

    expect(
      await screen.findByText(
        'This is a historical Stand Level Retention (SLB) record and is read-only.',
      ),
    ).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Submit' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Unsubmit' })).toBeNull();
  });
});

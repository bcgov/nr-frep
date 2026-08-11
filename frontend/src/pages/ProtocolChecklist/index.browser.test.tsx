import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import ProtocolChecklistPage from './index';

import API from '@/services/APIs';
import { checkInBioChecklist } from '@/services/offline/bioCheckIn';
import { bioOfflineRepo } from '@/services/offline/bioOfflineRepo';
import { takeBioChecklistOffline } from '@/services/offline/bioTakeOffline';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      getChecklist: vi.fn(),
      submit: vi.fn(),
      unsubmit: vi.fn(),
      activateCheckout: vi.fn(),
    },
    configuration: {
      getStreamClasses: vi.fn(() => Promise.resolve([])),
      getChecklistAnswers: vi.fn(() => Promise.resolve([])),
    },
  },
}));

const { authMock } = vi.hoisted(() => ({
  authMock: { canEdit: true, canPerformSysAdminActions: false },
}));
vi.mock('@/hooks/useAuthorization', () => ({ useAuthorization: () => authMock }));

vi.mock('@/context/confirm/useConfirm', () => ({
  useConfirm: () => vi.fn().mockResolvedValue(true),
}));

vi.mock('@/hooks/useOnlineStatus', () => ({ useOnlineStatus: () => true }));

vi.mock('@/services/offline/bioOfflineRepo', () => ({
  bioOfflineRepo: {
    load: vi.fn(),
    rejectedAttachmentOps: vi.fn(),
    discardAttachmentOp: vi.fn(),
  },
}));
vi.mock('@/services/offline/bioTakeOffline', () => ({
  takeBioChecklistOffline: vi.fn(),
  TakeOfflineCancelled: class extends Error {},
}));
vi.mock('@/services/offline/bioCheckIn', () => ({
  checkInBioChecklist: vi.fn(),
  CheckInBlockedError: class extends Error {},
}));

vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: vi.fn() }),
}));

const repo = bioOfflineRepo as unknown as {
  load: ReturnType<typeof vi.fn>;
  rejectedAttachmentOps: ReturnType<typeof vi.fn>;
  discardAttachmentOp: ReturnType<typeof vi.fn>;
};

const api = API.protocolChecklist as unknown as {
  getChecklist: ReturnType<typeof vi.fn>;
  submit: ReturnType<typeof vi.fn>;
  activateCheckout: ReturnType<typeof vi.fn>;
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
    api.getChecklist.mockResolvedValue({ ...activeChecklist, protocolType: 'SLB', statusCode: 'SUB' });

    renderPage();

    expect(
      await screen.findByText('This is a historical Stand Level Retention (SLB) record and is read-only.'),
    ).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Submit' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Unsubmit' })).toBeNull();
  });
});

// ── Checked out to a device (FE-5) ─────────────────────────────────────
//
// The page previously handled only SUB, so an RDO checklist rendered fully editable and every save
// raced the device holding it (or 403'd once BE-1 landed).

describe('ProtocolChecklistPage when checked out', () => {
  afterEach(() => {
    vi.clearAllMocks();
    authMock.canPerformSysAdminActions = false;
  });

  const checkedOut = { ...activeChecklist, statusCode: 'RDO', statusLabel: 'Checked out' };

  it('renders read-only and explains why', async () => {
    api.getChecklist.mockResolvedValue(checkedOut);

    renderPage();

    expect(await screen.findByText('Read only')).toBeTruthy();
    expect(screen.getByText(/checked out to a field device/i)).toBeTruthy();
  });

  it('offers neither Submit nor Unsubmit while a device holds it', async () => {
    // The device has to check in first — submitting underneath it would validate against a graph
    // the server has not received yet.
    api.getChecklist.mockResolvedValue(checkedOut);

    renderPage();

    await screen.findByText('Read only');
    expect(screen.queryByRole('button', { name: 'Submit' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Unsubmit' })).toBeNull();
  });

  it('shows Reactivate only to a sys admin', async () => {
    api.getChecklist.mockResolvedValue(checkedOut);

    renderPage();
    await screen.findByText('Read only');
    expect(screen.queryByRole('button', { name: 'Reactivate' })).toBeNull();
  });

  it('lets a sys admin reactivate a stranded checkout', async () => {
    authMock.canPerformSysAdminActions = true;
    api.getChecklist.mockResolvedValue(checkedOut);
    api.activateCheckout.mockResolvedValue({ statusCode: 'ACT' });

    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Reactivate' }));

    expect(api.activateCheckout).toHaveBeenCalledWith('9001');
  });

  it('still shows Unsubmit for a submitted checklist', async () => {
    // `editable` now excludes submitted, so Unsubmit needed its own gate — a submitted checklist is
    // read-only but must stay reversible.
    api.getChecklist.mockResolvedValue({ ...activeChecklist, statusCode: 'SUB' });

    renderPage();

    expect(await screen.findByRole('button', { name: 'Unsubmit' })).toBeTruthy();
  });
});

// ── Offline actions (UI wiring) ────────────────────────────────────────

describe('ProtocolChecklistPage offline actions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    repo.load.mockResolvedValue(undefined);
    repo.rejectedAttachmentOps.mockResolvedValue([]);
    api.getChecklist.mockResolvedValue(activeChecklist);
  });

  it('offers Take offline for an active checklist', async () => {
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Take offline' }));

    expect(takeBioChecklistOffline).toHaveBeenCalledWith('9001', expect.anything());
  });

  it('does not offer Take offline for a submitted checklist', async () => {
    // Nothing to take, and the server would refuse the checkout.
    api.getChecklist.mockResolvedValue({ ...activeChecklist, statusCode: 'SUB' });

    renderPage();

    await screen.findByText('Read only');
    expect(screen.queryByRole('button', { name: 'Take offline' })).toBeNull();
  });

  it('swaps Take offline for Check in once a copy is held', async () => {
    // While a local copy exists it is the authoritative one — offering both would invite a second
    // download over the top of unsynced field work.
    repo.load.mockResolvedValue({ checklistId: '9001', syncState: 'DIRTY' });

    renderPage();

    expect(await screen.findByRole('button', { name: 'Check in' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Take offline' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Submit' })).toBeNull();
  });

  it('checks the local copy in', async () => {
    repo.load.mockResolvedValue({ checklistId: '9001', syncState: 'DIRTY' });

    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Check in' }));

    expect(checkInBioChecklist).toHaveBeenCalledWith('9001', expect.anything());
  });

  it('lists each refused file with its reason and a discard action', async () => {
    // Named per file, not "3 files failed": the user has to decide about each one, and the bytes
    // may be field evidence that cannot be re-collected.
    repo.load.mockResolvedValue({ checklistId: '9001', syncState: 'CONFLICT' });
    repo.rejectedAttachmentOps.mockResolvedValue([
      { id: 1, fileName: 'virus.pdf', rejectedReason: 'Virus detected' },
    ]);

    renderPage();

    expect(await screen.findByText('virus.pdf')).toBeTruthy();
    expect(screen.getByText(/Virus detected/)).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: 'Discard' }));
    expect(repo.discardAttachmentOp).toHaveBeenCalledWith(1);
  });
});

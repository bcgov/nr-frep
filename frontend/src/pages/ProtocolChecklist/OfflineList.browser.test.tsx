import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import BioOfflineListPage from './OfflineList';

import API from '@/services/APIs';
import { bioOfflineRepo } from '@/services/offline/bioOfflineRepo';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      getCheckoutState: vi.fn(),
      releaseCheckout: vi.fn(),
    },
  },
}));

vi.mock('@/services/offline/bioOfflineRepo', () => ({
  bioOfflineRepo: {
    listOffline: vi.fn(),
    pendingAttachmentOps: vi.fn(),
    rejectedAttachmentOps: vi.fn(),
    remove: vi.fn(),
  },
}));

vi.mock('@/hooks/useOnlineStatus', () => ({ useOnlineStatus: () => true }));

const { displayMock } = vi.hoisted(() => ({ displayMock: vi.fn() }));
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: displayMock }),
}));
vi.mock('@/context/confirm/useConfirm', () => ({
  useConfirm: () => vi.fn().mockResolvedValue(true),
}));

const api = API.protocolChecklist as unknown as {
  getCheckoutState: ReturnType<typeof vi.fn>;
  releaseCheckout: ReturnType<typeof vi.fn>;
};
const repo = bioOfflineRepo as unknown as {
  listOffline: ReturnType<typeof vi.fn>;
  pendingAttachmentOps: ReturnType<typeof vi.fn>;
  rejectedAttachmentOps: ReturnType<typeof vi.fn>;
  remove: ReturnType<typeof vi.fn>;
};

const aRecord = (over: Record<string, unknown> = {}) => ({
  checklistId: '9001',
  snapshot: { checklistId: '9001', schemaVersion: '1', strata: [], attachments: [] },
  syncState: 'DIRTY',
  schemaVersion: '1',
  deviceCheckoutGuid: 'guid-1',
  tombstones: [],
  updatedAt: 1,
  ...over,
});

const renderPage = () => render(<MemoryRouter><BioOfflineListPage /></MemoryRouter>);

describe('BioOfflineListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    repo.pendingAttachmentOps.mockResolvedValue([]);
    repo.rejectedAttachmentOps.mockResolvedValue([]);
    api.getCheckoutState.mockResolvedValue({ statusCode: 'RDO', heldByThisDevice: true });
  });

  it('says so when nothing is stored offline', async () => {
    repo.listOffline.mockResolvedValue([]);

    renderPage();

    expect(await screen.findByText('No checklists are stored offline.')).toBeTruthy();
  });

  it('lists a copy with its sync status', async () => {
    repo.listOffline.mockResolvedValue([aRecord()]);

    renderPage();

    expect(await screen.findByText('Checklist 9001')).toBeTruthy();
    expect(await screen.findByText('Unsynced changes')).toBeTruthy();
  });

  it('surfaces rejected files ahead of anything else', async () => {
    // Rejected files hold bytes that exist nowhere else — the list must not bury them.
    repo.listOffline.mockResolvedValue([aRecord({ syncState: 'CONFLICT' })]);
    repo.rejectedAttachmentOps.mockResolvedValue([{ id: 1, rejectedReason: 'Virus detected' }]);

    renderPage();

    expect(await screen.findByText('1 file rejected')).toBeTruthy();
  });

  it('releases the checkout before removing the local copy', async () => {
    repo.listOffline.mockResolvedValue([aRecord()]);
    api.releaseCheckout.mockResolvedValue({ statusCode: 'ACT' });
    repo.remove.mockResolvedValue(undefined);

    renderPage();
    await screen.findByText('Checklist 9001');
    await userEvent.click(screen.getByRole('button', { name: 'Remove from device' }));

    await waitFor(() => expect(api.releaseCheckout).toHaveBeenCalledWith('9001', 'guid-1'));
    expect(repo.remove).toHaveBeenCalledWith('9001');
  });

  it('keeps the local copy when releasing the checkout fails', async () => {
    // Copied deliberately from CHR: orphaning the server checkout is worse than keeping a local copy
    // the user can retry, so a failed release must abort the removal.
    repo.listOffline.mockResolvedValue([aRecord()]);
    api.releaseCheckout.mockRejectedValue(new Error('network'));

    renderPage();
    await screen.findByText('Checklist 9001');
    await userEvent.click(screen.getByRole('button', { name: 'Remove from device' }));

    await waitFor(() => expect(api.releaseCheckout).toHaveBeenCalled());
    expect(repo.remove).not.toHaveBeenCalled();
    expect(displayMock).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Could not release the checkout' }),
    );
  });

  it('flags a copy the server has already submitted', async () => {
    repo.listOffline.mockResolvedValue([aRecord()]);
    api.getCheckoutState.mockResolvedValue({ statusCode: 'SUB', heldByThisDevice: false });

    renderPage();

    expect(await screen.findByText('Out of date')).toBeTruthy();
  });

  it('flags a copy whose checkout was reclaimed', async () => {
    // The most common stale case, and the one status alone cannot see: an admin activated the
    // checklist, so it is ACT again and this device's copy can never be checked in.
    repo.listOffline.mockResolvedValue([aRecord()]);
    api.getCheckoutState.mockResolvedValue({ statusCode: 'ACT', heldByThisDevice: false });

    renderPage();

    expect(await screen.findByText('Out of date')).toBeTruthy();
  });

  it('sends this device token so the server can compare without returning its own', async () => {
    repo.listOffline.mockResolvedValue([aRecord()]);

    renderPage();

    await waitFor(() =>
      expect(api.getCheckoutState).toHaveBeenCalledWith('9001', 'guid-1'));
  });
});

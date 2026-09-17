import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import OfflineListPage from './index';

import { useOnlineStatus } from '@/hooks/useOnlineStatus';
import API from '@/services/APIs';
import { bioOfflineRepo } from '@/services/offline/bioOfflineRepo';
import { chrOfflineRepo } from '@/services/offline/chrOfflineRepo';

vi.mock('@/services/APIs', () => ({
  default: {
    chrChecklist: { getChecklist: vi.fn(), release: vi.fn() },
    protocolChecklist: { getCheckoutState: vi.fn(), releaseCheckout: vi.fn() },
  },
}));

vi.mock('@/services/offline/chrOfflineRepo', () => ({
  chrOfflineRepo: { listOffline: vi.fn(), remove: vi.fn() },
}));

vi.mock('@/services/offline/bioOfflineRepo', () => ({
  bioOfflineRepo: {
    listOffline: vi.fn(),
    pendingAttachmentOps: vi.fn(),
    rejectedAttachmentOps: vi.fn(),
    remove: vi.fn(),
  },
}));

vi.mock('@/hooks/useOnlineStatus', () => ({ useOnlineStatus: vi.fn(() => true) }));

const { displayMock } = vi.hoisted(() => ({ displayMock: vi.fn() }));
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: displayMock }),
}));
vi.mock('@/context/confirm/useConfirm', () => ({
  useConfirm: () => vi.fn().mockResolvedValue(true),
}));

const chrApi = API.chrChecklist as unknown as {
  getChecklist: ReturnType<typeof vi.fn>;
  release: ReturnType<typeof vi.fn>;
};
const bioApi = API.protocolChecklist as unknown as {
  getCheckoutState: ReturnType<typeof vi.fn>;
  releaseCheckout: ReturnType<typeof vi.fn>;
};
const chrRepo = chrOfflineRepo as unknown as {
  listOffline: ReturnType<typeof vi.fn>;
  remove: ReturnType<typeof vi.fn>;
};
const bioRepo = bioOfflineRepo as unknown as {
  listOffline: ReturnType<typeof vi.fn>;
  pendingAttachmentOps: ReturnType<typeof vi.fn>;
  rejectedAttachmentOps: ReturnType<typeof vi.fn>;
  remove: ReturnType<typeof vi.fn>;
};
const onlineStatus = useOnlineStatus as unknown as ReturnType<typeof vi.fn>;

const aChrRecord = (over: Record<string, unknown> = {}) => ({
  checklistId: '1001',
  checkList: { openingID: 'OP-1' },
  deviceCheckoutGuid: 'guid-A',
  dirty: false,
  updatedAt: 10,
  ...over,
});

const aBioRecord = (over: Record<string, unknown> = {}) => ({
  checklistId: '9001',
  snapshot: { checklistId: '9001', schemaVersion: '1', strata: [], attachments: [] },
  syncState: 'DIRTY',
  schemaVersion: '1',
  deviceCheckoutGuid: 'guid-1',
  tombstones: [],
  updatedAt: 20,
  ...over,
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <OfflineListPage />
    </MemoryRouter>,
  );

/**
 * Carbon renders `kind="danger--tertiary"` with a visually-hidden "danger" span inside the button,
 * so its accessible name is "danger Remove from device" — match the visible label, not the whole name.
 */
const REMOVE_BUTTON = /Remove from device/;

describe('OfflineListPage', () => {
  beforeEach(() => {
    // Cleared here, not in afterEach: a promise still in flight when the previous test ended can
    // land a call after that test's teardown, and would otherwise be counted against this one.
    vi.clearAllMocks();
    onlineStatus.mockReturnValue(true);
    chrRepo.listOffline.mockResolvedValue([]);
    bioRepo.listOffline.mockResolvedValue([]);
    bioRepo.pendingAttachmentOps.mockResolvedValue([]);
    bioRepo.rejectedAttachmentOps.mockResolvedValue([]);
    chrApi.getChecklist.mockResolvedValue({ status: 'RDO', deviceCheckoutGuid: 'guid-A' });
    bioApi.getCheckoutState.mockResolvedValue({ statusCode: 'RDO', heldByThisDevice: true });
  });

  it('says so when neither store holds anything', async () => {
    renderPage();

    expect(await screen.findByText('No checklists are stored offline.')).toBeTruthy();
  });

  it('lists copies from both stores, each labelled with its protocol', async () => {
    chrRepo.listOffline.mockResolvedValue([aChrRecord()]);
    bioRepo.listOffline.mockResolvedValue([aBioRecord()]);

    renderPage();

    expect(await screen.findByText('Checklist 1001')).toBeTruthy();
    expect(await screen.findByText('Checklist 9001')).toBeTruthy();
    expect(screen.getByText('Cultural Heritage')).toBeTruthy();
    expect(screen.getByText('Stand Level Retention')).toBeTruthy();
  });

  it('links each row to its own protocol page', async () => {
    chrRepo.listOffline.mockResolvedValue([aChrRecord()]);
    bioRepo.listOffline.mockResolvedValue([aBioRecord()]);

    renderPage();

    expect((await screen.findByText('Checklist 1001')).getAttribute('href')).toBe(
      '/protocol-checklists/chr/1001',
    );
    // Not /biodiversity/ — that path has no route, so the link 404'd on the SLR-only list.
    expect(screen.getByText('Checklist 9001').getAttribute('href')).toBe(
      '/protocol-checklists/slr/9001',
    );
  });

  it('shows the numeric opening id an SLR copy captured at take-offline time', async () => {
    // The id (86496), not the opening *number* (93A 023 0.0 111) — the SLR page shows both and the
    // column is the id, matching CHR. A copy written before this was stored reads "—".
    chrRepo.listOffline.mockResolvedValue([]);
    bioRepo.listOffline.mockResolvedValue([
      aBioRecord({ openingId: '86496' }),
      aBioRecord({ checklistId: '9002', openingId: undefined, updatedAt: 5 }),
    ]);

    renderPage();

    expect(await screen.findByText('86496')).toBeTruthy();
    expect(screen.getByText('—')).toBeTruthy();
  });

  it('orders the merged list by most recently touched', async () => {
    chrRepo.listOffline.mockResolvedValue([aChrRecord({ updatedAt: 99 })]);
    bioRepo.listOffline.mockResolvedValue([aBioRecord({ updatedAt: 1 })]);

    renderPage();

    await screen.findByText('Checklist 9001');
    const links = screen.getAllByRole('link').map((el) => el.textContent);
    expect(links).toEqual(['Checklist 1001', 'Checklist 9001']);
  });

  it('keeps one store visible when the other cannot be read', async () => {
    bioRepo.listOffline.mockRejectedValue(new Error('IndexedDB blocked'));
    chrRepo.listOffline.mockResolvedValue([aChrRecord()]);

    renderPage();

    expect(await screen.findByText('Checklist 1001')).toBeTruthy();
  });

  it('flags a superseded CHR copy and shows sync state for a current one', async () => {
    chrRepo.listOffline.mockResolvedValue([
      aChrRecord({ checklistId: '1001', deviceCheckoutGuid: 'guid-A', dirty: false }),
      aChrRecord({
        checklistId: '2002',
        checkList: { openingID: 'OP-2' },
        deviceCheckoutGuid: 'guid-B',
        dirty: true,
      }),
    ]);
    // 1001 was submitted on the server (superseded); 2002 is still checked out to this device.
    chrApi.getChecklist.mockImplementation((id: string) =>
      id === '1001'
        ? Promise.resolve({ status: 'SUB', deviceCheckoutGuid: undefined })
        : Promise.resolve({ status: 'RDO', deviceCheckoutGuid: 'guid-B' }),
    );

    renderPage();

    expect(await screen.findByText('Out of date')).toBeTruthy();
    expect(await screen.findByText('Unsynced changes')).toBeTruthy();
  });

  it('flags an SLR copy whose checkout was reclaimed', async () => {
    // The most common stale case, and the one status alone cannot see: an admin activated the
    // checklist, so it is ACT again and this device's copy can never be checked in.
    bioRepo.listOffline.mockResolvedValue([aBioRecord()]);
    bioApi.getCheckoutState.mockResolvedValue({ statusCode: 'ACT', heldByThisDevice: false });

    renderPage();

    expect(await screen.findByText('Out of date')).toBeTruthy();
  });

  it('sends this device token so the server can compare without returning its own', async () => {
    bioRepo.listOffline.mockResolvedValue([aBioRecord()]);

    renderPage();

    await waitFor(() => expect(bioApi.getCheckoutState).toHaveBeenCalledWith('9001', 'guid-1'));
  });

  it('surfaces rejected files ahead of anything else', async () => {
    // Rejected files hold bytes that exist nowhere else — the list must not bury them.
    bioRepo.listOffline.mockResolvedValue([aBioRecord({ syncState: 'CONFLICT' })]);
    bioRepo.rejectedAttachmentOps.mockResolvedValue([{ id: 1, rejectedReason: 'Virus detected' }]);

    renderPage();

    expect(await screen.findByText('1 file rejected')).toBeTruthy();
  });

  it('shows Unverified for every copy when offline, and probes neither server', async () => {
    onlineStatus.mockReturnValue(false);
    chrRepo.listOffline.mockResolvedValue([aChrRecord()]);
    bioRepo.listOffline.mockResolvedValue([aBioRecord()]);

    renderPage();

    await waitFor(() => expect(screen.getAllByText('Unverified')).toHaveLength(2));
    expect(chrApi.getChecklist).not.toHaveBeenCalled();
    expect(bioApi.getCheckoutState).not.toHaveBeenCalled();
  });

  it('releases the CHR checkout through the CHR endpoint before removing locally', async () => {
    chrRepo.listOffline.mockResolvedValue([aChrRecord()]);
    chrApi.release.mockResolvedValue({});

    renderPage();
    await screen.findByText('Checklist 1001');
    await userEvent.click(screen.getByRole('button', { name: REMOVE_BUTTON }));

    await waitFor(() => expect(chrApi.release).toHaveBeenCalledWith('1001', 'guid-A'));
    await waitFor(() => expect(chrRepo.remove).toHaveBeenCalledWith('1001'));
    expect(bioApi.releaseCheckout).not.toHaveBeenCalled();
    expect(bioRepo.remove).not.toHaveBeenCalled();
  });

  it('releases the SLR checkout through the SLR endpoint before removing locally', async () => {
    bioRepo.listOffline.mockResolvedValue([aBioRecord()]);
    bioApi.releaseCheckout.mockResolvedValue({ statusCode: 'ACT' });

    renderPage();
    await screen.findByText('Checklist 9001');
    await userEvent.click(screen.getByRole('button', { name: REMOVE_BUTTON }));

    await waitFor(() => expect(bioApi.releaseCheckout).toHaveBeenCalledWith('9001', 'guid-1'));
    await waitFor(() => expect(bioRepo.remove).toHaveBeenCalledWith('9001'));
    expect(chrApi.release).not.toHaveBeenCalled();
    expect(chrRepo.remove).not.toHaveBeenCalled();
  });

  it('keeps the local copy when releasing the checkout fails', async () => {
    // Orphaning the server checkout is worse than keeping a local copy the user can retry, so a
    // failed release must abort the removal.
    bioRepo.listOffline.mockResolvedValue([aBioRecord()]);
    bioApi.releaseCheckout.mockRejectedValue(new Error('network'));

    renderPage();
    await screen.findByText('Checklist 9001');
    await userEvent.click(screen.getByRole('button', { name: REMOVE_BUTTON }));

    await waitFor(() => expect(bioApi.releaseCheckout).toHaveBeenCalled());
    expect(bioRepo.remove).not.toHaveBeenCalled();
    expect(displayMock).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Could not release the checkout' }),
    );
  });

  it('disables removal while offline', async () => {
    onlineStatus.mockReturnValue(false);
    chrRepo.listOffline.mockResolvedValue([aChrRecord()]);

    renderPage();

    const button = await screen.findByRole('button', { name: REMOVE_BUTTON });
    expect(button.hasAttribute('disabled')).toBe(true);
  });
});

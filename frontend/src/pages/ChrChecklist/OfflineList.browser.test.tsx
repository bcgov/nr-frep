import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import OfflineListPage from './OfflineList';

import { useOnlineStatus } from '@/hooks/useOnlineStatus';
import API from '@/services/APIs';
import { chrOfflineRepo } from '@/services/offline/chrOfflineRepo';

vi.mock('@/services/APIs', () => ({
  default: { chrChecklist: { getChecklist: vi.fn(), release: vi.fn() } },
}));

vi.mock('@/services/offline/chrOfflineRepo', () => ({
  chrOfflineRepo: { listOffline: vi.fn(), remove: vi.fn() },
}));

vi.mock('@/hooks/useOnlineStatus', () => ({ useOnlineStatus: vi.fn(() => true) }));

vi.mock('@/context/confirm/useConfirm', () => ({
  useConfirm: () => vi.fn().mockResolvedValue(true),
}));

vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: vi.fn() }),
}));

const api = API.chrChecklist as unknown as {
  getChecklist: ReturnType<typeof vi.fn>;
  release: ReturnType<typeof vi.fn>;
};
const repo = chrOfflineRepo as unknown as {
  listOffline: ReturnType<typeof vi.fn>;
  remove: ReturnType<typeof vi.fn>;
};
const onlineStatus = useOnlineStatus as unknown as ReturnType<typeof vi.fn>;

const renderPage = () =>
  render(
    <MemoryRouter>
      <OfflineListPage />
    </MemoryRouter>,
  );

describe('ChrOfflineListPage', () => {
  afterEach(() => {
    vi.clearAllMocks();
    onlineStatus.mockReturnValue(true);
  });

  it('flags a superseded offline copy and shows sync state for a current one', async () => {
    repo.listOffline.mockResolvedValue([
      {
        checklistId: '1001',
        checkList: { openingID: 'OP-1' },
        deviceCheckoutGuid: 'guid-A',
        dirty: false,
      },
      {
        checklistId: '2002',
        checkList: { openingID: 'OP-2' },
        deviceCheckoutGuid: 'guid-B',
        dirty: true,
      },
    ]);
    // 1001 was submitted on the server (superseded); 2002 is still checked out to this device.
    api.getChecklist.mockImplementation((id: string) =>
      id === '1001'
        ? Promise.resolve({ status: 'SUB', deviceCheckoutGuid: undefined })
        : Promise.resolve({ status: 'RDO', deviceCheckoutGuid: 'guid-B' }),
    );

    renderPage();

    expect(await screen.findByText('Out of date')).toBeTruthy();
    expect(await screen.findByText('Unsynced changes')).toBeTruthy();
  });

  it('releases the server checkout before removing a copy locally', async () => {
    onlineStatus.mockReturnValue(true);
    repo.listOffline.mockResolvedValue([
      {
        checklistId: '1001',
        checkList: { openingID: 'OP-1' },
        deviceCheckoutGuid: 'guid-A',
        dirty: false,
      },
    ]);
    api.getChecklist.mockResolvedValue({ status: 'RDO', deviceCheckoutGuid: 'guid-A' });
    api.release.mockResolvedValue({});

    renderPage();

    await userEvent.click(await screen.findByText('Remove from device'));

    await waitFor(() => expect(api.release).toHaveBeenCalledWith('1001', 'guid-A'));
    await waitFor(() => expect(repo.remove).toHaveBeenCalledWith('1001'));
  });

  it('shows Unverified for every copy when offline', async () => {
    onlineStatus.mockReturnValue(false);
    repo.listOffline.mockResolvedValue([
      {
        checklistId: '1001',
        checkList: { openingID: 'OP-1' },
        deviceCheckoutGuid: 'guid-A',
        dirty: false,
      },
    ]);

    renderPage();

    expect(await screen.findByText('Unverified')).toBeTruthy();
    expect(api.getChecklist).not.toHaveBeenCalled();
  });
});

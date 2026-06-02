import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ChrChecklistPage from './index';

import * as useAuthorizationModule from '@/hooks/useAuthorization';
import API from '@/services/APIs';
import { chrOfflineRepo } from '@/services/offline/chrOfflineRepo';

vi.mock('@/services/APIs', () => ({
  default: {
    chrChecklist: {
      getChecklist: vi.fn(),
      save: vi.fn(),
      submit: vi.fn(),
    },
  },
}));

vi.mock('@/services/offline/chrOfflineRepo', () => ({
  chrOfflineRepo: { load: vi.fn(), saveLocal: vi.fn(), upload: vi.fn(), takeOffline: vi.fn() },
}));

vi.mock('@/hooks/useOnlineStatus', () => ({ useOnlineStatus: () => true }));

vi.mock('@/hooks/useAuthorization', () => ({ useAuthorization: vi.fn() }));

vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: vi.fn() }),
}));

const api = API.chrChecklist as unknown as {
  getChecklist: ReturnType<typeof vi.fn>;
  save: ReturnType<typeof vi.fn>;
};
const repo = chrOfflineRepo as unknown as { load: ReturnType<typeof vi.fn> };
const useAuthorization = useAuthorizationModule.useAuthorization as ReturnType<typeof vi.fn>;

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/chr/checklists/1001']}>
      <Routes>
        <Route path="/chr/checklists/:id" element={<ChrChecklistPage />} />
      </Routes>
    </MemoryRouter>,
  );

const sampleChecklist = {
  checklistID: '1001',
  status: 'ACT',
  features: [],
  contacts: [],
  pictures: [],
};

describe('ChrChecklistPage', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('loads a checklist from the API and saves edits back', async () => {
    useAuthorization.mockReturnValue({ canEdit: true, isViewOnly: false });
    repo.load.mockResolvedValue(undefined);
    api.getChecklist.mockResolvedValue({ ...sampleChecklist });
    api.save.mockResolvedValue({ ...sampleChecklist });

    renderPage();

    expect(await screen.findByText('CHR checklist 1001')).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(api.save).toHaveBeenCalledTimes(1);
    expect(api.save.mock.calls[0][0]).toMatchObject({ checklistID: '1001' });
  });

  it('hides write actions for view-only users', async () => {
    useAuthorization.mockReturnValue({ canEdit: false, isViewOnly: true });
    repo.load.mockResolvedValue(undefined);
    api.getChecklist.mockResolvedValue({ ...sampleChecklist });

    renderPage();

    expect(await screen.findByText('View only')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
  });
});

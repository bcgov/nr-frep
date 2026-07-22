import { render, screen, waitFor } from '@testing-library/react';
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
      saveOpening: vi.fn(),
      saveBlockSummary: vi.fn(),
      saveContacts: vi.fn(),
      saveFeatures: vi.fn(),
      savePhotos: vi.fn(),
      submit: vi.fn(),
      activate: vi.fn(),
    },
  },
}));

vi.mock('@/services/offline/chrOfflineRepo', () => ({
  chrOfflineRepo: {
    load: vi.fn(),
    saveLocal: vi.fn(),
    upload: vi.fn(),
    takeOffline: vi.fn(),
    remove: vi.fn(),
  },
}));

vi.mock('@/hooks/useOnlineStatus', () => ({ useOnlineStatus: () => true }));

vi.mock('@/hooks/useAuthorization', () => ({ useAuthorization: vi.fn() }));

// Stable display fn — the real useNotification is context-memoized. A fresh fn per render would make
// the load effect's [id, display] dep re-fire every render (re-entering the loading state).
const { displayMock } = vi.hoisted(() => ({ displayMock: vi.fn() }));
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: displayMock }),
}));

vi.mock('@/context/auth/useAuth', () => ({
  useAuth: () => ({ user: { providerUsername: String.raw`IDIR\TESTER` } }),
}));

vi.mock('@/context/confirm/useConfirm', () => ({
  useConfirm: () => vi.fn().mockResolvedValue(true),
}));

const api = API.chrChecklist as unknown as {
  getChecklist: ReturnType<typeof vi.fn>;
  save: ReturnType<typeof vi.fn>;
  saveOpening: ReturnType<typeof vi.fn>;
  activate: ReturnType<typeof vi.fn>;
  submit: ReturnType<typeof vi.fn>;
};
const repo = chrOfflineRepo as unknown as {
  load: ReturnType<typeof vi.fn>;
  saveLocal: ReturnType<typeof vi.fn>;
  upload: ReturnType<typeof vi.fn>;
  remove: ReturnType<typeof vi.fn>;
};
const useAuthorization = useAuthorizationModule.useAuthorization as ReturnType<typeof vi.fn>;

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/protocol-checklists/chr/1001']}>
      <Routes>
        <Route path="/protocol-checklists/chr/:id" element={<ChrChecklistPage />} />
      </Routes>
    </MemoryRouter>,
  );

const sampleChecklist = {
  checklistID: '1001',
  status: 'ACT',
  // The Opening tab requires these to save; pre-fill them (assessedBy = the mock user).
  assessedBy: String.raw`IDIR\TESTER`,
  evaluationDate: '2026-06-10',
  generalLocation: '16 km on Finnegan FSR',
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
    api.saveOpening.mockResolvedValue({ ...sampleChecklist });

    renderPage();

    expect(await screen.findByText('1001-Cultural Heritage')).toBeTruthy();

    // Opening info is the default tab: Edit reveals the form, Save persists only that section.
    await userEvent.click(screen.getByRole('button', { name: 'Edit' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(api.saveOpening).toHaveBeenCalledTimes(1);
    expect(api.saveOpening.mock.calls[0][0]).toBe('1001');
    expect(api.saveOpening.mock.calls[0][1]).toMatchObject({ checklistID: '1001' });
  });

  it('shows the FAM-resolved evaluator name (not the raw userid) in the header', async () => {
    useAuthorization.mockReturnValue({ canEdit: true, isViewOnly: false });
    repo.load.mockResolvedValue(undefined);
    api.getChecklist.mockResolvedValue({
      ...sampleChecklist,
      assessedByName: 'Test Tester (TESTER)',
    });

    renderPage();

    expect(await screen.findByText('1001-Cultural Heritage')).toBeTruthy();
    // The resolved name shows in both the header "Evaluator" cell and the Opening-tab "Evaluator"
    // read-only field; the raw IDIR\TESTER userid is never shown.
    expect(screen.getAllByText('Test Tester (TESTER)').length).toBeGreaterThanOrEqual(2);
    expect(screen.queryByText(String.raw`IDIR\TESTER`)).toBeNull();
  });

  it('hides write actions for view-only users', async () => {
    useAuthorization.mockReturnValue({ canEdit: false, isViewOnly: true });
    repo.load.mockResolvedValue(undefined);
    api.getChecklist.mockResolvedValue({ ...sampleChecklist });

    renderPage();

    expect(await screen.findByText('View only')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
  });

  it('on an offline copy, Submit checks it in (upload) then submits', async () => {
    useAuthorization.mockReturnValue({ canEdit: true, isViewOnly: false });
    repo.load.mockResolvedValue({
      checklistId: '1001',
      checkList: { ...sampleChecklist, status: 'RDO' },
      dirty: false,
    });
    repo.saveLocal.mockResolvedValue(undefined);
    repo.upload.mockResolvedValue({ ...sampleChecklist, status: 'ACT' });
    repo.remove.mockResolvedValue(undefined);
    api.submit.mockResolvedValue({ ...sampleChecklist, status: 'SUB' });
    // The page probes the server to reconcile the offline copy's staleness (chrStaleness).
    api.getChecklist.mockResolvedValue({ ...sampleChecklist, status: 'RDO' });

    renderPage();

    expect(await screen.findByText('1001-Cultural Heritage')).toBeTruthy();
    expect(screen.getByText('Offline copy')).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));
    // Upload (check in: RDO → ACT) and drop the local draft happen before the submit call.
    // waitFor: the submit chain is async.
    await waitFor(() => expect(repo.upload).toHaveBeenCalledWith('1001'));
    expect(repo.remove).toHaveBeenCalledWith('1001');
    expect(api.submit).toHaveBeenCalledWith(
      '1001',
      expect.objectContaining({ checklistID: '1001' }),
    );
  });

  it('warns when an offline copy has been superseded on the server', async () => {
    useAuthorization.mockReturnValue({ canEdit: true, isViewOnly: false });
    repo.load.mockResolvedValue({
      checklistId: '1001',
      checkList: { ...sampleChecklist, status: 'RDO' },
      deviceCheckoutGuid: 'guid-A',
      dirty: false,
    });
    // Someone reactivated + submitted it on the server since this device checked it out.
    api.getChecklist.mockResolvedValue({
      ...sampleChecklist,
      status: 'SUB',
      deviceCheckoutGuid: undefined,
      updateUserid: String.raw`IDIR\jsmith`,
      updateTimestamp: '2012-10-01 14:30:00',
    });

    renderPage();

    expect(await screen.findByText('Offline copy out of date')).toBeTruthy();
    expect(screen.getByText(/Last updated by jsmith on Oct 1, 2012/)).toBeTruthy();
    // A stale copy can't be uploaded, so Submit and Sync changes are hidden…
    expect(screen.queryByRole('button', { name: 'Submit' })).toBeNull();
    expect(screen.queryByText('Sync changes')).toBeNull();
    // …leaving only the Remove from device escape hatch.
    expect(screen.getByText('Remove from device')).toBeTruthy();
  });

  it('lets an admin reactivate a checked-out (RDO) server checklist', async () => {
    useAuthorization.mockReturnValue({
      canEdit: true,
      isViewOnly: false,
      canPerformSysAdminActions: true,
    });
    repo.load.mockResolvedValue(undefined);
    api.getChecklist.mockResolvedValue({ ...sampleChecklist, status: 'RDO' });
    api.activate.mockResolvedValue({ ...sampleChecklist, status: 'ACT' });

    renderPage();

    expect(await screen.findByText('1001-Cultural Heritage')).toBeTruthy();
    // Checked-out server copy is read-only and shows the recovery banner; tabs are not editable.
    expect(screen.getByText('Read only')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Edit' })).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: 'Reactivate' }));
    expect(api.activate).toHaveBeenCalledWith('1001');
  });
});

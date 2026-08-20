import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

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
      getPhotos: vi.fn(),
      getPhotoContent: vi.fn(),
      addPhoto: vi.fn(),
      deletePhoto: vi.fn(),
      submit: vi.fn(),
      unsubmit: vi.fn(),
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
  getPhotos: ReturnType<typeof vi.fn>;
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

// A feature with every submit rule satisfied: labelled, not a composite (so it owes a description
// code and an information source), one feature type, one age, and a rating.
const completeFeature = {
  featureLabel: '1',
  compositeFeatureInd: 'false',
  featureDescriptionCode: 'CMT',
  featureInfoSourceCode: 'AIA',
  burialSite: 'true',
  pre1846: 'true',
  featureRating: 'HIGH',
};

// A checklist that is ready to submit. Submit runs a client-side pre-flight over the same rules the
// server enforces (see tabStatus.ts) and stops before calling the API when anything is outstanding,
// so a fixture missing a required field would never reach the call the test is about.
const sampleChecklist = {
  checklistID: '1001',
  status: 'ACT',
  // Opening info (assessedBy = the mock user).
  assessedBy: String.raw`IDIR\TESTER`,
  evaluationDate: '2026-06-10',
  generalLocation: '16 km on Finnegan FSR',
  yearOfHarvest: '2024',
  // Block summary.
  rating: 'HIGH',
  features: [completeFeature],
  contacts: [],
  pictures: [],
};

describe('ChrChecklistPage', () => {
  // The Photos tab loads its own page of metadata as soon as an online checklist is available, so
  // every test needs this to resolve — even the ones that never touch photos.
  beforeEach(() => {
    api.getPhotos.mockResolvedValue({ photos: [], totalCount: 0 });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('loads a checklist from the API and saves edits back', async () => {
    useAuthorization.mockReturnValue({ canEdit: true, isViewOnly: false, canChr: () => true });
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

  it('saves an Opening tab that is still missing a required field', async () => {
    useAuthorization.mockReturnValue({ canEdit: true, isViewOnly: false, canChr: () => true });
    repo.load.mockResolvedValue(undefined);
    api.getChecklist.mockResolvedValue({ ...sampleChecklist, generalLocation: '' });
    api.saveOpening.mockResolvedValue({ ...sampleChecklist, generalLocation: '' });

    renderPage();
    expect(await screen.findByText('1001-Cultural Heritage')).toBeTruthy();

    // General location is required for submit, but a part-finished Opening is a legitimate thing to
    // store — the save goes through, and the tab says what is still owed.
    await userEvent.click(screen.getByRole('button', { name: 'Edit' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(api.saveOpening).toHaveBeenCalledTimes(1));
    expect(await screen.findByText('Opening saved — required fields missing')).toBeTruthy();
    expect(
      screen.getByText('1 required field to resolve before this checklist can be submitted:'),
    ).toBeTruthy();
  });

  it('stays quiet about a feature that has been added but not saved', async () => {
    useAuthorization.mockReturnValue({ canEdit: true, isViewOnly: false, canChr: () => true });
    repo.load.mockResolvedValue(undefined);
    api.getChecklist.mockResolvedValue({ ...sampleChecklist, features: [] });

    renderPage();
    expect(await screen.findByText('1001-Cultural Heritage')).toBeTruthy();

    await userEvent.click(screen.getByRole('tab', { name: /Features/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Add feature' }));

    // The editor is open on a blank feature. Listing everything it owes before the user has saved it
    // once reads as a fault rather than as work in progress — the tab keeps quiet until the feature
    // is stored, which is why the pending list is held outside `checkList`.
    expect(screen.queryByText('Required fields missing')).toBeNull();
  });

  it('blocks Submit before calling the API when a tab is incomplete', async () => {
    useAuthorization.mockReturnValue({ canEdit: true, isViewOnly: false, canChr: () => true });
    repo.load.mockResolvedValue(undefined);
    // No features: the checklist cannot be submitted, and the Features tab has never been opened.
    api.getChecklist.mockResolvedValue({ ...sampleChecklist, features: [] });

    renderPage();
    expect(await screen.findByText('1001-Cultural Heritage')).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(await screen.findByText("This checklist isn't ready to submit")).toBeTruthy();
    // The pre-flight answers from what the page already holds, so the server is never asked.
    expect(api.submit).not.toHaveBeenCalled();
    // Pressing Submit also reveals the count a never-opened tab was holding back.
    expect(screen.getByLabelText('Features: 1 required field missing')).toBeTruthy();
  });

  it('shows the FAM-resolved evaluator name (not the raw userid) in the header', async () => {
    useAuthorization.mockReturnValue({ canEdit: true, isViewOnly: false, canChr: () => true });
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
    useAuthorization.mockReturnValue({ canEdit: false, isViewOnly: true, canChr: () => false });
    repo.load.mockResolvedValue(undefined);
    api.getChecklist.mockResolvedValue({ ...sampleChecklist });

    renderPage();

    expect(await screen.findByText('View only')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
  });

  it('on an offline copy, Submit checks it in (upload) then submits', async () => {
    useAuthorization.mockReturnValue({ canEdit: true, isViewOnly: false, canChr: () => true });
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

  // ── Offline photos must survive every local write ──────────────────────
  //
  // A photo captured offline holds its bytes in the local record and nowhere else until check-in
  // flushes them. Any local save that drops `pictures` destroys them permanently — which is what the
  // shared server-payload shape (`pictures: []`) did from three separate call sites.

  const offlineCopyWithAPhoto = () => ({
    checklistId: '1001',
    checkList: {
      ...sampleChecklist,
      status: 'RDO',
      pictures: [
        {
          description: 'Captured offline',
          mimeTypeCode: 'image/png',
          code: 'data:image/png;base64,iVBOR',
        },
      ],
    },
    dirty: true,
    deviceCheckoutGuid: 'guid',
  });

  const savedLocally = () =>
    (repo.saveLocal.mock.calls.at(-1)?.[0] ?? {}) as { pictures?: unknown[] };

  it('keeps offline photos when a section is saved locally', async () => {
    useAuthorization.mockReturnValue({ canEdit: true, isViewOnly: false, canChr: () => true });
    repo.load.mockResolvedValue(offlineCopyWithAPhoto());
    repo.saveLocal.mockResolvedValue(undefined);
    api.getChecklist.mockResolvedValue({ ...sampleChecklist, status: 'RDO' });

    renderPage();
    expect(await screen.findByText('1001-Cultural Heritage')).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: 'Edit' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(repo.saveLocal).toHaveBeenCalled());
    expect(savedLocally().pictures).toHaveLength(1);
  });

  it('keeps offline photos when Sync changes writes the local copy before uploading', async () => {
    // The worst of the three: this ran immediately before upload() flushed the photos, so the bytes
    // were already gone by the time the flush looked for them — and the sync reported success.
    useAuthorization.mockReturnValue({ canEdit: true, isViewOnly: false, canChr: () => true });
    repo.load.mockResolvedValue(offlineCopyWithAPhoto());
    repo.saveLocal.mockResolvedValue(undefined);
    repo.upload.mockResolvedValue({ ...sampleChecklist, status: 'ACT' });
    // Same guid on both sides, so the copy classifies as CURRENT — a stale one hides Sync/Submit.
    api.getChecklist.mockResolvedValue({
      ...sampleChecklist,
      status: 'RDO',
      deviceCheckoutGuid: 'guid',
    });

    renderPage();
    expect(await screen.findByText('1001-Cultural Heritage')).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: 'Sync changes' }));

    await waitFor(() => expect(repo.upload).toHaveBeenCalledWith('1001'));
    expect(savedLocally().pictures).toHaveLength(1);
  });

  it('drops the local copy after a successful sync', async () => {
    // A check-in clears the server's checkout guid, so a retained copy can never upload again.
    useAuthorization.mockReturnValue({ canEdit: true, isViewOnly: false, canChr: () => true });
    repo.load.mockResolvedValue(offlineCopyWithAPhoto());
    repo.saveLocal.mockResolvedValue(undefined);
    repo.upload.mockResolvedValue({ ...sampleChecklist, status: 'ACT' });
    repo.remove.mockResolvedValue(undefined);
    api.getChecklist.mockResolvedValue({
      ...sampleChecklist,
      status: 'RDO',
      deviceCheckoutGuid: 'guid',
    });

    renderPage();
    expect(await screen.findByText('1001-Cultural Heritage')).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: 'Sync changes' }));

    await waitFor(() => expect(repo.remove).toHaveBeenCalledWith('1001'));
    // No longer an offline copy: the page is now showing the checked-in server checklist.
    await waitFor(() => expect(screen.queryByText('Offline copy')).toBeNull());
  });

  it('keeps the local copy when a sync fails', async () => {
    // The whole point of removing only on success: a failed check-in must stay retryable, with the
    // photos still on the device.
    useAuthorization.mockReturnValue({ canEdit: true, isViewOnly: false, canChr: () => true });
    repo.load.mockResolvedValue(offlineCopyWithAPhoto());
    repo.saveLocal.mockResolvedValue(undefined);
    repo.upload.mockRejectedValue(new Error('conflict'));
    api.getChecklist.mockResolvedValue({
      ...sampleChecklist,
      status: 'RDO',
      deviceCheckoutGuid: 'guid',
    });

    renderPage();
    expect(await screen.findByText('1001-Cultural Heritage')).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: 'Sync changes' }));

    await waitFor(() => expect(repo.upload).toHaveBeenCalled());
    expect(repo.remove).not.toHaveBeenCalled();
    expect(screen.getByText('Offline copy')).toBeTruthy();
  });

  it('keeps offline photos when Submit checks the copy in first', async () => {
    useAuthorization.mockReturnValue({ canEdit: true, isViewOnly: false, canChr: () => true });
    repo.load.mockResolvedValue(offlineCopyWithAPhoto());
    repo.saveLocal.mockResolvedValue(undefined);
    repo.upload.mockResolvedValue({ ...sampleChecklist, status: 'ACT' });
    repo.remove.mockResolvedValue(undefined);
    api.submit.mockResolvedValue({ ...sampleChecklist, status: 'SUB' });
    api.getChecklist.mockResolvedValue({
      ...sampleChecklist,
      status: 'RDO',
      deviceCheckoutGuid: 'guid',
    });

    renderPage();
    expect(await screen.findByText('1001-Cultural Heritage')).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));

    await waitFor(() => expect(repo.upload).toHaveBeenCalledWith('1001'));
    expect(savedLocally().pictures).toHaveLength(1);
    // The submit payload is a server save, so it still strips them.
    expect(api.submit.mock.calls[0][1]).toMatchObject({ pictures: [] });
  });

  it('warns when an offline copy has been superseded on the server', async () => {
    useAuthorization.mockReturnValue({ canEdit: true, isViewOnly: false, canChr: () => true });
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
      canChr: () => true,
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

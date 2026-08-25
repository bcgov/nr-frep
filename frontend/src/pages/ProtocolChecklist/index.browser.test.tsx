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
      // Read by the tab-status pre-flight. Left un-stubbed (rejecting) in the older cases, which is
      // itself the "pre-flight failed → submit anyway" path.
      getBiodiversityOpening: vi.fn(),
      listBioStrata: vi.fn(),
      getBioStratum: vi.fn(),
      listBioPlots: vi.fn(),
      getBioPlot: vi.fn(),
    },
    // The page renders every tab panel, so each view's code-list reads have to resolve.
    configuration: {
      getStreamClasses: vi.fn(() => Promise.resolve([])),
      getChecklistAnswers: vi.fn(() => Promise.resolve([])),
      getSiteEvaluationCodes: vi.fn(() => Promise.resolve([])),
      getStrataTypes: vi.fn(() => Promise.resolve([])),
      searchBec: vi.fn(() => Promise.resolve([])),
      getSpecies: vi.fn(() => Promise.resolve([])),
      getWildlifeTreeDecay: vi.fn(() => Promise.resolve([])),
      getCwdDecay: vi.fn(() => Promise.resolve([])),
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

// The page reads the signed-in user's identity provider to build the SILVA Opening ID deep link;
// these tests render it outside an AuthProvider, so stub the hook rather than wrap every case.
vi.mock('@/context/auth/useAuth', () => ({
  useAuth: () => ({ user: { idpProvider: 'IDIR', privileges: {} } }),
}));

// Stable display: the tab views' load effects depend on it (via reportError → loadData), so a fresh
// fn each render re-fires those effects forever. Same hoist as the per-view test files.
const { display } = vi.hoisted(() => ({ display: vi.fn() }));
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display }),
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
  getBiodiversityOpening: ReturnType<typeof vi.fn>;
  listBioStrata: ReturnType<typeof vi.fn>;
  listBioPlots: ReturnType<typeof vi.fn>;
  getBioStratum: ReturnType<typeof vi.fn>;
  getBioPlot: ReturnType<typeof vi.fn>;
};

/** A checklist whose tabs are all in order, so the pre-flight passes and the proc is called. */
const readyChecklist = () => {
  api.getBiodiversityOpening.mockResolvedValue({
    checklistId: '9001',
    locationDescription: 'Block 12',
    evaluationDate: '2024-06-01',
    teamLeadNameId: 'IDIR\\ME',
    invasivePlantIndicator: 'N',
    innovativePracticeInd: 'N',
    frepSiteEvaluationCode: 'M',
    grossArea: '50',
    netArea: '40',
  });
  api.listBioStrata.mockResolvedValue([{ stratumId: 'S1' }]);
  api.getBioStratum.mockResolvedValue({
    stratumId: 'S1',
    stratumNumber: '1',
    strataTypeCode: 'PR',
    consistentMapInd: 'Y',
    size: '10',
    plotCount: '1',
    harvestAreaCode: 'HDR',
  });
  api.listBioPlots.mockResolvedValue([{ plotId: '1' }]);
  api.getBioPlot.mockResolvedValue({
    plotId: '1',
    plotNumber: '1',
    utmSignal: 'Y',
    utmZone: '10',
    utmEasting: '123456',
    utmNorthing: '1234567',
    firstLegTransect: '090',
    secondLegTransect: '180',
    basalAreaFactor: '5',
    treeIndicator: 'N',
    cwdTransectIndicator: 'N',
    standTable: [],
    cwdTable: [],
  });
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
  sections: [
    { id: 'opening', title: 'Opening info', fields: [] },
    { id: 'stratum', title: 'Stratum summary', fields: [] },
    { id: 'plots', title: 'Plots', fields: [] },
  ],
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

  beforeEach(() => {
    // Safe defaults for the reads behind the tab panels and the submit pre-flight; individual tests
    // override them to describe the checklist they need.
    api.getBiodiversityOpening.mockResolvedValue({ checklistId: '9001' });
    api.listBioStrata.mockResolvedValue([]);
    api.listBioPlots.mockResolvedValue([]);
    api.getBioStratum.mockResolvedValue({});
    api.getBioPlot.mockResolvedValue({});
  });

  it('submits an active checklist with the backend protocol code', async () => {
    api.getChecklist.mockResolvedValue({ ...activeChecklist });
    readyChecklist();
    api.submit.mockResolvedValue(undefined);

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Submit' }));

    // Submit now pre-flights every tab first, so the call happens a couple of awaits later.
    await vi.waitFor(() => expect(api.submit).toHaveBeenCalledWith('bio', '9001'));
  });

  it('renders validation messages when submit returns 400', async () => {
    // The pre-flight passes, so the proc gets its say — and its answer still reaches the user.
    api.getChecklist.mockResolvedValue({ ...activeChecklist });
    readyChecklist();
    api.submit.mockRejectedValue({ body: { validationErrors: ['frep.submit.common.teamlead'] } });

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Submit' }));

    // frep.submit.common.teamlead is mapped to friendly text (title "Opening info" + detail).
    expect(await screen.findByText('an evaluator is required.')).toBeTruthy();
  });

  it('blocks submit and names the outstanding work before calling the proc', async () => {
    // The case this exists for: the evaluator never opened the Opening tab, so its dot has been
    // deliberately quiet. Pressing Submit has to be the moment that stops being true.
    api.getChecklist.mockResolvedValue({ ...activeChecklist });
    api.getBiodiversityOpening.mockResolvedValue({ checklistId: '9001', grossArea: '50' });
    api.listBioStrata.mockResolvedValue([]);

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Submit' }));

    // One page-level banner naming the tabs at fault — the items themselves stay on those tabs.
    await screen.findByText("This checklist isn't ready to submit");
    expect(api.submit).not.toHaveBeenCalled();
    expect(
      screen.getByText(
        'Opening info, Stratum summary and Plots have required fields outstanding. ' +
          'Fix the items listed on each tab, then submit again.',
      ),
    ).toBeTruthy();

    // Each tab now lists its own outstanding work, including the ones that had never been opened.
    const listed = Array.from(
      document.querySelectorAll('.protocol-checklist__incomplete-list li'),
    ).map((li) => li.textContent);
    expect(listed).toContain('Evaluation date');
    expect(listed).toContain('No strata have been added — at least one is required');

    // ...and the counts they were holding back.
    expect(await screen.findByLabelText('Opening info: 6 required fields missing')).toBeTruthy();
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

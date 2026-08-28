import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import ProtocolChecklistPage from './index';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      getChecklist: vi.fn(),
      submit: vi.fn(),
      unsubmit: vi.fn(),
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

vi.mock('@/hooks/useAuthorization', () => ({ useAuthorization: () => ({ canEdit: true }) }));

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

const api = API.protocolChecklist as unknown as {
  getChecklist: ReturnType<typeof vi.fn>;
  submit: ReturnType<typeof vi.fn>;
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
    // NOT NULL on BIODIVERSITY_PLOT, so a stored plot always has one — and the tab's own save rules
    // are now counted alongside the proc's, which means a plot without it would block the submit.
    assessorName: 'TESTER',
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

    await userEvent.click(await screen.findByRole('button', { name: 'Submit checklist' }));

    // Submit now pre-flights every tab first, so the call happens a couple of awaits later.
    await vi.waitFor(() => expect(api.submit).toHaveBeenCalledWith('bio', '9001'));
  });

  it('renders validation messages when submit returns 400', async () => {
    // The pre-flight passes, so the proc gets its say — and its answer still reaches the user.
    api.getChecklist.mockResolvedValue({ ...activeChecklist });
    readyChecklist();
    api.submit.mockRejectedValue({ body: { validationErrors: ['frep.submit.common.teamlead'] } });

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Submit checklist' }));

    // frep.submit.common.teamlead is mapped to friendly text (title "Opening info" + detail).
    expect(await screen.findByText('an evaluator is required.')).toBeTruthy();
  });

  it('blocks submit and turns the whole page red before calling the proc', async () => {
    // A refused submit escalates every place the outstanding items are reported — page banner, tab
    // counts, per-tab disclosure — so the page reads as one answer rather than three remarks.
    api.getChecklist.mockResolvedValue({ ...activeChecklist });
    api.getBiodiversityOpening.mockResolvedValue({ checklistId: '9001', grossArea: '50' });
    api.listBioStrata.mockResolvedValue([]);

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Submit checklist' }));

    // One page-level banner, and it has replaced the informational tally rather than joining it.
    await screen.findByText('Checklist not submitted');
    expect(api.submit).not.toHaveBeenCalled();
    expect(
      screen.getByText(
        'All required items must be complete before it can be submitted. ' +
          'The tab counts show what is still outstanding.',
      ),
    ).toBeTruthy();
    expect(screen.queryByText(/required items outstanding across/)).toBeNull();

    // The tab count and the tab's own disclosure carry the refusal too.
    // Queried as the badge itself: the svg carries both an aria-label and a <title>, so a text
    // lookup matches the element and its tab panel alike.
    expect(
      document.querySelector('.protocol-checklist__tab-status')?.getAttribute('aria-label'),
    ).toBe('Opening info: 6 items outstanding, blocking submit');
    expect(document.querySelector('.protocol-checklist__outstanding-toggle--error')).not.toBeNull();

    // Each tab lists its own outstanding work in its disclosure panel.
    const listed = Array.from(
      document.querySelectorAll('.protocol-checklist__outstanding-list li'),
    ).map((li) => li.textContent);
    expect(listed).toContain('Evaluation date, in the Evaluation section');
    expect(listed).toContain('No strata have been added — at least one is required');
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
    expect(screen.queryByRole('button', { name: 'Submit checklist' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Unsubmit checklist' })).toBeNull();
  });
});

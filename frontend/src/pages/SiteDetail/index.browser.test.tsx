import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import SiteDetailPage from './index';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    siteDetail: {
      getSiteDetail: vi.fn(),
      saveResources: vi.fn(),
    },
    configuration: {
      getRejectionReasons: vi.fn().mockResolvedValue([
        { code: 'OTH', description: 'Other' },
        { code: 'DUP', description: 'Duplicate' },
      ]),
    },
  },
}));

vi.mock('@/hooks/useAuthorization', () => ({ useAuthorization: () => ({ canEdit: true }) }));

// Stable display reference — the page's load effect depends on it, so a fresh fn per render churns.
const { display } = vi.hoisted(() => ({ display: vi.fn() }));
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display }),
}));

const api = API.siteDetail as unknown as Record<string, ReturnType<typeof vi.fn>>;

const siteDetail = (status: string, checklistStatusCode: string | null = null) => ({
  frepSelectedSiteId: '1001',
  masterList: '2024/2025',
  orgUnit: 'DCK',
  client: 'c',
  clientName: 'n',
  opening: 'A12345',
  openingId: '987654',
  actualOpening: '987654',
  licenceNo: 'L1',
  actualLicence: 'L1',
  cuttingPermitId: 'CP',
  cutBlockId: 'CB',
  fspLink: 'FSP',
  harvestYear: '2024',
  resources: [
    {
      resourceValueId: '8001',
      resourceType: 'BIO',
      resourceName: 'Biodiversity',
      statusCode: status,
      rejectionReasonCode: null,
      rationale: null,
      otherComments: null,
      checklistId: null,
      checklistStatusCode,
      revisionCount: '3',
    },
  ],
});

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/site-detail/1001']}>
      <Routes>
        <Route path="/site-detail/:id" element={<SiteDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );

describe('SiteDetailPage resource editing', () => {
  afterEach(() => vi.clearAllMocks());

  it('edits a resource status and saves the round-tripped payload', async () => {
    api.getSiteDetail.mockResolvedValue(siteDetail('REJ'));
    api.saveResources.mockResolvedValue(siteDetail('ACC'));

    renderPage();

    // The form is editable by default — no "Edit resources" toggle. The first
    // combobox is the status select (the second is the rejection-reason select).
    const [statusSelect] = await screen.findAllByRole('combobox');
    await userEvent.selectOptions(statusSelect, 'ACC');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(api.saveResources).toHaveBeenCalledTimes(1);
    expect(api.saveResources.mock.calls[0][0]).toBe('1001');
    const payload = api.saveResources.mock.calls[0][1];
    expect(payload[0]).toMatchObject({
      resourceValueId: '8001',
      statusCode: 'ACC',
      revisionCount: '3',
    });
  });

  it('shows an editable form (and Save) by default for authorized users', async () => {
    api.getSiteDetail.mockResolvedValue(siteDetail('ACC'));
    renderPage();
    expect((await screen.findAllByRole('combobox')).length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: 'Save' })).toBeTruthy();
  });

  it('blocks save and shows an error when a rejected resource has no reason', async () => {
    api.getSiteDetail.mockResolvedValue(siteDetail('ACC'));

    renderPage();

    const [statusSelect] = await screen.findAllByRole('combobox');
    await userEvent.selectOptions(statusSelect, 'REJ'); // REJ requires a rejection reason
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(api.saveResources).not.toHaveBeenCalled();
    expect(display).toHaveBeenCalledWith(
      expect.objectContaining({ kind: 'error', title: 'Please fix the following before saving' }),
    );
  });

  it('allows empty status and does not save when no row has a status selected', async () => {
    api.getSiteDetail.mockResolvedValue(siteDetail('')); // resource loads with no status

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Save' }));

    expect(api.saveResources).not.toHaveBeenCalled();
    expect(display).toHaveBeenCalledWith(
      expect.objectContaining({ kind: 'info', title: 'Nothing to save' }),
    );
  });

  it('locks a submitted resource row, shows a Submitted lock icon, and disables Save', async () => {
    api.getSiteDetail.mockResolvedValue(siteDetail('ACC', 'SUB'));

    renderPage();

    // Submitted row renders read-only — no editable selects — and shows the lock icon.
    expect(await screen.findByRole('button', { name: 'Submitted' })).toBeTruthy();
    expect(screen.queryByRole('combobox')).toBeNull();
    // All resources submitted → Save is disabled (legacy FREP110 parity).
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
  });
});

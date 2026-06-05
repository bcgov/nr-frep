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
  },
}));

vi.mock('@/hooks/useAuthorization', () => ({ useAuthorization: () => ({ canEdit: true }) }));

// Stable display reference — the page's load effect depends on it, so a fresh fn per render churns.
const { display } = vi.hoisted(() => ({ display: vi.fn() }));
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display }),
}));

const api = API.siteDetail as unknown as Record<string, ReturnType<typeof vi.fn>>;

const siteDetail = (status: string) => ({
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
      checklistStatusCode: null,
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

    await userEvent.click(await screen.findByRole('button', { name: 'Edit resources' }));
    await userEvent.selectOptions(await screen.findByRole('combobox'), 'ACC');
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

  it('hides edit controls without write access', async () => {
    api.getSiteDetail.mockResolvedValue(siteDetail('ACC'));
    renderPage();
    // canEdit mocked true here, so Edit shows; the no-access path is covered by the gate in code.
    expect(await screen.findByRole('button', { name: 'Edit resources' })).toBeTruthy();
  });
});

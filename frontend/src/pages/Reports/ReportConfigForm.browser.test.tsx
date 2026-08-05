import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ReportConfigForm from './ReportConfigForm';
import { GENERATABLE_REPORTS } from './reportDefinitions';

import * as reportsService from '@/services/reports';

vi.mock('@/services/reports', () => ({
  requestReport: vi.fn(),
  requestCsvReport: vi.fn(),
  openBlobInNewTab: vi.fn(),
  triggerBrowserDownload: vi.fn(),
}));

// Mutable so a test can switch the persona; default = admin-like (sees everything, no CHR scoping).
const { auth } = vi.hoisted(() => ({
  auth: {
    value: { canEdit: true, canAnyChr: true, isSysAdmin: true, chrDistricts: [] as string[] },
  },
}));
vi.mock('@/hooks/useAuthorization', () => ({ useAuthorization: () => auth.value }));

const { display } = vi.hoisted(() => ({ display: vi.fn() }));
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display }),
}));

const svc = reportsService as unknown as {
  requestReport: ReturnType<typeof vi.fn>;
  requestCsvReport: ReturnType<typeof vi.fn>;
  openBlobInNewTab: ReturnType<typeof vi.fn>;
  triggerBrowserDownload: ReturnType<typeof vi.fn>;
};

const ORG_UNITS = [
  {
    orgUnitNo: '5',
    orgUnitCode: 'DCC',
    orgUnitName: 'Cariboo-Chilcotin Natural Resource District',
  },
  {
    orgUnitNo: '7',
    orgUnitCode: 'DKM',
    orgUnitName: 'Coast Mountains Natural Resource District',
  },
];
const YEARS = [{ effectiveYear: '2026', label: '26/27', current: true }];

const pdfDefinition = GENERATABLE_REPORTS.find((r) => r.id === 'checklist-completion-status')!;
const csvDefinition = GENERATABLE_REPORTS.find((r) => r.id === 'chr-data-extract')!;

const renderForm = (definition: (typeof GENERATABLE_REPORTS)[number]) =>
  render(
    <ReportConfigForm
      definition={definition}
      orgUnits={ORG_UNITS}
      masterListYears={YEARS}
      resourceValueStatuses={[]}
      checklistStatuses={[]}
      loading={false}
    />,
  );

describe('ReportConfigForm — export filenames', () => {
  afterEach(() => vi.clearAllMocks());

  it('a PDF opens a preview tab AND downloads a descriptively-named copy', async () => {
    svc.requestReport.mockResolvedValue({
      blob: new Blob(['pdf'], { type: 'application/pdf' }),
      filename: 'backend-default.pdf',
      contentType: 'application/pdf',
    });

    renderForm(pdfDefinition);
    await userEvent.click(screen.getByRole('button', { name: 'Generate PDF' }));

    await waitFor(() => expect(svc.triggerBrowserDownload).toHaveBeenCalledTimes(1));
    // Preview tab opened, and the download carries the descriptive .pdf name (not the backend default).
    expect(svc.openBlobInNewTab).toHaveBeenCalledTimes(1);
    expect(svc.triggerBrowserDownload.mock.calls[0][1]).toBe(
      'FREP_checklist_completion_status.pdf',
    );
  });

  it('a PDF filename includes the selected filter values (client + licence)', async () => {
    svc.requestReport.mockResolvedValue({
      blob: new Blob(['pdf'], { type: 'application/pdf' }),
      filename: 'backend-default.pdf',
      contentType: 'application/pdf',
    });

    renderForm(pdfDefinition);
    // Completion Status filters by client + licence (and a date range) — no org unit / year fields.
    await userEvent.type(screen.getByLabelText(/Client number/), '00012345');
    await userEvent.type(screen.getByLabelText(/Licence number/), 'A20015');
    await userEvent.click(screen.getByRole('button', { name: 'Generate PDF' }));

    await waitFor(() => expect(svc.triggerBrowserDownload).toHaveBeenCalledTimes(1));
    // parts order: client number then licence.
    expect(svc.triggerBrowserDownload.mock.calls[0][1]).toBe(
      'FREP_checklist_completion_status_(00012345)_(A20015).pdf',
    );
  });

  it('a CSV downloads with a descriptive name and does not open a tab', async () => {
    svc.requestCsvReport.mockResolvedValue({
      blob: new Blob(['csv'], { type: 'text/csv' }),
      filename: 'backend-default.csv',
      contentType: 'text/csv',
    });

    renderForm(csvDefinition);
    // chr-data-extract requires org unit + master-list year.
    // Labels carry a trailing " *" (required marker), so match on a substring.
    await userEvent.selectOptions(screen.getByLabelText(/Organization unit/), 'DCC');
    await userEvent.selectOptions(screen.getByLabelText(/Master list year/), '2026');
    await userEvent.click(screen.getByRole('button', { name: 'Export CSV' }));

    await waitFor(() => expect(svc.triggerBrowserDownload).toHaveBeenCalledTimes(1));
    expect(svc.triggerBrowserDownload.mock.calls[0][1]).toBe(
      '(DCC)_FREP_chr_data_extract_(2026_2027).csv',
    );
    expect(svc.openBlobInNewTab).not.toHaveBeenCalled();
  });
});

describe('ReportConfigForm — CHR Data Extract district scoping', () => {
  afterEach(() => {
    vi.clearAllMocks();
    auth.value = { canEdit: true, canAnyChr: true, isSysAdmin: true, chrDistricts: [] };
  });

  const orgUnitOptionTexts = () =>
    Array.from((screen.getByLabelText(/Organization unit/) as HTMLSelectElement).options).map(
      (o) => o.textContent,
    );

  it('a district-scoped CHR user sees only their districts and no "All districts"', () => {
    auth.value = { canEdit: true, canAnyChr: true, isSysAdmin: false, chrDistricts: ['DCC'] };
    renderForm(csvDefinition); // chr-data-extract

    const texts = orgUnitOptionTexts();
    expect(texts).not.toContain('— All —');
    expect(texts.some((t) => t?.includes('DCC'))).toBe(true);
    expect(texts.some((t) => t?.includes('DKM'))).toBe(false); // a district they lack
  });

  it('an admin gets the "All districts" option and every district', () => {
    renderForm(csvDefinition); // default auth = admin-like

    const texts = orgUnitOptionTexts();
    expect(texts).toContain('— All —');
    expect(texts.some((t) => t?.includes('DKM'))).toBe(true);
  });
});

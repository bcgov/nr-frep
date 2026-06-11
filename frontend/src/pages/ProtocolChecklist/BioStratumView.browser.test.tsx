import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import BioStratumView from './BioStratumView';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      listBioStrata: vi.fn(),
      getBioStratum: vi.fn(),
      getStratumComputed: vi.fn().mockResolvedValue({ nar: '12.3', plotsCompleted: '2' }),
      getNewStratumComputed: vi.fn().mockResolvedValue({ nar: '12.3', plotsCompleted: '0' }),
      saveBioStratum: vi.fn(),
      deleteBioStratum: vi.fn(),
    },
    configuration: {
      getStrataTypes: vi.fn().mockResolvedValue([
        { code: 'CC', description: 'CC - Clear cut' },
        { code: 'P1', description: 'P1 - Patch' },
      ]),
      searchBec: vi.fn().mockResolvedValue([]),
    },
  },
}));

vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: vi.fn() }),
}));

const api = API.protocolChecklist as unknown as {
  listBioStrata: ReturnType<typeof vi.fn>;
  getBioStratum: ReturnType<typeof vi.fn>;
  saveBioStratum: ReturnType<typeof vi.fn>;
  deleteBioStratum: ReturnType<typeof vi.fn>;
};

describe('BioStratumView', () => {
  afterEach(() => vi.clearAllMocks());

  it('keeps the form hidden until a stratum is opened', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.getBioStratum.mockResolvedValue({
      stratumId: 'S1',
      checklistId: '9001',
      stratumNumber: '1',
      windthrowTreatments: [],
      revisionCount: '2',
    });

    render(<BioStratumView checklistId="9001" canEdit submitted={false} />);

    // Table + Add stratum are present, but the form is not rendered yet.
    expect(await screen.findByRole('button', { name: 'Add stratum' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();

    // Editing a row opens the form.
    await userEvent.click(screen.getByRole('button', { name: 'Edit' }));
    expect(await screen.findByRole('button', { name: 'Save' })).toBeTruthy();
    // …and it can be closed again.
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
  });

  it('edits a stratum from the table and saves it', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.getBioStratum.mockResolvedValue({
      stratumId: 'S1',
      checklistId: '9001',
      stratumNumber: 'A1', // letters-then-digits mask (legacy validate_stratum_number)
      strataTypeCode: 'CC', // required (non-patch → harvest need not be PCH)
      consistentMapInd: 'Y', // required; 'Y' → stratum size required
      size: '2.5',
      plotCount: '3', // required
      harvestAreaCode: 'HNR', // required
      bgcZoneCode: 'CWH', // required
      windthrowTreatments: [],
      revisionCount: '2',
    });
    api.saveBioStratum.mockResolvedValue({ stratumId: 'S1', revisionCount: '3' });

    render(<BioStratumView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Save' }));

    expect(api.saveBioStratum).toHaveBeenCalledTimes(1);
    expect(api.saveBioStratum.mock.calls[0][0]).toBe('9001');
    // On save success the form closes and we return to the table.
    expect(await screen.findByRole('button', { name: 'Add stratum' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
  });

  it('blocks save and does not call the API when required fields are blank', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.getBioStratum.mockResolvedValue({
      stratumId: 'S1',
      checklistId: '9001',
      stratumNumber: '1',
      windthrowTreatments: [],
      revisionCount: '2',
    }); // plotCount / harvestAreaCode / bgcZoneCode intentionally blank

    render(<BioStratumView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Save' }));

    expect(api.saveBioStratum).not.toHaveBeenCalled();
  });

  it('blocks save when the Stratum Id breaks the letters-then-digits mask', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.getBioStratum.mockResolvedValue({
      stratumId: 'S1',
      checklistId: '9001',
      stratumNumber: '1', // digit-first → rejected by validate_stratum_number
      strataTypeCode: 'CC',
      consistentMapInd: 'Y',
      size: '2.5',
      plotCount: '3',
      harvestAreaCode: 'HNR',
      bgcZoneCode: 'CWH',
      windthrowTreatments: [],
      revisionCount: '2',
    });

    render(<BioStratumView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Save' }));

    expect(api.saveBioStratum).not.toHaveBeenCalled();
  });

  it('opens a blank stratum form on Add (no prefilled Stratum Id)', async () => {
    api.listBioStrata.mockResolvedValue([]);

    render(<BioStratumView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Add stratum' }));

    // The new (unsaved) stratum detail renders its Save action…
    expect(await screen.findByRole('button', { name: 'Save' })).toBeTruthy();
    // …with the Stratum Id field blank (no legacy sequence-value prefill).
    expect(screen.getByLabelText(/Stratum Id/i)).toHaveValue('');
  });

  it('shows the strata table with type label and deletes a row', async () => {
    api.listBioStrata.mockResolvedValue([
      { stratumId: 'S1', stratumNumber: 'A1', strataTypeCode: 'CC', revisionCount: '2' },
    ]);
    api.deleteBioStratum.mockResolvedValue('');

    render(<BioStratumView checklistId="9001" canEdit submitted={false} />);

    // Table columns + a row with the strata-type label resolved from the code.
    expect(await screen.findByRole('columnheader', { name: 'Stratum number' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Stratum type' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Actions' })).toBeTruthy();
    expect(screen.getByRole('cell', { name: 'A1' })).toBeTruthy();
    // Type label resolves from the code once getStrataTypes loads.
    expect(await screen.findByRole('cell', { name: 'CC - Clear cut' })).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: 'Delete' }));
    expect(api.deleteBioStratum).toHaveBeenCalledWith('S1', '2');
  });

  it('is read-only when submitted (Edit only, no Add or Delete)', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);

    render(<BioStratumView checklistId="9001" canEdit submitted />);

    expect(await screen.findByRole('button', { name: 'Edit' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Add stratum' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Delete' })).toBeNull();
  });
});

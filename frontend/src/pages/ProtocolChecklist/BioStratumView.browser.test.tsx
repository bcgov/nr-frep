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
      saveBioStratum: vi.fn(),
      deleteBioStratum: vi.fn(),
      nextStratumNumber: vi.fn(),
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
  nextStratumNumber: ReturnType<typeof vi.fn>;
};

describe('BioStratumView', () => {
  afterEach(() => vi.clearAllMocks());

  it('selects a stratum from the rail and saves it', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.getBioStratum.mockResolvedValue({
      stratumId: 'S1',
      checklistId: '9001',
      stratumNumber: '1',
      plotCount: '3', // required by the proc
      harvestAreaCode: 'HNR', // required
      bgcZoneCode: 'CWH', // required
      windthrowTreatments: [],
      revisionCount: '2',
    });
    api.saveBioStratum.mockResolvedValue({ stratumId: 'S1', revisionCount: '3' });

    render(<BioStratumView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: '1' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Save stratum' }));

    expect(api.saveBioStratum).toHaveBeenCalledTimes(1);
    expect(api.saveBioStratum.mock.calls[0][0]).toBe('9001');
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

    await userEvent.click(await screen.findByRole('button', { name: '1' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Save stratum' }));

    expect(api.saveBioStratum).not.toHaveBeenCalled();
  });

  it('adds a new stratum via the next-number service', async () => {
    api.listBioStrata.mockResolvedValue([]);
    api.nextStratumNumber.mockResolvedValue({ stratumNumber: '7' });

    render(<BioStratumView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Add stratum' }));

    expect(api.nextStratumNumber).toHaveBeenCalledTimes(1);
    // The new (unsaved) stratum detail renders its Save action.
    expect(await screen.findByRole('button', { name: 'Save stratum' })).toBeTruthy();
  });

  it('is read-only when submitted (no Add stratum)', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);

    render(<BioStratumView checklistId="9001" canEdit submitted />);

    expect(await screen.findByRole('button', { name: '1' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Add stratum' })).toBeNull();
  });
});

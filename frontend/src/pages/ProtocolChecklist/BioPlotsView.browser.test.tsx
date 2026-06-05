import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import BioPlotsView from './BioPlotsView';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      listBioStrata: vi.fn(),
      listBioPlots: vi.fn(),
      getBioPlot: vi.fn(),
      saveBioPlot: vi.fn(),
      deleteBioPlot: vi.fn(),
    },
  },
}));

vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: vi.fn() }),
}));

const api = API.protocolChecklist as unknown as {
  listBioStrata: ReturnType<typeof vi.fn>;
  listBioPlots: ReturnType<typeof vi.fn>;
  getBioPlot: ReturnType<typeof vi.fn>;
  saveBioPlot: ReturnType<typeof vi.fn>;
  deleteBioPlot: ReturnType<typeof vi.fn>;
};

describe('BioPlotsView', () => {
  afterEach(() => vi.clearAllMocks());

  it('prompts to add a stratum first when none exist', async () => {
    api.listBioStrata.mockResolvedValue([]);

    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);

    expect(
      await screen.findByText('Add a stratum on the Stratum summary tab before adding plots.'),
    ).toBeTruthy();
  });

  it('adds a plot under the selected stratum and saves it', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([]);
    api.saveBioPlot.mockResolvedValue({ plotId: 'P1', stratumId: 'S1', revisionCount: '1' });

    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Add plot' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Save plot' }));

    expect(api.saveBioPlot).toHaveBeenCalledTimes(1);
    expect(api.saveBioPlot.mock.calls[0][0]).toBe('S1');
  });

  it('is read-only when submitted (no Add plot)', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([{ plotId: 'P1', plotNumber: '1' }]);

    render(<BioPlotsView checklistId="9001" canEdit submitted />);

    expect(await screen.findByRole('button', { name: '1' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Add plot' })).toBeNull();
  });
});

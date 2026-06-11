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
    configuration: {
      getSpecies: vi.fn().mockResolvedValue([{ code: 'FD', description: 'FD - Douglas-fir' }]),
      getWildlifeTreeDecay: vi.fn().mockResolvedValue([{ code: '1', description: '1 - Live' }]),
      getCwdDecay: vi.fn().mockResolvedValue([{ code: '1', description: '1 - Sound' }]),
      getStrataTypes: vi
        .fn()
        .mockResolvedValue([{ code: 'DO', description: 'DO - Dispersed Other' }]),
      getEvaluators: vi.fn().mockResolvedValue([{ code: 'IDIR\\JDOE', description: 'JDOE' }]),
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

const config = API.configuration as unknown as {
  getEvaluators: ReturnType<typeof vi.fn>;
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
    await userEvent.click(await screen.findByRole('button', { name: 'Save' }));

    expect(api.saveBioPlot).toHaveBeenCalledTimes(1);
    expect(api.saveBioPlot.mock.calls[0][0]).toBe('S1');
    // On save success the form closes and we return to the table.
    expect(await screen.findByRole('button', { name: 'Add plot' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
  });

  it('shows the plots table and deletes a row', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([
      { plotId: 'P1', plotNumber: '1', assessorName: 'jdoe', revisionCount: '2' },
    ]);
    api.deleteBioPlot.mockResolvedValue('');

    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);

    expect(await screen.findByRole('columnheader', { name: 'Plot number' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Assessor name' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Actions' })).toBeTruthy();
    expect(screen.getByRole('cell', { name: 'jdoe' })).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: 'Delete' }));
    expect(api.deleteBioPlot).toHaveBeenCalledWith('P1', '2');
  });

  it('shows the Stand table only after "Trees exist" is checked', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([]);

    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Add plot' }));
    // Conditional: no Stand table until the indicator is on.
    expect(screen.queryByText('Stand table (trees)')).toBeNull();

    await userEvent.click(screen.getByRole('checkbox', { name: 'Trees exist' }));
    expect(await screen.findByText('Stand table (trees)')).toBeTruthy();
  });

  it('blocks Add and shows a notice when the checklist has no evaluator', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([]);
    config.getEvaluators.mockResolvedValueOnce([]); // no team members saved

    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);

    expect(
      await screen.findByText(
        'Plots cannot be added until an Evaluator has been saved on the Administration tab.',
      ),
    ).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Add plot' })).toBeDisabled();
  });

  it('is read-only when submitted (Edit only, no Add or Delete)', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([{ plotId: 'P1', plotNumber: '1' }]);

    render(<BioPlotsView checklistId="9001" canEdit submitted />);

    expect(await screen.findByRole('button', { name: 'Edit' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Add plot' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Delete' })).toBeNull();
  });
});

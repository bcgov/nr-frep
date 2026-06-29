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
    // No UTM signal for this plot (so UTM coords aren't required); both bearing legs are required.
    await userEvent.click(await screen.findByRole('checkbox', { name: 'No UTM signal available' }));
    await userEvent.type(await screen.findByLabelText('Bearing 1st leg', { exact: false }), '120');
    await userEvent.type(screen.getByLabelText('2nd leg', { exact: false }), '240');
    // Evaluated by is required, and exactly one measurement method (BAF) must be entered.
    await userEvent.selectOptions(
      screen.getByLabelText('Evaluated by', { exact: false }),
      'IDIR\\JDOE',
    );
    await userEvent.type(screen.getByLabelText('BAF', { exact: false }), '10');
    await userEvent.click(await screen.findByRole('button', { name: 'Save' }));

    expect(api.saveBioPlot).toHaveBeenCalledTimes(1);
    expect(api.saveBioPlot.mock.calls[0][0]).toBe('S1');
    // On save success the form closes and we return to the table.
    expect(await screen.findByRole('button', { name: 'Add plot' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
  });

  it('blocks the save until both bearing legs are entered', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([]);
    api.saveBioPlot.mockResolvedValue({ plotId: 'P1', stratumId: 'S1', revisionCount: '1' });

    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Add plot' }));
    await userEvent.click(await screen.findByRole('checkbox', { name: 'No UTM signal available' }));
    // No bearing legs entered → save is blocked and the fields show inline errors.
    await userEvent.click(await screen.findByRole('button', { name: 'Save' }));
    expect(api.saveBioPlot).not.toHaveBeenCalled();
    expect(screen.getByText('Bearing 1st leg is required.')).toBeTruthy();
    expect(screen.getByText('2nd leg is required.')).toBeTruthy();

    // Fill both legs, Evaluated by, and one measurement method → save proceeds.
    await userEvent.type(screen.getByLabelText('Bearing 1st leg', { exact: false }), '120');
    await userEvent.type(screen.getByLabelText('2nd leg', { exact: false }), '240');
    await userEvent.selectOptions(
      screen.getByLabelText('Evaluated by', { exact: false }),
      'IDIR\\JDOE',
    );
    await userEvent.type(screen.getByLabelText('BAF', { exact: false }), '10');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(api.saveBioPlot).toHaveBeenCalledTimes(1);
  });

  it('requires valid UTM coordinates when "No UTM signal available" is unchecked', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([]);
    api.saveBioPlot.mockResolvedValue({ plotId: 'P1', stratumId: 'S1', revisionCount: '1' });

    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Add plot' }));
    // Signal available by default → Zone/Easting/Northing are required. Fill bearing legs so only
    // UTM blocks the save.
    await userEvent.type(await screen.findByLabelText('Bearing 1st leg', { exact: false }), '120');
    await userEvent.type(screen.getByLabelText('2nd leg', { exact: false }), '240');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(api.saveBioPlot).not.toHaveBeenCalled();
    expect(screen.getByText('Easting is required.')).toBeTruthy();
    expect(screen.getByText('Northing is required.')).toBeTruthy();

    // Too-short easting → length error (the input is also capped at 6 chars by maxLength).
    await userEvent.type(screen.getByLabelText('Easting', { exact: false }), '123');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(api.saveBioPlot).not.toHaveBeenCalled();
    expect(screen.getByText('Easting must be exactly 6 digits.')).toBeTruthy();
  });

  it('shows an inline error when "Trees exist" is checked but the stand table is empty', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([]);
    api.saveBioPlot.mockResolvedValue({ plotId: 'P1', stratumId: 'S1', revisionCount: '1' });

    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Add plot' }));
    await userEvent.type(await screen.findByLabelText('Bearing 1st leg', { exact: false }), '120');
    await userEvent.type(screen.getByLabelText('2nd leg', { exact: false }), '240');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Trees exist' }));

    // Trees exist but no stand rows → save blocked with an inline stand-table error.
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(api.saveBioPlot).not.toHaveBeenCalled();
    expect(screen.getByText('Stand table required')).toBeTruthy();
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

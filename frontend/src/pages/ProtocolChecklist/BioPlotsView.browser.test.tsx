import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import BioPlotsView from './BioPlotsView';

import API from '@/services/APIs';
import { autofillableCount, stillAutofillable } from '@/testing/autofill';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      listBioStrata: vi.fn(),
      listBioPlots: vi.fn(),
      getBioPlot: vi.fn(),
      saveBioPlot: vi.fn(),
      deleteBioPlot: vi.fn(),
      // The checklist Evaluator (team lead) that "Evaluated by" defaults to.
      getBiodiversityOpening: vi
        .fn()
        .mockResolvedValue({ teamLeadNameId: 'JDOE', teamLeadName: 'John Doe (JDOE)' }),
    },
    configuration: {
      // Backend returns the name only in `description`; the plot sub-table dropdowns render it as
      // "<code> - <description>".
      getSpecies: vi.fn().mockResolvedValue([{ code: 'FD', description: 'Douglas-fir' }]),
      getWildlifeTreeDecay: vi.fn().mockResolvedValue([{ code: '1', description: 'Live' }]),
      getCwdDecay: vi.fn().mockResolvedValue([{ code: '1', description: 'Sound' }]),
      getStrataTypes: vi
        .fn()
        .mockResolvedValue([{ code: 'DO', description: 'DO - Dispersed Other' }]),
    },
  },
}));

vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: vi.fn() }),
}));

vi.mock('@/context/auth/useAuth', () => ({
  useAuth: () => ({ user: { providerUsername: 'IDIR\\ME' } }),
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

    // The checklist Evaluator (from getBiodiversityOpening) is what "Evaluated by" defaults to.
    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Add plot' }));
    // No UTM signal for this plot (so UTM coords aren't required); both bearing legs are required.
    await userEvent.click(await screen.findByRole('checkbox', { name: 'No UTM signal available' }));
    await userEvent.type(await screen.findByLabelText('Bearing 1st leg', { exact: false }), '120');
    await userEvent.type(screen.getByLabelText('2nd leg', { exact: false }), '240');
    // Exactly one measurement method (BAF) must be entered. Evaluated by is pre-filled from the
    // checklist Evaluator, so no dropdown selection is needed.
    await userEvent.type(screen.getByLabelText('BAF', { exact: false }), '10');
    // Plot # is required (mirrors FREP_212_BIOPLOT.save_plot).
    await userEvent.type(screen.getByLabelText('Plot #', { exact: false }), '1');
    await userEvent.click(await screen.findByRole('button', { name: 'Save' }));

    expect(api.saveBioPlot).toHaveBeenCalledTimes(1);
    expect(api.saveBioPlot.mock.calls[0][0]).toBe('S1');
    // assessorName defaulted to the checklist Evaluator, stored as a bare userid.
    expect(api.saveBioPlot.mock.calls[0][1]).toMatchObject({ assessorName: 'JDOE' });
    // On save success the form closes and we return to the table.
    expect(await screen.findByRole('button', { name: 'Add plot' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
  });

  it('saves a plot with the bearing legs still blank, but marks them', async () => {
    // A plot recorded before the transect is walked still stores; the gap is marked inline, counted
    // on the tab and blocks submit. Plot # and Evaluated by are the two the database insists on.
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([]);
    api.saveBioPlot.mockResolvedValue({ plotId: 'P1', stratumId: 'S1', revisionCount: '1' });

    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Add plot' }));
    await userEvent.click(await screen.findByRole('checkbox', { name: 'No UTM signal available' }));
    await userEvent.type(screen.getByLabelText('Plot #', { exact: false }), '1');
    await userEvent.click(await screen.findByRole('button', { name: 'Save' }));

    expect(api.saveBioPlot).toHaveBeenCalledTimes(1);
  });

  it('still blocks the save when a bearing is out of range', async () => {
    // A value that *is* entered has to be a real bearing — 0-359.
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([]);
    api.saveBioPlot.mockResolvedValue({ plotId: 'P1', stratumId: 'S1', revisionCount: '1' });

    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Add plot' }));
    await userEvent.click(await screen.findByRole('checkbox', { name: 'No UTM signal available' }));
    await userEvent.type(screen.getByLabelText('Plot #', { exact: false }), '1');
    await userEvent.type(await screen.findByLabelText('Bearing 1st leg', { exact: false }), '400');
    await userEvent.click(await screen.findByRole('button', { name: 'Save' }));

    expect(api.saveBioPlot).not.toHaveBeenCalled();
  });

  it('requires valid UTM coordinates when "No UTM signal available" is unchecked', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([]);
    api.saveBioPlot.mockResolvedValue({ plotId: 'P1', stratumId: 'S1', revisionCount: '1' });

    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Add plot' }));
    await userEvent.type(await screen.findByLabelText('Bearing 1st leg', { exact: false }), '120');
    await userEvent.type(screen.getByLabelText('2nd leg', { exact: false }), '240');
    await userEvent.type(screen.getByLabelText('Plot #', { exact: false }), '1');

    // A too-short easting is a malformed value, not a gap — that still blocks the save (the input is
    // also capped at 6 chars by maxLength).
    await userEvent.type(screen.getByLabelText('Easting', { exact: false }), '123');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(api.saveBioPlot).not.toHaveBeenCalled();
    expect(screen.getByText('Easting must be exactly 6 digits.')).toBeTruthy();
    // The still-blank northing is marked alongside it, but on its own it would not have blocked.
    expect(screen.getByText('Northing is required.')).toBeTruthy();
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

    // Trees exist but no stand rows → marked inline and counted against submit, but the plot still
    // saves. A stand row the user has *added* must be complete, because every column of
    // BIODIVERSITY_STAND_DETAIL is NOT NULL — that is covered by the row-level rules.
    await userEvent.type(screen.getByLabelText('Plot #', { exact: false }), '1');
    expect(screen.getByText('Stand table required')).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(api.saveBioPlot).toHaveBeenCalledTimes(1);
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
    expect(screen.getByRole('columnheader', { name: 'Action' })).toBeTruthy();
    expect(screen.getByRole('cell', { name: 'jdoe' })).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: /Delete/ }));
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

  it('renders sub-table species options as "<code> - <description>"', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([]);

    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Add plot' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Trees exist' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Add new row' }));

    // The "Spp." dropdown shows the code prefixed — not just the description.
    expect(screen.getByRole('option', { name: 'FD - Douglas-fir' })).toBeTruthy();
    expect(screen.queryByRole('option', { name: 'Douglas-fir' })).toBeNull();
  });

  it('defaults "Evaluated by" to the checklist Evaluator and claims it via "Assign it to me"', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([]);

    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Add plot' }));

    // Defaults to the checklist Evaluator, shown with its FAM-resolved name (no dropdown).
    expect(screen.getByText('John Doe (JDOE)')).toBeTruthy();
    expect(screen.queryByRole('combobox', { name: /Evaluated by/ })).toBeNull();

    // The current user (ME) isn't the evaluator, so they can claim the plot.
    await userEvent.click(screen.getByRole('button', { name: 'Assign it to me' }));
    expect(screen.getByText('ME')).toBeTruthy();
    // Once claimed, the button is gone (current user is now the assessor).
    expect(screen.queryByRole('button', { name: 'Assign it to me' })).toBeNull();
  });

  it('is read-only when submitted (Edit only, no Add or Delete)', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([{ plotId: 'P1', plotNumber: '1' }]);

    render(<BioPlotsView checklistId="9001" canEdit submitted />);

    expect(await screen.findByRole('button', { name: 'Edit' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Add plot' })).toBeNull();
    expect(screen.queryByRole('button', { name: /Delete/ })).toBeNull();
  });
});

describe('BioPlotsView — browser autofill', () => {
  /** See the note in BioStratumView's equivalent: stable ids make every checklist field a
   *  candidate for the browser to refill from the last record it saw. */
  it('leaves no field for the browser to autofill from the previous plot', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([]);

    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Add plot' }));
    await screen.findByLabelText('Bearing 1st leg', { exact: false });

    expect(autofillableCount()).toBeGreaterThan(5);
    expect(stillAutofillable()).toEqual([]);
  });
});

describe('BioPlotsView — duplicate plot number', () => {
  /**
   * Caught before the save. Left to the database this comes back from
   * FREP_BIODIVERSITY_PLOT.VALIDATE as frep.web.usr.database.record.plot.number.already.exists.
   */
  it('reports a number another plot in the stratum already holds, and does not save', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([{ plotId: 'P1', plotNumber: '1', stratumId: 'S1' }]);

    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Add plot' }));
    await userEvent.type(await screen.findByLabelText('Plot #', { exact: false }), '1');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(
      await screen.findByText('Plot 1 already exists in this stratum. Use a different number.'),
    ).toBeTruthy();
    expect(api.saveBioPlot).not.toHaveBeenCalled();
  });

  it('does not report an existing plot as a clash with itself', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.listBioPlots.mockResolvedValue([{ plotId: 'P1', plotNumber: '1', stratumId: 'S1' }]);
    api.getBioPlot.mockResolvedValue({
      plotId: 'P1',
      stratumId: 'S1',
      plotNumber: '1',
      assessorName: 'IDIR\\TESTER',
      utmSignal: 'N',
      firstLegTransect: '120',
      secondLegTransect: '240',
      treeIndicator: 'N',
      cwdTransectIndicator: 'N',
      standTable: [],
      cwdTable: [],
    });

    api.saveBioPlot.mockResolvedValue({ plotId: 'P1', stratumId: 'S1', revisionCount: '2' });

    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);
    await userEvent.click(await screen.findByRole('button', { name: /Edit/ }));
    await screen.findByLabelText('Plot #', { exact: false });

    // Saving is what reveals the errors, so the save has to be attempted for this to mean anything.
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    // Same exclusion the proc makes: the plot being saved is not compared against itself.
    expect(screen.queryByText(/already exists in this stratum/)).toBeNull();
    expect(api.saveBioPlot).toHaveBeenCalled();
  });
});

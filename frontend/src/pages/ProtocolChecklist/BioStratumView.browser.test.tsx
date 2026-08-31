import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import BioStratumView from './BioStratumView';

import API from '@/services/APIs';
import { autofillableCount, stillAutofillable } from '@/testing/autofill';

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

  it('lists the tab\u2019s outstanding rules under the stratum they belong to', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);

    render(
      <BioStratumView
        checklistId="9001"
        canEdit
        submitted={false}
        outstanding={[
          { title: 'Stratum 1', items: ['missing Stratum type', 'missing Mapped size'] },
        ]}
      />,
    );

    expect(await screen.findByText('Outstanding in this tab')).toBeTruthy();
    // The heading carries the record, so each rule below it reads as that stratum's.
    expect(document.querySelector('.protocol-checklist__outstanding-title')?.textContent).toBe(
      'Stratum 1',
    );
    const listed = Array.from(
      document.querySelectorAll('.protocol-checklist__outstanding-list li'),
    );
    expect(listed.map((li) => li.textContent)).toEqual([
      'missing Stratum type',
      'missing Mapped size',
    ]);
  });

  it('folds the outstanding list away when the disclosure is clicked', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);

    render(
      <BioStratumView
        checklistId="9001"
        canEdit
        submitted={false}
        outstanding={[{ title: 'Stratum 1', items: ['missing Stratum type'] }]}
      />,
    );

    // Open by default: the list is the answer to "why can't I submit?".
    const toggle = await screen.findByRole('button', { name: /Outstanding in this tab/ });
    expect(document.querySelectorAll('.protocol-checklist__outstanding-list li').length).toBe(1);

    await userEvent.click(toggle);
    expect(document.querySelectorAll('.protocol-checklist__outstanding-list li').length).toBe(0);
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
  });

  it('saves a stratum that is missing its number, type and size', async () => {
    // Nullable columns: the evaluator keeps what they have. The four the database insists on (plot
    // count, consistent-with-map, harvest area, BGC zone) plus BGC subzone are supplied.
    const partial = {
      stratumId: 'S1',
      checklistId: '9001',
      stratumNumber: '',
      strataTypeCode: '',
      consistentMapInd: 'Y',
      plotCount: '2',
      harvestAreaCode: 'HDR',
      bgcZoneCode: 'CWH',
      bgcSubzoneCode: 'ds',
      windthrowTreatments: [],
      revisionCount: '2',
    };
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.getBioStratum.mockResolvedValue(partial);
    api.saveBioStratum.mockResolvedValue(partial);

    render(<BioStratumView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(api.saveBioStratum).toHaveBeenCalledTimes(1);
  });

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
      bgcSubzoneCode: 'ds', // required (legacy PT #43888)
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

  it('hides the 0-plot error until Save is clicked, then shows it and blocks the save', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.getBioStratum.mockResolvedValue({
      stratumId: 'S1',
      checklistId: '9001',
      stratumNumber: 'A1',
      strataTypeCode: 'CC', // non-patch
      consistentMapInd: 'Y',
      size: '2.5',
      plotCount: '0', // 0 plots requires a patch type → inline error
      harvestAreaCode: 'HNR',
      bgcZoneCode: 'CWH',
      bgcSubzoneCode: 'ds',
      windthrowTreatments: [],
      revisionCount: '2',
    });

    render(<BioStratumView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    // The form opens clean: an incomplete record must not greet the user with errors it has not
    // been asked to fix yet.
    expect(screen.queryByText('A stratum with 0 plots must be a patch stratum type.')).toBeNull();

    // Save is the point the user asserts the form is complete — errors surface then, and block it.
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(
      await screen.findByText('A stratum with 0 plots must be a patch stratum type.'),
    ).toBeTruthy();
    expect(api.saveBioStratum).not.toHaveBeenCalled();
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
    expect(screen.getByRole('columnheader', { name: 'Action' })).toBeTruthy();
    expect(screen.getByRole('cell', { name: 'A1' })).toBeTruthy();
    // Type label resolves from the code once getStrataTypes loads.
    expect(await screen.findByRole('cell', { name: 'CC - Clear cut' })).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: /Delete/ }));
    expect(api.deleteBioStratum).toHaveBeenCalledWith('S1', '2');
  });

  it('is read-only when submitted (Edit only, no Add or Delete)', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);

    render(<BioStratumView checklistId="9001" canEdit submitted />);

    expect(await screen.findByRole('button', { name: 'Edit' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Add stratum' })).toBeNull();
    expect(screen.queryByRole('button', { name: /Delete/ })).toBeNull();
  });
});

describe('BioStratumView — browser autofill', () => {
  afterEach(() => vi.clearAllMocks());

  /**
   * Every stratum field keeps a stable `id` (`stratum-<key>`), so without this the browser treats
   * the second stratum's field as the same one it saw on the first and offers what was typed there
   * — and accepting a single suggestion cascades into the rest of the group it infers. The values
   * are per-stratum evaluation data, so a repeat of the previous stratum is always wrong.
   */
  it('leaves no field for the browser to autofill from the previous stratum', async () => {
    api.listBioStrata.mockResolvedValue([]);
    render(<BioStratumView checklistId="9001" canEdit submitted={false} outstanding={[]} />);

    await userEvent.click(await screen.findByRole('button', { name: /Add stratum/i }));
    await screen.findByLabelText(/Stratum type/i);

    expect(autofillableCount()).toBeGreaterThan(5);
    expect(stillAutofillable()).toEqual([]);
  });
});

describe('BioStratumView — same BEC as another stratum', () => {
  afterEach(() => vi.clearAllMocks());

  /** Two saved strata with different BECs, plus per-stratum values that must not travel. */
  const stratumOne = {
    stratumId: 'S1',
    stratumNumber: '1',
    bgcZoneCode: 'CWH',
    bgcSubzoneCode: 'vm',
    bgcVariant: '1',
    bgcPhase: 'a',
    becSiteSeriesCd: '01',
    siteSeriesPhaseCd: 'b',
    seral: 'Y',
    // This stratum's own evaluation data.
    estimatedSize: '42.5',
    harvestAreaCode: 'CC',
    patchWindthrowPct: '15',
    otherWindthrowTreatmentDesc: 'Blowdown along the east edge',
    revisionCount: '1',
  };
  const stratumTwo = {
    stratumId: 'S2',
    stratumNumber: '2',
    bgcZoneCode: 'IDF',
    bgcSubzoneCode: 'dk',
    bgcVariant: '3',
    bgcPhase: '',
    becSiteSeriesCd: '05',
    siteSeriesPhaseCd: '',
    seral: 'N',
    revisionCount: '1',
  };

  const stratumField = (key: string): string =>
    (document.getElementById(`stratum-${key}`) as HTMLInputElement | null)?.value ?? '(missing)';

  const openPicker = async (saved: Record<string, unknown>[] = [stratumOne, stratumTwo]) => {
    api.listBioStrata.mockResolvedValue(
      saved.map((s) => ({ stratumId: s.stratumId, stratumNumber: s.stratumNumber })),
    );
    api.getBioStratum.mockImplementation(async (id: string) =>
      saved.find((s) => s.stratumId === id),
    );
    render(<BioStratumView checklistId="9001" canEdit submitted={false} outstanding={[]} />);
    await userEvent.click(await screen.findByRole('button', { name: /Add stratum/i }));
    await screen.findByLabelText(/Stratum type/i);
    await userEvent.click(screen.getByRole('button', { name: /Same BEC as another stratum/ }));
    // Scoped by class, not by role or text: the BEC catalogue dialog is also in the DOM while
    // closed (so `getByRole('dialog')` matches both), and the button that opens this one carries
    // the same words as its heading (so a text query matches both of those).
    return (await waitFor(() => {
      const modal = document.querySelector('.bec-copy-modal');
      if (!modal) throw new Error('copy dialog not open');
      return modal as HTMLElement;
    })) as HTMLElement;
  };

  it('offers every other stratum, not just the one before this', async () => {
    const dialog = await openPicker();

    // The reported bug: only the last stratum could be chosen.
    expect(await within(dialog).findByText('CWH')).toBeTruthy();
    expect(within(dialog).getByText('IDF')).toBeTruthy();
  });

  it('copies every field of the chosen stratum’s BEC', async () => {
    const dialog = await openPicker();
    await within(dialog).findByText('IDF');

    const row = within(dialog).getByText('IDF').closest('tr') as HTMLElement;
    await userEvent.click(within(row).getByRole('button', { name: 'Select' }));

    // All seven together: FREP_VALIDATE_BGC validates the combination, so a partial copy is worse
    // than none — including the blanks, which must overwrite rather than be skipped.
    await waitFor(() => expect(stratumField('bgcZoneCode')).toBe('IDF'));
    expect(stratumField('bgcSubzoneCode')).toBe('dk');
    expect(stratumField('bgcVariant')).toBe('3');
    expect(stratumField('bgcPhase')).toBe('');
    expect(stratumField('becSiteSeriesCd')).toBe('05');
    expect(stratumField('siteSeriesPhaseCd')).toBe('');
    expect(stratumField('seral')).toBe('N');
  });

  it('copies nothing but the BEC', async () => {
    const dialog = await openPicker();
    await within(dialog).findByText('CWH');

    const row = within(dialog).getByText('CWH').closest('tr') as HTMLElement;
    await userEvent.click(within(row).getByRole('button', { name: 'Select' }));
    await waitFor(() => expect(stratumField('bgcZoneCode')).toBe('CWH'));

    // The exact fields the user watched browser autofill drag across.
    expect(stratumField('estimatedSize')).toBe('');
    expect(stratumField('harvestAreaCode')).toBe('');
    expect(stratumField('patchWindthrowPct')).toBe('');
    expect(screen.queryByDisplayValue('Blowdown along the east edge')).toBeNull();
  });

  it('lists one entry per distinct BEC, however many strata share it', async () => {
    const twin = { ...stratumOne, stratumId: 'S3', stratumNumber: '3' };
    const dialog = await openPicker([stratumOne, stratumTwo, twin]);
    await within(dialog).findByText('IDF');

    // Strata 1 and 3 share a BEC; repeating it offers no extra choice.
    expect(within(dialog).getAllByText('CWH')).toHaveLength(1);
  });

  it('says so when no other stratum has a BEC yet', async () => {
    const dialog = await openPicker([{ stratumId: 'S1', stratumNumber: '1', revisionCount: '1' }]);
    expect(
      await within(dialog).findByText('None of the other strata have a BEC recorded yet.'),
    ).toBeTruthy();
  });

  it('offers nothing to copy when this is the first stratum', async () => {
    api.listBioStrata.mockResolvedValue([]);
    render(<BioStratumView checklistId="9001" canEdit submitted={false} outstanding={[]} />);
    await userEvent.click(await screen.findByRole('button', { name: /Add stratum/i }));
    await screen.findByLabelText(/Stratum type/i);

    expect(screen.queryByRole('button', { name: /Same BEC as another stratum/ })).toBeNull();
  });
});

/**
 * A value the column cannot hold is wrong the moment it is on screen, so it is marked as the user
 * types rather than held back until Save. The rules a correct entry passes through on its way in
 * still wait — see utils/validation.ts for the split.
 */
describe('BioStratumView — errors while the user is still typing', () => {
  afterEach(() => vi.clearAllMocks());

  const openEditor = async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: 'AB' }]);
    api.getBioStratum.mockResolvedValue({
      stratumId: 'S1',
      checklistId: '9001',
      stratumNumber: 'AB',
      strataTypeCode: 'CC',
      plotCount: '5',
      windthrowTreatments: [],
      revisionCount: '2',
    });
    render(<BioStratumView checklistId="9001" canEdit submitted={false} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
  };

  it('marks a plot count past its maximum without waiting for Save', async () => {
    await openEditor();
    const plots = screen.getByLabelText(/# of plots in stratum/i);

    await userEvent.clear(plots);
    await userEvent.type(plots, '500');

    expect(await screen.findByText(/must be at most 99/)).toBeTruthy();
    expect(api.saveBioStratum).not.toHaveBeenCalled();
  });

  it('leaves a Stratum Id alone until it is finished, then names it once the field is left', async () => {
    // "A" is the first keystroke of every valid stratum number, so the pattern is judged on blur.
    await openEditor();
    const id = screen.getByLabelText(/Stratum Id/i);

    await userEvent.clear(id);
    await userEvent.type(id, '1');
    expect(screen.queryByText(/1-3 letters then 0-2 digits/)).toBeNull();

    await userEvent.tab();
    expect(await screen.findByText(/1-3 letters then 0-2 digits/)).toBeTruthy();
  });

  it('says nothing about a field left blank', async () => {
    await openEditor();
    const plots = screen.getByLabelText(/# of plots in stratum/i);

    // Emptied and tabbed past: a gap, reported on the tab and at Save, not because it was visited.
    await userEvent.clear(plots);
    await userEvent.tab();

    expect(screen.queryByText(/Plot count is required/i)).toBeNull();
  });
});

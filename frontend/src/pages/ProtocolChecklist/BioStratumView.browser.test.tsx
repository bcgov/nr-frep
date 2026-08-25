import { render, screen, waitFor } from '@testing-library/react';
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

  it('lists the tab\u2019s outstanding rules in a banner', async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);

    render(
      <BioStratumView
        checklistId="9001"
        canEdit
        submitted={false}
        outstanding={['Stratum 1 — missing Stratum type', 'Stratum 1 — missing Mapped size']}
      />,
    );

    expect(await screen.findByText('Required fields missing')).toBeTruthy();
    expect(
      screen.getByText('2 items to resolve before this checklist can be submitted:'),
    ).toBeTruthy();
    const listed = Array.from(document.querySelectorAll('.protocol-checklist__incomplete-list li'));
    expect(listed.map((li) => li.textContent)).toEqual([
      'Stratum 1 — missing Stratum type',
      'Stratum 1 — missing Mapped size',
    ]);
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

describe('BioStratumView — same BEC as previous stratum', () => {
  afterEach(() => vi.clearAllMocks());

  /** The previous stratum, carrying a full BEC plus per-stratum values that must not travel. */
  const previous = {
    stratumId: 'S1',
    stratumNumber: '1',
    bgcZoneCode: 'CWH',
    bgcSubzoneCode: 'vm',
    bgcVariant: '1',
    bgcPhase: 'a',
    becSiteSeriesCd: '01',
    siteSeriesPhaseCd: 'b',
    seral: 'Y',
    // Everything below is this stratum's own evaluation data.
    estimatedSize: '42.5',
    harvestAreaCode: 'CC',
    patchWindthrowPct: '15',
    otherWindthrowTreatmentDesc: 'Blowdown along the east edge',
    revisionCount: '1',
  };

  /**
   * A stratum form field's value, by key.
   *
   * Read by id, not by label: the BEC search modal's criteria inputs carry the same labels and stay
   * in the DOM while the dialog is closed, so a label query can silently assert on the wrong field.
   */
  const stratumField = (key: string): string =>
    (document.getElementById(`stratum-${key}`) as HTMLInputElement | null)?.value ?? '(missing)';

  const openNewStratum = async () => {
    api.listBioStrata.mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]);
    api.getBioStratum.mockResolvedValue(previous);
    render(<BioStratumView checklistId="9001" canEdit submitted={false} outstanding={[]} />);
    await userEvent.click(await screen.findByRole('button', { name: /Add stratum/i }));
    await screen.findByLabelText(/Stratum type/i);
  };

  it('copies every field of the BEC combination', async () => {
    await openNewStratum();
    await userEvent.click(screen.getByRole('button', { name: /Same BEC as stratum 1/ }));

    // All seven together: FREP_VALIDATE_BGC validates the combination, so a partial copy is worse
    // than none.
    await waitFor(() => expect(stratumField('bgcZoneCode')).toBe('CWH'));
    expect(stratumField('bgcSubzoneCode')).toBe('vm');
    expect(stratumField('bgcVariant')).toBe('1');
    expect(stratumField('bgcPhase')).toBe('a');
    expect(stratumField('becSiteSeriesCd')).toBe('01');
    expect(stratumField('siteSeriesPhaseCd')).toBe('b');
    expect(stratumField('seral')).toBe('Y');
  });

  it('copies nothing but the BEC', async () => {
    // The whole point of the button: the browser's autofill was dragging these across too, and they
    // are per-stratum evaluation values.
    await openNewStratum();
    await userEvent.click(screen.getByRole('button', { name: /Same BEC as stratum 1/ }));
    await waitFor(() => expect(stratumField('bgcZoneCode')).toBe('CWH'));

    // The exact fields the user watched autofill drag across.
    expect(stratumField('estimatedSize')).toBe('');
    expect(stratumField('harvestAreaCode')).toBe('');
    expect(stratumField('patchWindthrowPct')).toBe('');
    expect(screen.queryByDisplayValue('42.5')).toBeNull();
    expect(screen.queryByDisplayValue('Blowdown along the east edge')).toBeNull();
  });

  it('offers nothing to copy when this is the first stratum', async () => {
    api.listBioStrata.mockResolvedValue([]);
    render(<BioStratumView checklistId="9001" canEdit submitted={false} outstanding={[]} />);
    await userEvent.click(await screen.findByRole('button', { name: /Add stratum/i }));
    await screen.findByLabelText(/Stratum type/i);

    expect(screen.queryByRole('button', { name: /Same BEC as stratum/ })).toBeNull();
  });
});

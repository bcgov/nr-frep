import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import BioOpeningView from './BioOpeningView';

import API from '@/services/APIs';
import { autofillableCount, stillAutofillable } from '@/testing/autofill';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      getBiodiversityOpening: vi.fn(),
      saveBiodiversityOpening: vi.fn(),
    },
    configuration: { getChecklistAnswers: vi.fn(), getSiteEvaluationCodes: vi.fn() },
  },
}));

// Stable display — the load effect depends on it (via reportError → loadData); a fresh fn each
// render would re-fire the effect and bounce the view back into its loading skeleton.
const { display } = vi.hoisted(() => ({ display: vi.fn() }));
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display }),
}));

vi.mock('@/context/auth/useAuth', () => ({
  useAuth: () => ({ user: { providerUsername: 'IDIR\\ME' } }),
}));

const api = API.protocolChecklist as unknown as {
  getBiodiversityOpening: ReturnType<typeof vi.fn>;
  saveBiodiversityOpening: ReturnType<typeof vi.fn>;
};
const config = API.configuration as unknown as {
  getChecklistAnswers: ReturnType<typeof vi.fn>;
  getSiteEvaluationCodes: ReturnType<typeof vi.fn>;
};

describe('BioOpeningView', () => {
  afterEach(() => vi.clearAllMocks());

  it('loads the opening and saves edits inline', async () => {
    api.getBiodiversityOpening.mockResolvedValue({
      checklistId: '9001',
      locationDescription: 'old',
      // All required fields filled so the save isn't blocked by inline validation.
      evaluationDate: '2024-06-01',
      teamLeadNameId: 'IDIR\\ME',
      invasivePlantIndicator: 'N',
      innovativePracticeInd: 'N',
      frepSiteEvaluationCode: 'M',
      revisionCount: '3',
    });
    api.saveBiodiversityOpening.mockResolvedValue({ checklistId: '9001', revisionCount: '4' });
    config.getChecklistAnswers.mockResolvedValue([
      { code: 'Y', description: 'Yes' },
      { code: 'N', description: 'No' },
    ]);
    config.getSiteEvaluationCodes.mockResolvedValue([
      { code: 'E', description: 'Exceeds' },
      { code: 'M', description: 'Meets' },
    ]);

    render(<BioOpeningView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(api.saveBiodiversityOpening).toHaveBeenCalledTimes(1);
    expect(api.saveBiodiversityOpening.mock.calls[0][0]).toBe('9001');
  });

  it('shows no disclosure at all once the tab owes nothing', async () => {
    // The Opening tab hands the panel one group whether or not it holds anything, so an empty group
    // used to render the toggle opening onto an empty list. Nothing outstanding, nothing shown.
    const complete = {
      checklistId: '9001',
      evaluationDate: '2024-06-01',
      teamLeadNameId: 'IDIR\\ME',
      locationDescription: 'Block 12',
      innovativePracticeInd: 'N',
      invasivePlantIndicator: 'N',
      frepSiteEvaluationCode: 'M',
      revisionCount: '1',
    };
    api.getBiodiversityOpening.mockResolvedValue(complete);
    config.getChecklistAnswers.mockResolvedValue([
      { code: 'Y', description: 'Yes' },
      { code: 'N', description: 'No' },
    ]);
    config.getSiteEvaluationCodes.mockResolvedValue([{ code: 'M', description: 'Meets' }]);

    render(<BioOpeningView checklistId="9001" canEdit submitted={false} />);

    await screen.findByRole('button', { name: 'Edit' });
    expect(screen.queryByText('Outstanding in this tab')).toBeNull();
  });

  it('lists what an untouched checklist owes, and tracks the stored record not the edit buffer', async () => {
    // A brand-new checklist: only the read-only RESULTS values are populated. It still says what it
    // owes — withholding that is least helpful to someone who has done the least.
    const untouched = {
      checklistId: '9001',
      grossArea: '50',
      netArea: '40',
      harvestDate: '2025-06-01',
      revisionCount: '1',
    };
    api.getBiodiversityOpening.mockResolvedValue(untouched);
    api.saveBiodiversityOpening.mockResolvedValue({
      ...untouched,
      locationDescription: 'Block 12',
    });
    config.getChecklistAnswers.mockResolvedValue([
      { code: 'Y', description: 'Yes' },
      { code: 'N', description: 'No' },
    ]);
    config.getSiteEvaluationCodes.mockResolvedValue([{ code: 'M', description: 'Meets' }]);

    render(<BioOpeningView checklistId="9001" canEdit submitted={false} />);

    await screen.findByRole('button', { name: 'Edit' });
    expect(screen.getByText('Outstanding in this tab')).toBeTruthy();
    const listed = () =>
      Array.from(document.querySelectorAll('.protocol-checklist__outstanding-list li')).map(
        (li) => li.textContent,
      );
    expect(listed()).toContain('Location description, in the Opening identification section');

    // Typing does not move the list: it describes the *stored* record, so it stays in step with the
    // count on the tab. Reading the edit buffer instead would rewrite the list under the cursor.
    await userEvent.click(screen.getByRole('button', { name: 'Edit' }));
    await userEvent.type(screen.getByLabelText(/Location description/), 'Block 12');
    expect(listed()).toContain('Location description, in the Opening identification section');

    // The save is what moves it.
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(api.saveBiodiversityOpening).toHaveBeenCalledTimes(1);
    await waitFor(() =>
      expect(listed()).not.toContain('Location description, in the Opening identification section'),
    );
  });

  it('saves a blank Location description and warns that it is still required', async () => {
    // A part-finished Opening is a legitimate saved state: the evaluator keeps what they have, and
    // the missing answers are reported rather than refused (submit still enforces them).
    const incomplete = {
      checklistId: '9001',
      locationDescription: '',
      evaluationDate: '2024-06-01',
      teamLeadNameId: 'IDIR\\ME',
      invasivePlantIndicator: 'N',
      innovativePracticeInd: 'N',
      frepSiteEvaluationCode: '',
      revisionCount: '3',
    };
    api.getBiodiversityOpening.mockResolvedValue(incomplete);
    api.saveBiodiversityOpening.mockResolvedValue(incomplete);
    config.getChecklistAnswers.mockResolvedValue([
      { code: 'Y', description: 'Yes' },
      { code: 'N', description: 'No' },
    ]);
    config.getSiteEvaluationCodes.mockResolvedValue([{ code: 'M', description: 'Meets' }]);

    render(<BioOpeningView checklistId="9001" canEdit submitted={false} />);

    // Before any save, the tab already says what it owes.
    expect(await screen.findByText('Outstanding in this tab')).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: 'Edit' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(api.saveBiodiversityOpening).toHaveBeenCalledTimes(1);
    // Listed in tab order, one per line. Opening is one form, so the items are ungrouped.
    const listed = Array.from(
      document.querySelectorAll('.protocol-checklist__outstanding-list li'),
    );
    expect(listed.map((li) => li.textContent)).toEqual([
      'Location description, in the Opening identification section',
      'Rating, in the Evaluator opinion section',
    ]);
    expect(document.querySelector('.protocol-checklist__outstanding-title')).toBeNull();
    // A successful save closes the editor, so the inline field errors go with it — the panel is
    // what persists, and it is the reason the user can still find the gaps.
    expect(screen.getByRole('button', { name: 'Edit' })).toBeTruthy();
  });

  it('refuses to remove an evaluation date, which the proc has no way to clear', async () => {
    // FREP_210_BIO_OPENING.SAVE applies the date only when it is not null, and Oracle binds an empty
    // string as null — so a cleared date arrives as "not supplied" and the stored one survives. The
    // tab refuses the edit rather than accepting one that silently would not take, and then reading
    // the old date back into the field as though the form had ignored the user.
    const stored = {
      checklistId: '9001',
      evaluationDate: '2024-06-01',
      locationDescription: 'Block 12',
      teamLeadNameId: 'IDIR\\ME',
      invasivePlantIndicator: 'N',
      innovativePracticeInd: 'N',
      frepSiteEvaluationCode: 'M',
      revisionCount: '3',
    };
    api.getBiodiversityOpening.mockResolvedValue(stored);
    config.getChecklistAnswers.mockResolvedValue([
      { code: 'Y', description: 'Yes' },
      { code: 'N', description: 'No' },
    ]);
    config.getSiteEvaluationCodes.mockResolvedValue([{ code: 'M', description: 'Meets' }]);

    render(<BioOpeningView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    await userEvent.clear(screen.getByLabelText(/Evaluation date/));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(api.saveBiodiversityOpening).not.toHaveBeenCalled();
    expect(
      screen.getByText(
        'Evaluation date can\u2019t be removed once saved \u2014 enter a different date instead.',
      ),
    ).toBeTruthy();
  });

  it('still saves a blank evaluation date on a checklist that never had one', async () => {
    // The removal rule is deliberately narrow: nothing is being removed here, so the date stays
    // advisory like every other required field and the part-finished tab saves.
    const untouched = {
      checklistId: '9001',
      locationDescription: 'Block 12',
      teamLeadNameId: 'IDIR\\ME',
      invasivePlantIndicator: 'N',
      innovativePracticeInd: 'N',
      frepSiteEvaluationCode: 'M',
      revisionCount: '3',
    };
    api.getBiodiversityOpening.mockResolvedValue(untouched);
    api.saveBiodiversityOpening.mockResolvedValue(untouched);
    config.getChecklistAnswers.mockResolvedValue([
      { code: 'Y', description: 'Yes' },
      { code: 'N', description: 'No' },
    ]);
    config.getSiteEvaluationCodes.mockResolvedValue([{ code: 'M', description: 'Meets' }]);

    render(<BioOpeningView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(api.saveBiodiversityOpening).toHaveBeenCalledTimes(1);
    expect(api.saveBiodiversityOpening.mock.calls[0][1].evaluationDate ?? '').toBe('');
  });

  it('still blocks the save when a value is too long for its column', async () => {
    // Completeness is advisory; a value the column cannot store is not — the insert would fail with
    // ORA-12899, and the backend rejects it with a 400.
    api.getBiodiversityOpening.mockResolvedValue({
      checklistId: '9001',
      locationDescription: 'x'.repeat(60),
      evaluationDate: '2024-06-01',
      teamLeadNameId: 'IDIR\\ME',
      invasivePlantIndicator: 'N',
      innovativePracticeInd: 'N',
      frepSiteEvaluationCode: 'M',
      revisionCount: '3',
    });
    config.getChecklistAnswers.mockResolvedValue([
      { code: 'Y', description: 'Yes' },
      { code: 'N', description: 'No' },
    ]);
    config.getSiteEvaluationCodes.mockResolvedValue([{ code: 'M', description: 'Meets' }]);

    render(<BioOpeningView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(api.saveBiodiversityOpening).not.toHaveBeenCalled();
    expect(
      screen.getByText('Location description too long — the limit is 50 and this entry uses 60.'),
    ).toBeTruthy();
  });

  it('claims the current user as evaluator via "Assign it to me"', async () => {
    api.getBiodiversityOpening.mockResolvedValue({
      checklistId: '9001',
      locationDescription: 'loc',
      evaluationDate: '2024-06-01',
      invasivePlantIndicator: 'N',
      innovativePracticeInd: 'N',
      frepSiteEvaluationCode: 'M',
      teamLeadNameId: 'IDIR\\OTHER', // someone else is the lead — takeover allowed
      teamLeadName: 'Other Person',
      revisionCount: '3',
    });
    api.saveBiodiversityOpening.mockResolvedValue({ checklistId: '9001', revisionCount: '4' });
    config.getChecklistAnswers.mockResolvedValue([
      { code: 'Y', description: 'Yes' },
      { code: 'N', description: 'No' },
    ]);
    config.getSiteEvaluationCodes.mockResolvedValue([{ code: 'M', description: 'Meets' }]);

    render(<BioOpeningView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    await userEvent.click(screen.getByRole('button', { name: 'Assign it to me' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(api.saveBiodiversityOpening).toHaveBeenCalledTimes(1);
    // The saved payload names the current user as the evaluator (team lead).
    expect(api.saveBiodiversityOpening.mock.calls[0][1].teamLeadNameId).toBe('IDIR\\ME');
  });

  it('hides "Assign it to me" when the current user already is the evaluator (bare-userid form)', async () => {
    // The legacy evaluator table stores the id without the IDIR\ prefix; the button must still hide.
    api.getBiodiversityOpening.mockResolvedValue({
      checklistId: '9001',
      locationDescription: 'loc',
      evaluationDate: '2024-06-01',
      invasivePlantIndicator: 'N',
      innovativePracticeInd: 'N',
      frepSiteEvaluationCode: 'M',
      teamLeadNameId: 'me', // bare + lower-cased — normalizes to the logged-in IDIR\ME
      teamLeadName: 'Me Myself (ME)',
      revisionCount: '3',
    });
    config.getChecklistAnswers.mockResolvedValue([]);
    config.getSiteEvaluationCodes.mockResolvedValue([{ code: 'M', description: 'Meets' }]);

    render(<BioOpeningView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));

    expect(screen.queryByRole('button', { name: 'Assign it to me' })).toBeNull();
  });

  it('hides the Edit control for a submitted checklist', async () => {
    api.getBiodiversityOpening.mockResolvedValue({ checklistId: '9001', revisionCount: '5' });
    config.getChecklistAnswers.mockResolvedValue([]);
    config.getSiteEvaluationCodes.mockResolvedValue([]);

    render(<BioOpeningView checklistId="9001" canEdit submitted />);

    expect(await screen.findByText('Opening identification')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Edit' })).toBeNull();
  });
});

describe('BioOpeningView — browser autofill', () => {
  /** See the note in BioStratumView's equivalent: stable ids make every checklist field a
   *  candidate for the browser to refill from the last record it saw. */
  it('leaves no field for the browser to autofill', async () => {
    render(<BioOpeningView checklistId="9001" canEdit submitted={false} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));

    expect(autofillableCount()).toBeGreaterThan(2);
    expect(stillAutofillable()).toEqual([]);
  });
});

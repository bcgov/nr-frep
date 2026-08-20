import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import BioOpeningView from './BioOpeningView';

import API from '@/services/APIs';

/**
 * Locks the Opening tab's required-field markers to the rules that actually block the user.
 *
 * Two rule sets apply, and the asterisks have to satisfy both:
 *
 * - `FREP_TOMBSTONE.validate_biodiversity_chklst` blocks *submit* on three Opening fields:
 *   evaluation date (`frep.submit.common.evaluation`), exactly one evaluation team lead
 *   (`frep.submit.common.teamlead`) and location description (`frep.submit.biodiversity.opening`).
 * - `validateOpening` blocks the tab's own *save* on those three plus the two Yes/No indicators and
 *   the rating, plus each indicator's comment when it is set to Yes.
 *
 * A field the user cannot get past without filling must say so up front; anything else must not
 * carry an asterisk, or the marker stops meaning anything.
 */

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      getBiodiversityOpening: vi.fn(),
      saveBiodiversityOpening: vi.fn(),
    },
    configuration: { getChecklistAnswers: vi.fn(), getSiteEvaluationCodes: vi.fn() },
  },
}));

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

/** The label element whose text is `text`, ignoring any trailing asterisk. */
const labelFor = (text: string): Element | undefined =>
  Array.from(document.querySelectorAll('label, .protocol-checklist__label')).find(
    (node) => node.textContent?.replace(/\s*\*\s*$/, '').trim() === text,
  );

/** True when the field's label carries the required asterisk. */
const marked = (text: string): boolean => {
  const label = labelFor(text);
  expect(label, `no label rendered for "${text}"`).toBeTruthy();
  return Boolean(label?.querySelector('.required-asterisk'));
};

const openForEditing = async (opening: Record<string, string>) => {
  api.getBiodiversityOpening.mockResolvedValue({
    checklistId: '9001',
    revisionCount: '3',
    ...opening,
  });
  config.getChecklistAnswers.mockResolvedValue([
    { code: 'Y', description: 'Yes' },
    { code: 'N', description: 'No' },
  ]);
  config.getSiteEvaluationCodes.mockResolvedValue([{ code: 'M', description: 'Meets' }]);

  render(<BioOpeningView checklistId="9001" canEdit submitted={false} />);
  await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
};

describe('Opening info — required-field markers', () => {
  afterEach(() => vi.clearAllMocks());

  it('marks every field that blocks submit', async () => {
    await openForEditing({ invasivePlantIndicator: 'N', innovativePracticeInd: 'N' });

    expect(marked('Evaluation date')).toBe(true);
    expect(marked('Evaluator')).toBe(true);
    expect(marked('Location description')).toBe(true);
  });

  it('marks the fields that block the tab’s own save', async () => {
    await openForEditing({ invasivePlantIndicator: 'N', innovativePracticeInd: 'N' });

    expect(marked('Innovative / unique forest practices used?')).toBe(true);
    expect(marked('Invasive plant species present?')).toBe(true);
    expect(marked('Rating (stand-level biodiversity maintained)')).toBe(true);
  });

  it('leaves the genuinely optional fields unmarked', async () => {
    await openForEditing({ invasivePlantIndicator: 'N', innovativePracticeInd: 'N' });

    // Optional; only format-checked, and it widens the stratum size cap rather than gating submit.
    expect(marked('FREP gross area override (ha)')).toBe(false);
    expect(marked('Rationale')).toBe(false);
    // Each comment is optional while its indicator is No.
    expect(marked('Please describe')).toBe(false);
    expect(marked('Comments')).toBe(false);
  });

  it('marks the innovative-practices comment once the indicator is Yes', async () => {
    await openForEditing({ invasivePlantIndicator: 'N', innovativePracticeInd: 'Y' });

    expect(marked('Please describe')).toBe(true);
    expect(marked('Comments')).toBe(false);
  });

  it('marks the invasive-plants comment once the indicator is Yes', async () => {
    await openForEditing({ invasivePlantIndicator: 'Y', innovativePracticeInd: 'N' });

    expect(marked('Comments')).toBe(true);
    expect(marked('Please describe')).toBe(false);
  });
});

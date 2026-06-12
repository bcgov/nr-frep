import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import BioOpeningView from './BioOpeningView';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      getBiodiversityOpening: vi.fn(),
      saveBiodiversityOpening: vi.fn(),
    },
    configuration: { getChecklistAnswers: vi.fn(), getSiteEvaluationCodes: vi.fn() },
  },
}));

vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: vi.fn() }),
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

  it('blocks the save when Location description is blank', async () => {
    api.getBiodiversityOpening.mockResolvedValue({
      checklistId: '9001',
      locationDescription: '',
      revisionCount: '3',
    });
    api.saveBiodiversityOpening.mockResolvedValue({ checklistId: '9001', revisionCount: '4' });
    config.getChecklistAnswers.mockResolvedValue([]);
    config.getSiteEvaluationCodes.mockResolvedValue([]);

    render(<BioOpeningView checklistId="9001" canEdit submitted={false} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(api.saveBiodiversityOpening).not.toHaveBeenCalled();
    expect(screen.getByText('Location description is required.')).toBeTruthy();
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

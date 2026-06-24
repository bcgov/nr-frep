import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import MasterListAdminPage from './index';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    configuration: { getMasterListYears: vi.fn() },
    masterListAdmin: {
      getMasterList: vi.fn(),
      generate: vi.fn(),
      regenerateDistrict: vi.fn(),
      saveComments: vi.fn(),
      deleteMasterList: vi.fn(),
    },
  },
}));

// Stable display — the page's load effects depend on it.
const { display } = vi.hoisted(() => ({ display: vi.fn() }));
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display }),
}));

const config = API.configuration as unknown as Record<string, ReturnType<typeof vi.fn>>;
const api = API.masterListAdmin as unknown as Record<string, ReturnType<typeof vi.fn>>;

const criteria = (generated: boolean) => ({
  effectiveYear: '2024',
  minHarvestCompleteDate: '2023-04-01',
  maxHarvestCompleteDate: '2024-03-31',
  minOpeningGrossAreaHa: 5,
  maxSitesPerDistrict: 12,
  resourceEvaluationInd: 'N',
  generationComments: 'note',
  generated,
  generationStats: generated
    ? [
        {
          orgUnitNo: '43',
          orgUnitCode: 'DCK',
          orgUnitName: 'Chilliwack',
          eligibleSites: 12,
          selectedSites: 8,
          resourceValueInd: 'N',
        },
      ]
    : [],
});

describe('MasterListAdminPage actions', () => {
  afterEach(() => vi.clearAllMocks());

  it('regenerates a single district', async () => {
    config.getMasterListYears.mockResolvedValue([
      { effectiveYear: '2024', label: '2024/2025', current: true },
    ]);
    api.getMasterList.mockResolvedValue(criteria(true));
    api.regenerateDistrict.mockResolvedValue(criteria(true));

    render(<MasterListAdminPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Regenerate' }));

    expect(api.regenerateDistrict).toHaveBeenCalledWith('2024', '43');
  });

  it('disables per-district Regenerate when the district has evaluations (ind = Y)', async () => {
    config.getMasterListYears.mockResolvedValue([
      { effectiveYear: '2024', label: '2024/2025', current: true },
    ]);
    const evaluated = criteria(true);
    evaluated.generationStats[0].resourceValueInd = 'Y';
    api.getMasterList.mockResolvedValue(evaluated);

    render(<MasterListAdminPage />);

    expect(await screen.findByRole('button', { name: 'Regenerate' })).toHaveProperty(
      'disabled',
      true,
    );
    expect(api.regenerateDistrict).not.toHaveBeenCalled();
  });

  it('saves generation comments without regenerating', async () => {
    config.getMasterListYears.mockResolvedValue([
      { effectiveYear: '2024', label: '2024/2025', current: true },
    ]);
    api.getMasterList.mockResolvedValue(criteria(true));
    api.saveComments.mockResolvedValue(criteria(true));

    render(<MasterListAdminPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Save comments' }));

    expect(api.saveComments).toHaveBeenCalledWith('2024', 'note');
  });

  it('locks generate and delete once evaluations are under way (ind = Y)', async () => {
    config.getMasterListYears.mockResolvedValue([
      { effectiveYear: '2024', label: '2024/2025', current: true },
    ]);
    api.getMasterList.mockResolvedValue({ ...criteria(true), resourceEvaluationInd: 'Y' });

    render(<MasterListAdminPage />);

    // Carbon's danger--tertiary button injects a visually-hidden "danger" span, so the Delete
    // button's accessible name is "dangerDelete list" — match it with a regex.
    expect(await screen.findByRole('button', { name: 'Generate master list' })).toHaveProperty(
      'disabled',
      true,
    );
    expect(screen.getByRole('button', { name: /Delete list/ })).toHaveProperty('disabled', true);
  });

  it('with no list (ind = empty), only generate is enabled', async () => {
    config.getMasterListYears.mockResolvedValue([
      { effectiveYear: '2024', label: '2024/2025', current: true },
    ]);
    api.getMasterList.mockResolvedValue({ ...criteria(false), resourceEvaluationInd: '' });

    render(<MasterListAdminPage />);

    expect(await screen.findByRole('button', { name: 'Generate master list' })).toHaveProperty(
      'disabled',
      false,
    );
    expect(screen.getByRole('button', { name: 'Save comments' })).toHaveProperty('disabled', true);
    expect(screen.queryByRole('button', { name: 'Delete list' })).toBeNull();
  });
});

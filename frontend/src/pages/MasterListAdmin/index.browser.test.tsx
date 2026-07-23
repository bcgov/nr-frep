import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import MasterListAdminPage from './index';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    configuration: { getMasterListYears: vi.fn(), getNewMasterListYears: vi.fn() },
    masterListAdmin: {
      getMasterList: vi.fn(),
      generate: vi.fn(),
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
        },
      ]
    : [],
});

describe('MasterListAdminPage actions', () => {
  afterEach(() => vi.clearAllMocks());

  it('saves generation comments without regenerating', async () => {
    config.getNewMasterListYears.mockResolvedValue([
      { effectiveYear: '2024', label: '2024/2025', current: true },
    ]);
    api.getMasterList.mockResolvedValue(criteria(true));
    api.saveComments.mockResolvedValue(criteria(true));

    render(<MasterListAdminPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Save comments' }));

    expect(api.saveComments).toHaveBeenCalledWith('2024', 'note');
  });

  it('locks generate and delete once evaluations are under way (ind = Y)', async () => {
    config.getNewMasterListYears.mockResolvedValue([
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
    config.getNewMasterListYears.mockResolvedValue([
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

  it('criteria inputs are editable with no list (ind = empty)', async () => {
    config.getNewMasterListYears.mockResolvedValue([
      { effectiveYear: '2024', label: '2024/2025', current: true },
    ]);
    api.getMasterList.mockResolvedValue({ ...criteria(false), resourceEvaluationInd: '' });

    render(<MasterListAdminPage />);

    expect(
      await screen.findByLabelText('Min harvest-complete date', { exact: false }),
    ).toHaveProperty('disabled', false);
    expect(screen.getByLabelText('Max harvest-complete date', { exact: false })).toHaveProperty(
      'disabled',
      false,
    );
    expect(screen.getByLabelText('Min opening gross area (ha)', { exact: false })).toHaveProperty(
      'disabled',
      false,
    );
    expect(screen.getByLabelText('Max sites per district', { exact: false })).toHaveProperty(
      'disabled',
      false,
    );
    expect(screen.getByLabelText('Generation comments')).toHaveProperty('disabled', false);
  });

  it('blocks generate and shows inline errors when criteria are invalid', async () => {
    config.getNewMasterListYears.mockResolvedValue([
      { effectiveYear: '2024', label: '2024/2025', current: true },
    ]);
    // No list yet (inputs editable), but min date is after max and out of the allowed window.
    api.getMasterList.mockResolvedValue({
      ...criteria(false),
      resourceEvaluationInd: '',
      minHarvestCompleteDate: '1990-01-01',
      maxHarvestCompleteDate: '1995-01-01',
    });

    render(<MasterListAdminPage />);

    const generate = await screen.findByRole('button', { name: 'Generate master list' });
    await userEvent.click(generate);

    expect(api.generate).not.toHaveBeenCalled();
    // Both dates are outside the allowed window, so the range error appears on each.
    expect(
      (await screen.findAllByText('Must be between 1997-06-15 and 2050-12-31.', { exact: false }))
        .length,
    ).toBeGreaterThan(0);
  });

  it('disables criteria inputs once a list exists but keeps comments editable (legacy parity)', async () => {
    config.getNewMasterListYears.mockResolvedValue([
      { effectiveYear: '2024', label: '2024/2025', current: true },
    ]);
    api.getMasterList.mockResolvedValue({ ...criteria(true), resourceEvaluationInd: 'N' });

    render(<MasterListAdminPage />);

    // Legacy frep700GenerateMasterList.jsp disables the criteria fields whenever
    // resourceEvaluationInd != '' (a list exists), but leaves Generation Comments editable.
    expect(
      await screen.findByLabelText('Min harvest-complete date', { exact: false }),
    ).toHaveProperty('disabled', true);
    expect(screen.getByLabelText('Max harvest-complete date', { exact: false })).toHaveProperty(
      'disabled',
      true,
    );
    expect(screen.getByLabelText('Min opening gross area (ha)', { exact: false })).toHaveProperty(
      'disabled',
      true,
    );
    expect(screen.getByLabelText('Max sites per district', { exact: false })).toHaveProperty(
      'disabled',
      true,
    );
    expect(screen.getByLabelText('Generation comments')).toHaveProperty('disabled', false);
  });

  it('clears a field error once the field is fixed (no re-submit needed)', async () => {
    config.getNewMasterListYears.mockResolvedValue([
      { effectiveYear: '2024', label: '2024/2025', current: true },
    ]);
    // Editable year (no list yet) with an empty min gross area → "required" on Generate.
    api.getMasterList.mockResolvedValue({
      ...criteria(false),
      resourceEvaluationInd: '',
      minOpeningGrossAreaHa: undefined,
    });

    render(<MasterListAdminPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Generate master list' }));
    expect(await screen.findByText('Min opening gross area is required.')).toBeTruthy();

    // Fixing the field clears its error immediately — without pressing Generate again.
    fireEvent.change(screen.getByLabelText('Min opening gross area (ha)', { exact: false }), {
      target: { value: '10' },
    });

    await waitFor(() =>
      expect(screen.queryByText('Min opening gross area is required.')).toBeNull(),
    );
  });

  it('offers the next not-yet-created year and defaults to the current year', async () => {
    config.getNewMasterListYears.mockResolvedValue([
      { effectiveYear: '2027', label: '2027/2028', current: false }, // synthetic next year (MAX+1)
      { effectiveYear: '2026', label: '2026/2027', current: true }, // latest existing → default
      { effectiveYear: '2025', label: '2025/2026', current: false },
    ]);
    api.getMasterList.mockResolvedValue(criteria(true));

    render(<MasterListAdminPage />);

    // The next year is selectable for generation…
    expect(await screen.findByRole('option', { name: '2027/2028' })).toBeTruthy();
    // …but the screen still defaults to the current active year.
    expect((screen.getByLabelText('Master list year') as HTMLSelectElement).value).toBe('2026');
  });
});

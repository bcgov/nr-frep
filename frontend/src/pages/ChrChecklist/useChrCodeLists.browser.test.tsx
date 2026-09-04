import { render } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  useContactRoleCodes,
  useInformationSourceCodes,
  useRatingCodes,
  useReserveTypeCodes,
} from './useChrCodeLists';
import { clearCodeListCache } from './useCodeList';

import type { CodeOption } from '@/pages/ChrChecklist/codeLists';

// The tables hold bare descriptions in Title Case, in whatever order the query returns them —
// deliberately shuffled here, and carrying a code the UI has never heard of.
vi.mock('@/services/APIs', () => ({
  default: {
    configuration: {
      getChrFeatureInfoSourceCodes: vi.fn(() =>
        Promise.resolve([
          { code: 'SP', description: 'Site Plan' },
          { code: 'AIA', description: 'Archaeological Impact Assessment' },
        ]),
      ),
      getChrReserveTypeCodes: vi.fn(() =>
        Promise.resolve([
          { code: 'CC', description: 'Clear Cut' },
          { code: 'ZZZ', description: 'Something New' },
          { code: 'PR', description: 'Patch Riparian' },
          { code: 'DR', description: 'Dispersed Riparian' },
        ]),
      ),
      getChrSiteEvaluationCodes: vi.fn(() =>
        Promise.resolve([
          { code: 'W', description: 'Well' },
          { code: 'V', description: 'Very Poorly' },
        ]),
      ),
      getChrParticipantRoleCodes: vi.fn(() =>
        Promise.resolve([{ code: 'FN', description: 'First Nations' }]),
      ),
      getChrFeatureClassCodes: vi.fn(() => Promise.resolve([])),
    },
  },
}));

/** Renders a hook and reports what it returned, once the fetch has settled. */
const collect = async (hook: () => CodeOption[]): Promise<CodeOption[]> => {
  let latest: CodeOption[] = [];
  const Probe = () => {
    latest = hook();
    return null;
  };
  render(<Probe />);
  await vi.waitFor(() => expect(latest.some((o) => o.code !== '')).toBe(true));
  return latest;
};

describe('useChrCodeLists', () => {
  beforeEach(() => clearCodeListCache());

  it('affixes the code the way each screen shows it', async () => {
    expect((await collect(useInformationSourceCodes)).map((o) => o.label)).toEqual([
      'AIA - Archaeological Impact Assessment',
      'SP - Site Plan',
    ]);
    expect((await collect(useReserveTypeCodes)).map((o) => o.label)).toContain(
      'Patch Riparian (PR)',
    );
  });

  it('keeps the screen order rather than the order the table returns', async () => {
    // Patch before Dispersed before the rest — grouped, which alphabetising would scramble.
    const codes = (await collect(useReserveTypeCodes)).map((o) => o.code);
    expect(codes.slice(0, 4)).toEqual(['', 'PR', 'DR', 'CC']);
    expect((await collect(useRatingCodes)).map((o) => o.code)).toEqual(['V', 'W']);
  });

  it('shows a code the UI has never heard of rather than dropping it', async () => {
    const codes = (await collect(useReserveTypeCodes)).map((o) => o.code);
    expect(codes[codes.length - 1]).toBe('ZZZ');
  });

  it('keeps the None entry on the two selects that carry their own', async () => {
    expect((await collect(useReserveTypeCodes))[0]).toEqual({ code: '', label: 'None' });
    expect((await collect(useContactRoleCodes))[0]).toEqual({ code: '', label: 'None' });
  });
});

import { describe, expect, it } from 'vitest';

import {
  classifyStaleness,
  formatUpdatedBy,
  isStale,
  stalenessBanner,
} from '@/services/offline/chrStaleness';

import type { StalenessVerdict } from '@/services/offline/chrStaleness';

describe('classifyStaleness', () => {
  it('is CURRENT when the server checkout still matches the local guid', () => {
    expect(classifyStaleness('guid-1', { status: 'RDO', deviceCheckoutGuid: 'guid-1' })).toBe(
      'CURRENT',
    );
  });

  it('is SUBMITTED_ELSEWHERE when the server status is SUB', () => {
    // Wins even if the guid differs — submission is the stronger signal.
    expect(classifyStaleness('guid-1', { status: 'SUB', deviceCheckoutGuid: undefined })).toBe(
      'SUBMITTED_ELSEWHERE',
    );
  });

  it('is RECLAIMED when the server checkout was reset (guid nulled by reactivate)', () => {
    expect(classifyStaleness('guid-1', { status: 'ACT', deviceCheckoutGuid: undefined })).toBe(
      'RECLAIMED',
    );
  });

  it('is RECLAIMED when re-checked-out elsewhere (different guid)', () => {
    expect(classifyStaleness('guid-1', { status: 'RDO', deviceCheckoutGuid: 'guid-2' })).toBe(
      'RECLAIMED',
    );
  });
});

describe('isStale', () => {
  it('flags superseded/removed verdicts, not current/unverified', () => {
    const superseded: StalenessVerdict[] = ['RECLAIMED', 'SUBMITTED_ELSEWHERE', 'GONE'];
    expect(superseded.every(isStale)).toBe(true);
    expect(isStale('CURRENT')).toBe(false);
    expect(isStale('UNVERIFIED')).toBe(false);
  });
});

describe('formatUpdatedBy', () => {
  it('strips the IDIR\\ prefix and formats the date to the app standard', () => {
    expect(
      formatUpdatedBy({ updateUserid: 'IDIR\\jsmith', updateTimestamp: '2012-10-01 14:30:00' }),
    ).toBe('Last updated by jsmith on Oct 1, 2012.');
  });

  it('handles partial / missing audit gracefully', () => {
    expect(formatUpdatedBy({ updateUserid: 'IDIR\\jsmith' })).toBe('Last updated by jsmith.');
    expect(formatUpdatedBy({ updateTimestamp: '2012-10-01 14:30:00' })).toBe(
      'Last updated on Oct 1, 2012.',
    );
    expect(formatUpdatedBy(undefined)).toBe('');
  });
});

describe('stalenessBanner', () => {
  it('returns null for CURRENT (no banner)', () => {
    expect(stalenessBanner('CURRENT')).toBeNull();
  });

  it('warns and names the last updater for SUBMITTED_ELSEWHERE', () => {
    const banner = stalenessBanner('SUBMITTED_ELSEWHERE', {
      updateUserid: 'IDIR\\jsmith',
      updateTimestamp: '2012-10-01 14:30:00',
    });
    expect(banner?.kind).toBe('warning');
    expect(banner?.subtitle).toContain('submitted on the server');
    expect(banner?.subtitle).toContain('Last updated by jsmith on Oct 1, 2012.');
  });

  it('uses an info kind for UNVERIFIED', () => {
    expect(stalenessBanner('UNVERIFIED')?.kind).toBe('info');
  });
});

import { describe, expect, it } from 'vitest';

import type { AdministrationData } from '@/types/protocolChecklist';

import {
  teamCountOf,
  teamMemberAddBlocked,
  validateAdministration,
} from '@/pages/ProtocolChecklist/administrationValidation';

const base = (overrides: Partial<AdministrationData> = {}): AdministrationData => ({
  evaluationDate: '2024-01-15',
  blockAccessTime: '2.5',
  hoursOnBlock: '8',
  peopleOnBlock: '2',
  teamLeadNameId: 'LEAD',
  teamMembers: [{ evaluatorUserid: 'M1', teamLeadInd: 'N' }],
  ...overrides,
});

const future = new Date(Date.now() + 400 * 86_400_000).toISOString().slice(0, 10);

describe('validateAdministration', () => {
  it('returns no errors for valid data', () => {
    expect(validateAdministration(base())).toEqual({});
  });

  it('allows blank optional fields', () => {
    expect(
      validateAdministration(
        base({
          evaluationDate: '',
          blockAccessTime: '',
          hoursOnBlock: '',
          peopleOnBlock: '',
          teamLeadNameId: '',
          teamMembers: [],
        }),
      ),
    ).toEqual({});
  });

  it('rejects a future evaluation date', () => {
    expect(validateAdministration(base({ evaluationDate: future })).evaluationDate).toMatch(
      /future/,
    );
  });

  it('rejects non-numeric hours', () => {
    expect(validateAdministration(base({ blockAccessTime: 'abc' })).blockAccessTime).toMatch(
      /number/,
    );
  });

  it('rejects hours out of range', () => {
    expect(validateAdministration(base({ hoursOnBlock: '10000' })).hoursOnBlock).toMatch(
      /between 0 and 9999.99/,
    );
  });

  it('rejects more than two decimal places', () => {
    expect(validateAdministration(base({ blockAccessTime: '2.555' })).blockAccessTime).toMatch(
      /2 decimal/,
    );
  });

  it('rejects non-integer people on block', () => {
    expect(validateAdministration(base({ peopleOnBlock: '2.5' })).peopleOnBlock).toMatch(
      /whole number/,
    );
  });

  it('rejects people on block above 10', () => {
    expect(validateAdministration(base({ peopleOnBlock: '11' })).peopleOnBlock).toMatch(
      /between 0 and 10/,
    );
  });

  it('rejects people on block below the team size', () => {
    // team = lead + 2 members = 3, but people on block = 2
    const data = base({
      peopleOnBlock: '2',
      teamMembers: [
        { evaluatorUserid: 'M1', teamLeadInd: 'N' },
        { evaluatorUserid: 'M2', teamLeadInd: 'N' },
      ],
    });
    expect(validateAdministration(data).peopleOnBlock).toMatch(/greater than or equal/);
  });
});

describe('teamCountOf', () => {
  it('counts the lead plus non-lead members', () => {
    expect(teamCountOf(base())).toBe(2);
    expect(teamCountOf(base({ teamLeadNameId: '' }))).toBe(1);
    expect(teamCountOf(base({ teamLeadNameId: '', teamMembers: [] }))).toBe(0);
  });
});

describe('teamMemberAddBlocked', () => {
  it('blocks when the team already fills people on block', () => {
    // team = 2 (lead + 1 member), people on block = 2 → no room
    expect(teamMemberAddBlocked(base({ peopleOnBlock: '2' }))).toBe(true);
  });

  it('allows when people on block leaves room', () => {
    expect(teamMemberAddBlocked(base({ peopleOnBlock: '5' }))).toBe(false);
  });

  it('blocks when people on block is blank', () => {
    expect(teamMemberAddBlocked(base({ peopleOnBlock: '' }))).toBe(true);
  });
});

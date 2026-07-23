import { describe, expect, it } from 'vitest';

import { randomListCsvFilename } from './randomListCsvFilename';

import type { OrgUnit } from '@/types/configuration';

const ORG_UNITS: OrgUnit[] = [
  {
    orgUnitNo: '5',
    orgUnitCode: 'DCC',
    orgUnitName: 'Cariboo-Chilcotin Natural Resource District',
  },
  { orgUnitNo: '7', orgUnitCode: 'DND', orgUnitName: 'Nadina Natural Resource District' },
];

describe('randomListCsvFilename', () => {
  it('includes the org-unit code and the master-list year range', () => {
    expect(randomListCsvFilename('2026', ORG_UNITS, '5')).toBe(
      '(DCC)_FREP_Random_List_(2026_2027).csv',
    );
  });

  it('drops the district prefix when "All districts" is selected', () => {
    expect(randomListCsvFilename('2026', ORG_UNITS, '')).toBe('FREP_Random_List_(2026_2027).csv');
  });

  it('falls back to the raw year when it is not numeric', () => {
    expect(randomListCsvFilename('2026-27', ORG_UNITS, '5')).toBe(
      '(DCC)_FREP_Random_List_(2026-27).csv',
    );
  });
});

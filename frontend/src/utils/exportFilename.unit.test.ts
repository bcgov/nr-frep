import { describe, expect, it } from 'vitest';

import { buildExportFilename } from './exportFilename';

describe('buildExportFilename', () => {
  it('includes the org-unit code and the master-list year range', () => {
    expect(
      buildExportFilename({ base: 'FREP_Random_List', orgUnitCode: 'DCC', effectiveYear: '2026' }),
    ).toBe('(DCC)_FREP_Random_List_(2026_2027).csv');
  });

  it('drops the prefix when the org unit is absent or the "all" sentinel', () => {
    expect(buildExportFilename({ base: 'FREP_Checklist_Search', effectiveYear: '2026' })).toBe(
      'FREP_Checklist_Search_(2026_2027).csv',
    );
    expect(
      buildExportFilename({
        base: 'FREP_Checklist_Search',
        orgUnitCode: '*',
        effectiveYear: '2026',
      }),
    ).toBe('FREP_Checklist_Search_(2026_2027).csv');
  });

  it('drops the year range when the year is absent or the "all" sentinel', () => {
    expect(buildExportFilename({ base: 'FREP_Random_List', orgUnitCode: 'DCC' })).toBe(
      '(DCC)_FREP_Random_List.csv',
    );
    expect(
      buildExportFilename({ base: 'FREP_Random_List', orgUnitCode: 'DCC', effectiveYear: '*' }),
    ).toBe('(DCC)_FREP_Random_List.csv');
  });

  it('uses a non-numeric year verbatim', () => {
    expect(
      buildExportFilename({ base: 'FREP_X', orgUnitCode: 'DCC', effectiveYear: '2026-27' }),
    ).toBe('(DCC)_FREP_X_(2026-27).csv');
  });

  it('spells out the "all" sentinel when marker labels are provided (Reports)', () => {
    expect(
      buildExportFilename({
        base: 'FREP_chr_data_extract',
        orgUnitCode: '*',
        effectiveYear: '*',
        allDistrictsLabel: 'All_Districts',
        allYearsLabel: 'All_Years',
      }),
    ).toBe('(All_Districts)_FREP_chr_data_extract_(All_Years).csv');
  });

  it('uses the concrete district/year even when marker labels are provided', () => {
    expect(
      buildExportFilename({
        base: 'FREP_chr_data_extract',
        orgUnitCode: 'DCC',
        effectiveYear: '2026',
        allDistrictsLabel: 'All_Districts',
        allYearsLabel: 'All_Years',
      }),
    ).toBe('(DCC)_FREP_chr_data_extract_(2026_2027).csv');
  });

  it('still omits an unselected (empty) value even when marker labels are provided', () => {
    // "Select…" ('' / undefined) is not the same as "— All —" ('*') — only the sentinel gets a marker.
    expect(
      buildExportFilename({
        base: 'FREP_chr_data_extract',
        effectiveYear: '2026',
        allDistrictsLabel: 'All_Districts',
        allYearsLabel: 'All_Years',
      }),
    ).toBe('FREP_chr_data_extract_(2026_2027).csv');
  });

  it('appends extra selected filter values as (value) segments, skipping blanks and *', () => {
    expect(
      buildExportFilename({
        base: 'FREP_checklist_rejection_reason',
        orgUnitCode: 'DCC',
        extension: 'pdf',
        parts: ['2024-01-01_to_2024-06-30', '', null, '*', 'A20015'],
      }),
    ).toBe('(DCC)_FREP_checklist_rejection_reason_(2024-01-01_to_2024-06-30)_(A20015).pdf');
  });

  it('omits the parts suffix entirely when none are provided', () => {
    expect(
      buildExportFilename({
        base: 'FREP_checklist_completion_status',
        extension: 'pdf',
        parts: [],
      }),
    ).toBe('FREP_checklist_completion_status.pdf');
  });

  it('honours a custom extension', () => {
    expect(
      buildExportFilename({
        base: 'FREP_X',
        orgUnitCode: 'DCC',
        effectiveYear: '2026',
        extension: 'pdf',
      }),
    ).toBe('(DCC)_FREP_X_(2026_2027).pdf');
  });
});

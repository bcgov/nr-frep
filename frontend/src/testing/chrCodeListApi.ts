import { vi } from 'vitest';

/**
 * The configuration code-list calls the CHR checklist components make on mount.
 *
 * Every screen that renders a feature, a composite, the contacts table or the block summary now
 * fetches its dropdown options, so a spec that constructs any of them needs all of these stubbed —
 * an unmocked one is a `TypeError` at render, not a missing option. Kept in one place so adding a
 * list later means editing this file rather than every spec that happens to mount a form.
 *
 * The rows are the real active contents of each table, taken from the code tables themselves:
 * bare descriptions in Title Case, with the expired duplicates left out and no code affixes — the
 * UI adds those. A spec asserting on a label is then asserting what an evaluator would see.
 */
export const chrCodeListApi = () => ({
  getChrFeatureClassCodes: vi.fn(() =>
    Promise.resolve([
      { code: 'AOP', description: 'Area of Potential' },
      { code: 'ARCH', description: 'Archaeological Resource' },
      { code: 'CMT', description: 'Culturally Modified Tree(s)' },
      { code: 'CT', description: 'Cultural Trail' },
      { code: 'EF', description: 'Ecological Features' },
      { code: 'HPZ', description: 'High Potential Zone' },
      { code: 'OTH', description: 'Other Feature Class' },
      { code: 'PLNT', description: 'Cultural Plants' },
      { code: 'TUA', description: 'Traditional Use Area' },
    ]),
  ),
  getChrFeatureInfoSourceCodes: vi.fn(() =>
    Promise.resolve([
      { code: 'AIA', description: 'Archaeological Impact Assessment' },
      { code: 'AOA', description: 'Archaeological Overview Assessment' },
      { code: 'CHRS', description: 'Pre-harvest CHR survey or assessment report' },
      { code: 'CMTS', description: 'CMT Survey' },
      { code: 'ISR', description: 'Information Sharing Reports' },
      { code: 'OTH', description: 'Other Information Source' },
      { code: 'PCOM', description: 'Personal Communication' },
      { code: 'PFR', description: 'Preliminary Field Reconnaissance' },
      { code: 'SP', description: 'Site Plan' },
      { code: 'TUS', description: 'Traditional Use Study' },
    ]),
  ),
  getChrReserveTypeCodes: vi.fn(() =>
    Promise.resolve([
      { code: 'AOP', description: 'Area Of Potential' },
      { code: 'CC', description: 'Clear Cut' },
      { code: 'CCSRZ', description: 'Cultural Cedar Stand Reserve Zone' },
      { code: 'DO', description: 'Dispersed Other' },
      { code: 'DR', description: 'Dispersed Riparian' },
      { code: 'DT', description: 'Dispersed Temporary' },
      { code: 'DW', description: 'Dispersed Wildlife' },
      { code: 'HPZ', description: 'High Potential Zone' },
      { code: 'LUOR', description: 'Land Use Objectives Regulation' },
      { code: 'OGMA', description: 'Old Growth Management Area' },
      { code: 'PO', description: 'Patch Other' },
      { code: 'PR', description: 'Patch Riparian' },
      { code: 'PT', description: 'Patch Temporary' },
      { code: 'PU', description: 'Patch Unidentified' },
      { code: 'PW', description: 'Patch Wildlife' },
    ]),
  ),
  getChrSiteEvaluationCodes: vi.fn(() =>
    Promise.resolve([
      { code: 'E', description: 'Very Well' },
      { code: 'M', description: 'Moderately' },
      { code: 'P', description: 'Poorly' },
      { code: 'U', description: "Don't Know" },
      { code: 'V', description: 'Very Poorly' },
      { code: 'W', description: 'Well' },
    ]),
  ),
  getChrParticipantRoleCodes: vi.fn(() =>
    Promise.resolve([
      { code: 'FN', description: 'First Nations' },
      { code: 'PROPONENT', description: 'Proponent (Licensee)' },
    ]),
  ),
  // Ordered as the proc returns them — ORDER BY description DESC, which with NA excluded lands
  // Yes / No / Don't Know. Q3 renders them in that order rather than re-sorting.
  getChecklistAnswers: vi.fn(() =>
    Promise.resolve([
      { code: 'Y', description: 'Yes' },
      { code: 'N', description: 'No' },
      { code: 'D', description: "Don't Know" },
    ]),
  ),
});

export default chrCodeListApi;

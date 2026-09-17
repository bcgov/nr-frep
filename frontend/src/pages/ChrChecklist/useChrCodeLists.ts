import type { CodeOption } from '@/pages/ChrChecklist/codeLists';
import type { CodeOption as FetchedCode } from '@/types/configuration';

import useCodeList, { primeCodeList } from '@/pages/ChrChecklist/useCodeList';
import API from '@/services/APIs';

/**
 * The CHR dropdowns, driven by the code tables rather than by lists in the source.
 *
 * Two hardcoded values had already reached production as ORA-02291 — a code that does not exist in
 * its parent table cannot fail at compile time and will not fail a unit test, because only Oracle
 * ever checks it. Fetching removes the class of bug rather than the two instances.
 *
 * Each list keeps two things the table cannot supply:
 *
 * **Label shape.** The tables hold a bare description; the screens show it affixed with its code —
 * "AIA - Archaeological Impact Assessment", "Patch Riparian (PR)". That is generated here from the
 * fetched row, so a code added to a table is labelled correctly without anyone editing this file.
 *
 * **Order.** The tables have no display-order column, and the order on screen is deliberate:
 * reserve types are grouped Patch, then Dispersed, then the rest, which alphabetising would
 * scramble. The known codes below fix that order; anything the table gains later sorts after them,
 * visible rather than silently dropped.
 */

/** Display order, and nothing else — the wording comes from the table. */
const FEATURE_CLASS_ORDER = ['ARCH', 'CMT', 'PLNT', 'CT', 'EF', 'TUA', 'AOP', 'HPZ', 'OTH'];

const INFORMATION_SOURCE_ORDER = [
  'AIA',
  'AOA',
  'CMTS',
  'ISR',
  'PCOM',
  'PFR',
  'CHRS',
  'SP',
  'TUS',
  'OTH',
];

const RESERVE_TYPE_ORDER = [
  'PR',
  'PW',
  'PO',
  'PT',
  'PU',
  'DR',
  'DW',
  'DO',
  'DT',
  'HPZ',
  'AOP',
  'OGMA',
  'LUOR',
  'CCSRZ',
  'CC',
];

const RATING_ORDER = ['V', 'P', 'M', 'W', 'E', 'U'];

const CONTACT_ROLE_ORDER = ['FN', 'PROPONENT'];

/** The "None" entry on the selects that carry one instead of CodeSelect's own blank. */
const NONE: CodeOption = { code: '', label: 'None' };

const ordered = (
  rows: FetchedCode[],
  order: readonly string[],
  label: (row: FetchedCode) => string,
): CodeOption[] =>
  [...rows]
    .sort((a, b) => {
      // An unknown code sorts after every known one, then alphabetically among its peers, so a code
      // added to the table appears at the end rather than vanishing.
      const ai = order.indexOf(a.code);
      const bi = order.indexOf(b.code);
      if (ai === -1 && bi === -1) return a.description.localeCompare(b.description);
      if (ai === -1) return 1;
      if (bi === -1) return -1;
      return ai - bi;
    })
    .map((row) => ({ code: row.code, label: label(row) }));

/** Feature class — "Culturally Modified Tree(s)". Plain description. */
export const useFeatureClassCodes = (): CodeOption[] =>
  ordered(
    useCodeList('chr-feature-class', () => API.configuration.getChrFeatureClassCodes()),
    FEATURE_CLASS_ORDER,
    (row) => row.description,
  );

/** Information source — "AIA - Archaeological Impact Assessment". Code, then description. */
export const useInformationSourceCodes = (): CodeOption[] =>
  ordered(
    useCodeList('chr-info-source', () => API.configuration.getChrFeatureInfoSourceCodes()),
    INFORMATION_SOURCE_ORDER,
    (row) => `${row.code} - ${row.description}`,
  );

/** Reserve type — "Patch Riparian (PR)". Description, then code. Carries its own "None". */
export const useReserveTypeCodes = (): CodeOption[] => [
  NONE,
  ...ordered(
    useCodeList('chr-reserve-type', () => API.configuration.getChrReserveTypeCodes()),
    RESERVE_TYPE_ORDER,
    (row) => `${row.description} (${row.code})`,
  ),
];

/** Feature and block rating — CHR_SITE_EVALUATION_CODE, not the SLR table of the same idea. */
export const useRatingCodes = (): CodeOption[] =>
  ordered(
    useCodeList('chr-site-evaluation', () => API.configuration.getChrSiteEvaluationCodes()),
    RATING_ORDER,
    (row) => row.description,
  );

/** Contact role. Carries its own "None". */
export const useContactRoleCodes = (): CodeOption[] => [
  NONE,
  ...ordered(
    useCodeList('chr-participant-role', () => API.configuration.getChrParticipantRoleCodes()),
    CONTACT_ROLE_ORDER,
    (row) => row.description,
  ),
];

/** Look a code up in a fetched list, falling back to the raw code when it is not there. */
export const labelFor = (options: CodeOption[], code?: string): string =>
  options.find((o) => o.code === code)?.label ?? code ?? '';

/**
 * Warm every CHR code list into the on-disk cache.
 *
 * Called when a checklist is taken offline: from then on the device may have no connection for the
 * rest of the day, and these lists are the only thing a device-local checklist still needs the API
 * for. Without them the feature editor's dropdowns are empty and a feature cannot be given a class
 * or an information source in the field.
 *
 * Keys must match the hooks above — a mismatch would warm a cache nothing reads.
 */
export const prefetchChrCodeLists = async (): Promise<void> => {
  await Promise.all([
    primeCodeList('chr-feature-class', () => API.configuration.getChrFeatureClassCodes()),
    primeCodeList('chr-info-source', () => API.configuration.getChrFeatureInfoSourceCodes()),
    primeCodeList('chr-reserve-type', () => API.configuration.getChrReserveTypeCodes()),
    primeCodeList('chr-site-evaluation', () => API.configuration.getChrSiteEvaluationCodes()),
    primeCodeList('chr-participant-role', () => API.configuration.getChrParticipantRoleCodes()),
    // The feature editor's Q3 answers, fetched with the same key it uses.
    primeCodeList('checklist-answers:NA', () => API.configuration.getChecklistAnswers('NA')),
  ]);
};

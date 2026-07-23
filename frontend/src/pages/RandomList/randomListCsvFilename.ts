import type { OrgUnit } from '@/types/configuration';

import { buildExportFilename } from '@/utils/exportFilename';

/**
 * CSV filename for the District Random List export, e.g. `(DCC)_FREP_Random_List_(2026_2027).csv`.
 * The `(CODE)_` prefix is dropped when "All districts" is selected. Replaces the backend default
 * (`frep_100_random_checklist`).
 */
export const randomListCsvFilename = (
  effectiveYear: string,
  orgUnits: OrgUnit[],
  orgUnit: string,
): string => {
  const orgUnitCode = orgUnits.find((unit) => unit.orgUnitNo === orgUnit)?.orgUnitCode;
  return buildExportFilename({ base: 'FREP_Random_List', orgUnitCode, effectiveYear });
};

/**
 * Builds a descriptive export filename from the export's selected context, e.g.
 * `(DCC)_FREP_Random_List_(2026_2027).csv`.
 *
 * A missing part (empty / `undefined`) is omitted, so the name stays clean:
 *   - no org unit → `FREP_Random_List_(2026_2027).csv`
 *   - no year     → `(DCC)_FREP_Random_List.csv`
 *
 * The legacy "all" sentinel `*` is, by default, treated the same as missing (omitted). Where an
 * export wants an explicit marker for "all" (the Reports page, whose district/year selects offer an
 * explicit "— All —" option distinct from "not chosen"), pass {@link allDistrictsLabel} /
 * {@link allYearsLabel} and the `*` value renders as `(All_Districts)` / `(All_Years)`.
 *
 * `effectiveYear` is the start of the master-list year range (2026 → rendered `2026_2027`); a
 * non-numeric year is used verbatim. Replaces the generic backend defaults (e.g.
 * `frep_100_random_checklist`).
 *
 * {@link parts} appends any additional selected filter values (each as a `_(value)` segment, in
 * order) after the year range — used by the Reports page so a report's other filters (date range,
 * licence, client, status…) show up in the name. Blank / `undefined` / `*` parts are skipped.
 */
export const buildExportFilename = ({
  base,
  orgUnitCode,
  effectiveYear,
  extension = 'csv',
  allDistrictsLabel,
  allYearsLabel,
  parts,
}: {
  base: string;
  orgUnitCode?: string | null;
  effectiveYear?: string | null;
  extension?: string;
  allDistrictsLabel?: string;
  allYearsLabel?: string;
  parts?: (string | null | undefined)[];
}): string => {
  const prefix = (() => {
    if (orgUnitCode === '*') return allDistrictsLabel ? `(${allDistrictsLabel})_` : '';
    const code = orgUnitCode ? orgUnitCode.trim() : '';
    return code ? `(${code})_` : '';
  })();

  const range = (() => {
    if (effectiveYear === '*') return allYearsLabel ? `_(${allYearsLabel})` : '';
    const year = effectiveYear ? effectiveYear.trim() : '';
    if (!year) return '';
    const startYear = Number(year);
    return Number.isFinite(startYear) ? `_(${startYear}_${startYear + 1})` : `_(${year})`;
  })();

  const extra = (parts ?? [])
    .map((part) => (part == null ? '' : part.trim()))
    .filter((part) => part !== '' && part !== '*')
    .map((part) => `_(${part})`)
    .join('');

  return `${prefix}${base}${range}${extra}.${extension}`;
};

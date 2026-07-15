import type { ReportFormat } from '@/services/reports';

/**
 * Generatable-report registry — the front-end mirror of the backend
 * `ReportDefinition` enum, modelled on the nr-fspts `reportDefinitions.ts`. Each
 * entry drives a {@link ReportConfigForm} (which inputs to show) and POSTs to
 * `/api/v1/reports/{id}`.
 *
 * <p>To add a report: register it in the backend `ReportDefinition` + drop its
 * JRXML, then add an entry to {@link GENERATABLE_REPORTS} whose {@code id} matches
 * the backend route token.</p>
 */
export type ReportFieldKey =
  | 'dateRange'
  | 'orgUnit'
  | 'masterListYear'
  | 'resourceValueStatus'
  | 'checklistStatus'
  | 'clientNumber'
  | 'licence'
  | 'openingId';

export interface GeneratableReport {
  /** Route token — must match the backend `ReportDefinition` id. */
  id: string;
  title: string;
  summary: string;
  availableFormats: ReportFormat[];
  /** Which inputs the form shows; 'required' enforces a value before generating. */
  fields: Partial<Record<ReportFieldKey, boolean | 'optional' | 'required'>>;
  /** Optional grid layout: each inner array is one row of field keys. */
  layout?: ReportFieldKey[][];
}

// Reports → Biodiversity → Data Extract (legacy JCRS FREPRPT001-005). All CSV extracts sharing the
// same filters: org unit (required), master-list year (required), resource-value status (required),
// opening id (optional). Ids match the backend ReportDefinition route tokens.
const BIODIVERSITY_EXTRACT_FIELDS: GeneratableReport['fields'] = {
  orgUnit: 'required',
  masterListYear: 'required',
  resourceValueStatus: 'required',
  openingId: 'optional',
};
const BIODIVERSITY_EXTRACT_LAYOUT: ReportFieldKey[][] = [
  ['orgUnit', 'masterListYear', 'resourceValueStatus', 'openingId'],
];

export const GENERATABLE_REPORTS: GeneratableReport[] = [
  {
    id: 'biodiversity-extract-block',
    title: 'Biodiversity — Block Table extract',
    summary: 'Opening/block-level biodiversity checklist data.',
    availableFormats: ['csv'],
    fields: BIODIVERSITY_EXTRACT_FIELDS,
    layout: BIODIVERSITY_EXTRACT_LAYOUT,
  },
  {
    id: 'biodiversity-extract-stratum',
    title: 'Biodiversity — Stratum Table extract',
    summary: 'Stratum-summary biodiversity data.',
    availableFormats: ['csv'],
    fields: BIODIVERSITY_EXTRACT_FIELDS,
    layout: BIODIVERSITY_EXTRACT_LAYOUT,
  },
  {
    id: 'biodiversity-extract-plot',
    title: 'Biodiversity — Plot Table extract',
    summary: 'Plot-level biodiversity data.',
    availableFormats: ['csv'],
    fields: BIODIVERSITY_EXTRACT_FIELDS,
    layout: BIODIVERSITY_EXTRACT_LAYOUT,
  },
  {
    id: 'biodiversity-extract-stand',
    title: 'Biodiversity — Stand Table extract',
    summary: 'Stand-table (tree) biodiversity data.',
    availableFormats: ['csv'],
    fields: BIODIVERSITY_EXTRACT_FIELDS,
    layout: BIODIVERSITY_EXTRACT_LAYOUT,
  },
  {
    id: 'biodiversity-extract-cwd',
    title: 'Biodiversity — Coarse Woody Debris extract',
    summary: 'CWD-table biodiversity data.',
    availableFormats: ['csv'],
    fields: BIODIVERSITY_EXTRACT_FIELDS,
    layout: BIODIVERSITY_EXTRACT_LAYOUT,
  },
  // Reports → Checklist Completion Status (legacy JCRS FREPRPT012). Jasper PDF rolled up by
  // region / org unit; the date range supplies the proc's start/end year, plus optional
  // licence + client number. No org-unit filter — the proc aggregates all regions itself.
  {
    id: 'checklist-completion-status',
    title: 'Checklist Completion Status',
    summary:
      'Resource-value checklist completion statistics by region and district, for a year range. ' +
      'Counts in parentheses are unsubmitted checklists.',
    availableFormats: ['pdf'],
    fields: {
      dateRange: 'optional',
      licence: 'optional',
      clientNumber: 'optional',
    },
    // One row → the 3-column field grid flows the fields 3 per row
    // (Date from, Date to, Licence | Client number).
    layout: [['dateRange', 'licence', 'clientNumber']],
  },
  // Reports → Checklist Rejection Reason (legacy JCRS FREPRPT018). Jasper PDF: accepted/rejected
  // checklist counts by region / district with a per-district rejection-reason breakdown. Filtered
  // by org unit + year range (the date range supplies the proc's start/end year).
  {
    id: 'checklist-rejection-reason',
    title: 'Checklist Rejection Reason',
    summary:
      'Accepted vs. rejected checklist counts by region and district, with the rejection reasons ' +
      'per district, for an org unit and year range.',
    availableFormats: ['pdf'],
    fields: {
      orgUnit: 'optional',
      dateRange: 'optional',
    },
    // One row → Organization unit, Date from, Date to align across the 3 columns.
    layout: [['orgUnit', 'dateRange']],
  },
  // Reports → Cultural Heritage → Data Extract (legacy JCRS FREPRPT022). CSV data extract (97 flat
  // columns) filtered by org unit, master-list year, checklist status and resource value. Admin-only
  // in legacy — access gating is deferred (see plan), so this is visible to all report users for now.
  {
    id: 'chr-data-extract',
    title: 'Cultural Heritage — Data Extract',
    summary:
      'Cultural Heritage Resource checklist, site and feature detail. Filtered by ' +
      'org unit, master-list year, checklist status and resource value.',
    availableFormats: ['csv'],
    fields: {
      orgUnit: 'required',
      masterListYear: 'required',
      checklistStatus: 'optional',
      resourceValueStatus: 'optional',
    },
    layout: [['orgUnit', 'masterListYear', 'checklistStatus', 'resourceValueStatus']],
  },
];

import { Button, DatePicker, DatePickerInput, Select, SelectItem, TextInput } from '@carbon/react';
import { Fragment, useEffect, useMemo, useState, type FC, type ReactNode } from 'react';

import { requiredLabel } from '@/utils/requiredLabel';

import type { GeneratableReport, ReportFieldKey } from './reportDefinitions';
import type { CodeOption, MasterListYear, OrgUnit } from '@/types/configuration';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import {
  openBlobInNewTab,
  requestCsvReport,
  requestReport,
  triggerBrowserDownload,
  type ReportFormat,
  type ReportRequestPayload,
} from '@/services/reports';
import { apiErrorMessage } from '@/utils/apiError';
import { buildExportFilename } from '@/utils/exportFilename';

/**
 * Per-report parameter form, modelled on the nr-fspts `ReportConfigForm`. Renders
 * only the inputs the {@link GeneratableReport} declares, validates required
 * fields client-side, then POSTs to `/api/v1/reports/{id}`. A CSV downloads with a descriptive
 * filename; a PDF opens in a new tab for preview and also downloads a descriptively-named copy.
 *
 * <p>The biodiversity data-extract filters mirror the legacy JCRS input controls:
 * org unit / master-list year / resource-value status each carry an "— All —"
 * option whose value is the legacy `*` sentinel.</p>
 */

const ALL = '*';

type Props = {
  definition: GeneratableReport;
  orgUnits: OrgUnit[];
  masterListYears: MasterListYear[];
  resourceValueStatuses: CodeOption[];
  checklistStatuses: CodeOption[];
  loading: boolean;
};

type FormState = {
  startDate: string;
  endDate: string;
  orgUnitCode: string;
  masterListYear: string;
  resourceValueStatus: string;
  checklistStatus: string;
  clientNumber: string;
  licenceNumber: string;
  openingId: string;
};

const BLANK: FormState = {
  startDate: '',
  endDate: '',
  orgUnitCode: '',
  masterListYear: '',
  resourceValueStatus: '',
  checklistStatus: '',
  clientNumber: '',
  licenceNumber: '',
  openingId: '',
};

type FormErrors = Partial<Record<keyof FormState, string>>;

const isRequired = (value: boolean | 'optional' | 'required' | undefined) => value === 'required';

// Required-field rules driving validate(): the report-definition field key, the form-state field it
// maps to, and the error key. A data table keeps validate() flat (one loop) instead of a long if-chain.
const REQUIRED_FIELD_CHECKS: {
  cfg: ReportFieldKey;
  field: keyof FormState;
  error: keyof FormErrors;
}[] = [
  { cfg: 'orgUnit', field: 'orgUnitCode', error: 'orgUnitCode' },
  { cfg: 'masterListYear', field: 'masterListYear', error: 'masterListYear' },
  { cfg: 'resourceValueStatus', field: 'resourceValueStatus', error: 'resourceValueStatus' },
  { cfg: 'checklistStatus', field: 'checklistStatus', error: 'checklistStatus' },
  { cfg: 'openingId', field: 'openingId', error: 'openingId' },
  { cfg: 'clientNumber', field: 'clientNumber', error: 'clientNumber' },
  { cfg: 'licence', field: 'licenceNumber', error: 'licenceNumber' },
];

const ReportConfigForm: FC<Props> = ({
  definition,
  orgUnits,
  masterListYears,
  resourceValueStatuses,
  checklistStatuses,
  loading,
}) => {
  const { display } = useNotification();
  const { canEdit } = useAuthorization();
  const [form, setForm] = useState<FormState>(BLANK);
  const [errors, setErrors] = useState<FormErrors>({});
  const [generating, setGenerating] = useState(false);

  // Reset when switching reports.
  useEffect(() => {
    setForm(BLANK);
    setErrors({});
  }, [definition.id]);

  const set = (key: keyof FormState, value: string) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    // Clear the field's inline error as soon as the user edits it.
    setErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
  };

  const rows = useMemo<ReportFieldKey[][]>(() => {
    if (definition.layout) return definition.layout;
    return (Object.keys(definition.fields) as ReportFieldKey[]).map((key) => [key]);
  }, [definition]);

  // Returns a map of field → inline error message; empty when the form is valid.
  const validate = (): FormErrors => {
    const e: FormErrors = {};
    for (const { cfg, field, error } of REQUIRED_FIELD_CHECKS) {
      if (isRequired(definition.fields[cfg]) && !form[field].trim()) {
        e[error] = 'Required.';
      }
    }
    if (form.startDate && form.endDate && form.startDate > form.endDate) {
      e.endDate = 'Date to must be on or after Date from.';
    }
    return e;
  };

  const generate = async (format: ReportFormat) => {
    const validationErrors = validate();
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }
    setErrors({});
    setGenerating(true);
    try {
      const payload: ReportRequestPayload = {
        startDate: form.startDate || null,
        endDate: form.endDate || null,
        orgUnitCode: form.orgUnitCode || null,
        masterListYear: form.masterListYear || null,
        resourceValueStatus: form.resourceValueStatus || null,
        checklistStatus: form.checklistStatus || null,
        clientNumber: form.clientNumber || null,
        licenceNumber: form.licenceNumber || null,
        openingId: form.openingId || null,
        format,
      };
      // CSV data extracts go to the dedicated /csv endpoint (Commons CSV); PDF/template reports
      // use the Jasper endpoint.
      const response =
        format === 'csv'
          ? await requestCsvReport(definition.id, payload)
          : await requestReport(definition.id, payload);
      // Descriptive name from the report + its selected filters, not the generic backend default.
      // Org unit → prefix and master-list year → range; every other selected filter (date range,
      // status, client, licence, opening) is appended so the name reflects what produced the file.
      // "— All —" ('*') is spelled out so an all/all export is still distinguishable by filename.
      const dateRange = (() => {
        if (form.startDate && form.endDate) return `${form.startDate}_to_${form.endDate}`;
        if (form.startDate) return `from_${form.startDate}`;
        if (form.endDate) return `to_${form.endDate}`;
        return null;
      })();
      const filename = buildExportFilename({
        base: `FREP_${definition.id.replace(/-/g, '_')}`,
        orgUnitCode: form.orgUnitCode,
        effectiveYear: form.masterListYear,
        extension: format,
        allDistrictsLabel: 'All_Districts',
        allYearsLabel: 'All_Years',
        parts: [
          dateRange,
          form.resourceValueStatus,
          form.checklistStatus,
          form.clientNumber,
          form.licenceNumber,
          form.openingId,
        ],
      });
      // A PDF also opens in a new tab for a quick preview; a blob previewed in a tab can't carry a
      // filename, so the parallel download is what gives the user a descriptively-named file.
      if (format === 'pdf') {
        openBlobInNewTab(response.blob);
      }
      triggerBrowserDownload(response.blob, filename);
      display({ kind: 'success', title: `${definition.title} ready`, timeout: 4000 });
    } catch (err) {
      display({
        kind: 'error',
        title: 'Unable to generate the report',
        subtitle: apiErrorMessage(err),
        timeout: 9000,
      });
    } finally {
      setGenerating(false);
    }
  };

  const fieldNode = (key: ReportFieldKey) => {
    const cfg = definition.fields[key];
    if (!cfg) return null;
    const required = isRequired(cfg);
    const label = (base: string): ReactNode => requiredLabel(base, required);
    switch (key) {
      case 'dateRange': {
        const iso = (d?: Date) => (d ? d.toISOString().slice(0, 10) : '');
        // Two single pickers (one grid cell each) rather than one range picker, matching the
        // nr-fspts field-group layout and the single-mode DatePicker width fix in reports.scss.
        return (
          <Fragment key="dateRange">
            <DatePicker
              datePickerType="single"
              dateFormat="Y-m-d"
              value={form.startDate ? [form.startDate] : []}
              onChange={(dates: Date[]) => set('startDate', iso(dates[0]))}
            >
              <DatePickerInput
                id={`${definition.id}-start`}
                labelText="Date from"
                placeholder="YYYY-MM-DD"
              />
            </DatePicker>
            <DatePicker
              datePickerType="single"
              dateFormat="Y-m-d"
              value={form.endDate ? [form.endDate] : []}
              onChange={(dates: Date[]) => set('endDate', iso(dates[0]))}
            >
              <DatePickerInput
                id={`${definition.id}-end`}
                labelText="Date to"
                placeholder="YYYY-MM-DD"
                invalid={!!errors.endDate}
                invalidText={errors.endDate}
              />
            </DatePicker>
          </Fragment>
        );
      }
      case 'orgUnit':
        return (
          <Select
            key="orgUnit"
            id={`${definition.id}-orgUnit`}
            labelText={label('Organization unit')}
            disabled={loading}
            invalid={!!errors.orgUnitCode}
            invalidText={errors.orgUnitCode}
            value={form.orgUnitCode}
            onChange={(e) => set('orgUnitCode', e.target.value)}
          >
            <SelectItem value="" text="Select…" />
            <SelectItem value={ALL} text="— All —" />
            {orgUnits.map((o) => (
              <SelectItem
                key={o.orgUnitCode}
                value={o.orgUnitCode}
                text={`${o.orgUnitCode} - ${o.orgUnitName}`}
              />
            ))}
          </Select>
        );
      case 'masterListYear':
        return (
          <Select
            key="masterListYear"
            id={`${definition.id}-mly`}
            labelText={label('Master list year')}
            disabled={loading}
            invalid={!!errors.masterListYear}
            invalidText={errors.masterListYear}
            value={form.masterListYear}
            onChange={(e) => set('masterListYear', e.target.value)}
          >
            <SelectItem value="" text="Select…" />
            <SelectItem value={ALL} text="— All —" />
            {masterListYears.map((y) => (
              <SelectItem key={y.effectiveYear} value={y.effectiveYear} text={y.label} />
            ))}
          </Select>
        );
      case 'resourceValueStatus':
        return (
          <Select
            key="resourceValueStatus"
            id={`${definition.id}-rvs`}
            labelText={label('Resource value status')}
            disabled={loading}
            invalid={!!errors.resourceValueStatus}
            invalidText={errors.resourceValueStatus}
            value={form.resourceValueStatus}
            onChange={(e) => set('resourceValueStatus', e.target.value)}
          >
            <SelectItem value="" text="Select…" />
            <SelectItem value={ALL} text="— All —" />
            {resourceValueStatuses.map((s) => (
              <SelectItem key={s.code} value={s.code} text={s.description || s.code} />
            ))}
          </Select>
        );
      case 'checklistStatus':
        return (
          <Select
            key="checklistStatus"
            id={`${definition.id}-cls`}
            labelText={label('Checklist status')}
            disabled={loading}
            invalid={!!errors.checklistStatus}
            invalidText={errors.checklistStatus}
            value={form.checklistStatus}
            onChange={(e) => set('checklistStatus', e.target.value)}
          >
            <SelectItem value="" text="Select…" />
            <SelectItem value={ALL} text="— All —" />
            {checklistStatuses.map((s) => (
              <SelectItem key={s.code} value={s.code} text={s.description || s.code} />
            ))}
          </Select>
        );
      case 'clientNumber':
        return (
          <TextInput
            key="clientNumber"
            id={`${definition.id}-client`}
            labelText={label('Client number')}
            maxLength={8}
            invalid={!!errors.clientNumber}
            invalidText={errors.clientNumber}
            value={form.clientNumber}
            onChange={(e) => set('clientNumber', e.target.value)}
          />
        );
      case 'licence':
        return (
          <TextInput
            key="licence"
            id={`${definition.id}-licence`}
            labelText={label('Licence number')}
            maxLength={10}
            invalid={!!errors.licenceNumber}
            invalidText={errors.licenceNumber}
            value={form.licenceNumber}
            onChange={(e) => set('licenceNumber', e.target.value)}
          />
        );
      case 'openingId':
        return (
          <TextInput
            key="openingId"
            id={`${definition.id}-opening`}
            labelText={label('Opening ID')}
            maxLength={10}
            invalid={!!errors.openingId}
            invalidText={errors.openingId}
            value={form.openingId}
            onChange={(e) => set('openingId', e.target.value)}
          />
        );
      default:
        return null;
    }
  };

  const formats = definition.availableFormats;

  return (
    <div className="report-form">
      {rows.map((row) => (
        <div className="report-form__field-group" key={row.join('-')}>
          {row.map((key) => fieldNode(key))}
        </div>
      ))}
      <div className="report-form__actions">
        <Button kind="ghost" disabled={generating} onClick={() => setForm(BLANK)}>
          Reset
        </Button>
        {/* Report generation requires write access (FREP_ADMIN / FREP_EDITOR) — view-only users see
            no generate buttons (the backend rejects the POST with 403 regardless). */}
        {canEdit && formats.includes('csv') && (
          <Button kind="tertiary" disabled={generating} onClick={() => void generate('csv')}>
            Export CSV
          </Button>
        )}
        {canEdit && formats.includes('pdf') && (
          <Button disabled={generating} onClick={() => void generate('pdf')}>
            Generate PDF
          </Button>
        )}
        {!canEdit && (
          <span className="report-form__readonly-note">
            You need editor access to generate reports.
          </span>
        )}
      </div>
    </div>
  );
};

export default ReportConfigForm;

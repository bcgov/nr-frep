import { Button, DatePicker, DatePickerInput, Select, SelectItem, TextInput } from '@carbon/react';
import { useEffect, useMemo, useState, type FC } from 'react';

import type { GeneratableReport, ReportFieldKey } from './reportDefinitions';
import type { CodeOption, MasterListYear, OrgUnit } from '@/types/configuration';

import { useNotification } from '@/context/notification/useNotification';
import {
  openBlobInNewTab,
  requestCsvReport,
  requestReport,
  triggerBrowserDownload,
  type ReportFormat,
  type ReportRequestPayload,
} from '@/services/reports';

/**
 * Per-report parameter form, modelled on the nr-fspts `ReportConfigForm`. Renders
 * only the inputs the {@link GeneratableReport} declares, validates required
 * fields client-side, then POSTs to `/api/v1/reports/{id}` and opens (PDF) or
 * downloads (CSV) the result.
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

const isRequired = (value: boolean | 'optional' | 'required' | undefined) => value === 'required';

const ReportConfigForm: FC<Props> = ({
  definition,
  orgUnits,
  masterListYears,
  resourceValueStatuses,
  checklistStatuses,
  loading,
}) => {
  const { display } = useNotification();
  const [form, setForm] = useState<FormState>(BLANK);
  const [generating, setGenerating] = useState(false);

  // Reset when switching reports.
  useEffect(() => setForm(BLANK), [definition.id]);

  const set = (key: keyof FormState, value: string) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const rows = useMemo<ReportFieldKey[][]>(() => {
    if (definition.layout) return definition.layout;
    return (Object.keys(definition.fields) as ReportFieldKey[]).map((key) => [key]);
  }, [definition]);

  const validate = (): string | null => {
    if (isRequired(definition.fields.orgUnit) && !form.orgUnitCode.trim()) {
      return 'Organization unit is required.';
    }
    if (isRequired(definition.fields.masterListYear) && !form.masterListYear.trim()) {
      return 'Master list year is required.';
    }
    if (isRequired(definition.fields.resourceValueStatus) && !form.resourceValueStatus.trim()) {
      return 'Resource value status is required.';
    }
    if (isRequired(definition.fields.checklistStatus) && !form.checklistStatus.trim()) {
      return 'Checklist status is required.';
    }
    if (isRequired(definition.fields.openingId) && !form.openingId.trim()) {
      return 'Opening ID is required.';
    }
    if (isRequired(definition.fields.clientNumber) && !form.clientNumber.trim()) {
      return 'Client number is required.';
    }
    if (isRequired(definition.fields.licence) && !form.licenceNumber.trim()) {
      return 'Licence number is required.';
    }
    if (form.startDate && form.endDate && form.startDate > form.endDate) {
      return 'The start date must be on or before the end date.';
    }
    return null;
  };

  const generate = async (format: ReportFormat) => {
    const error = validate();
    if (error) {
      display({
        kind: 'warning',
        title: 'Check the report fields',
        subtitle: error,
        timeout: 6000,
      });
      return;
    }
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
      if (format === 'pdf') {
        openBlobInNewTab(response.blob);
      } else {
        triggerBrowserDownload(response.blob, response.filename);
      }
      display({ kind: 'success', title: `${definition.title} ready`, timeout: 4000 });
    } catch (err) {
      display({
        kind: 'error',
        title: 'Unable to generate the report',
        subtitle: err instanceof Error ? err.message : 'Unknown error',
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
    const label = (base: string) => (required ? `${base} (required)` : base);
    switch (key) {
      case 'dateRange':
        return (
          <DatePicker
            key="dateRange"
            datePickerType="range"
            dateFormat="Y-m-d"
            value={[form.startDate, form.endDate].filter(Boolean)}
            onChange={(dates: Date[]) => {
              const iso = (d?: Date) => (d ? d.toISOString().slice(0, 10) : '');
              set('startDate', iso(dates[0]));
              set('endDate', iso(dates[1]));
            }}
          >
            <DatePickerInput
              id={`${definition.id}-start`}
              labelText="Date from"
              placeholder="YYYY-MM-DD"
            />
            <DatePickerInput
              id={`${definition.id}-end`}
              labelText="Date to"
              placeholder="YYYY-MM-DD"
            />
          </DatePicker>
        );
      case 'orgUnit':
        return (
          <Select
            key="orgUnit"
            id={`${definition.id}-orgUnit`}
            labelText={label('Organization unit')}
            disabled={loading}
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
      {rows.map((row, i) => (
        <div className="report-form__row" key={i}>
          {row.map((key) => fieldNode(key))}
        </div>
      ))}
      <div className="report-form__actions">
        <Button kind="ghost" disabled={generating} onClick={() => setForm(BLANK)}>
          Reset
        </Button>
        {formats.includes('csv') && (
          <Button kind="tertiary" disabled={generating} onClick={() => void generate('csv')}>
            Export CSV
          </Button>
        )}
        {formats.includes('pdf') && (
          <Button disabled={generating} onClick={() => void generate('pdf')}>
            Generate PDF
          </Button>
        )}
      </div>
    </div>
  );
};

export default ReportConfigForm;

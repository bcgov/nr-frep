import {
  Button,
  Column,
  DatePicker,
  DatePickerInput,
  Grid,
  InlineNotification,
  NumberInput,
  Select,
  SelectItem,
  SkeletonText,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
  TextArea,
  Tile,
} from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import type { MasterListYear } from '@/types/configuration';
import type { GenerateMasterListRequest, MasterListAdmin } from '@/types/masterListAdmin';

import { useConfirm } from '@/context/confirm/useConfirm';
import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';
import { apiErrorMessage } from '@/utils/apiError';

import './masterListAdmin.scss';

const emptyForm = (effectiveYear: string): GenerateMasterListRequest => ({
  effectiveYear,
  minHarvestCompleteDate: '',
  maxHarvestCompleteDate: '',
  minOpeningGrossAreaHa: 5,
  maxSitesPerDistrict: 12,
  comments: '',
});

/** Carbon DatePicker hands back a local Date; serialize to the `YYYY-MM-DD` the proc expects. */
const toIsoDate = (d?: Date): string => (d ? d.toISOString().slice(0, 10) : '');

// Generate-List field rules, mirroring legacy Frep700ValidationManager (and the backend).
const HARVEST_DATE_MIN = '1997-06-15';
const HARVEST_DATE_MAX = '2050-12-31';
const MAX_GROSS_AREA_HA = 99999.9999;
const MAX_COMMENTS_LENGTH = 4000;

type FormErrors = Partial<
  Record<'minArea' | 'minDate' | 'maxDate' | 'maxSites' | 'comments', string>
>;

const decimalPlaces = (n: number): number => {
  const s = String(n);
  const dot = s.indexOf('.');
  return dot < 0 || s.includes('e') ? 0 : s.length - dot - 1;
};

/** Returns a map of field → message; empty when the form is valid. */
const validateGenerateForm = (f: GenerateMasterListRequest): FormErrors => {
  const errors: FormErrors = {};

  const area = f.minOpeningGrossAreaHa;
  if (area == null || Number.isNaN(area)) {
    errors.minArea = 'Min opening gross area is required.';
  } else if (area < 0 || area > MAX_GROSS_AREA_HA) {
    errors.minArea = 'Must be between 0 and 99999.9999.';
  } else if (decimalPlaces(area) > 4) {
    errors.minArea = 'At most 4 decimal places.';
  }

  const min = (f.minHarvestCompleteDate ?? '').trim();
  const max = (f.maxHarvestCompleteDate ?? '').trim();
  if (!min) {
    errors.minDate = 'Required.';
  } else if (min < HARVEST_DATE_MIN || min > HARVEST_DATE_MAX) {
    errors.minDate = 'Must be between 1997-06-15 and 2050-12-31.';
  }
  if (!max) {
    errors.maxDate = 'Required.';
  } else if (max < HARVEST_DATE_MIN || max > HARVEST_DATE_MAX) {
    errors.maxDate = 'Must be between 1997-06-15 and 2050-12-31.';
  }
  if (!errors.minDate && !errors.maxDate && min >= max) {
    errors.maxDate = 'Must be after the min harvest-complete date.';
  }

  const sites = f.maxSitesPerDistrict;
  if (sites == null || Number.isNaN(sites)) {
    errors.maxSites = 'Max sites per district is required.';
  } else if (!Number.isInteger(sites) || sites < 1 || sites > 500) {
    errors.maxSites = 'Must be a whole number between 1 and 500.';
  }

  if ((f.comments ?? '').length > MAX_COMMENTS_LENGTH) {
    errors.comments = 'Must be 4000 characters or fewer.';
  }

  return errors;
};

/** Legacy FREP700 lock state from resource_evaluation_ind: '' none, 'N' generated, 'Y' locked. */
const evalStateTag = (ind: string): { type: 'gray' | 'teal' | 'red'; label: string } => {
  if (ind === 'Y') return { type: 'red', label: 'Evaluations under way (locked)' };
  if (ind === 'N') return { type: 'teal', label: 'Generated' };
  return { type: 'gray', label: 'No list yet' };
};

const MasterListAdminPage: FC = () => {
  const { display } = useNotification();
  const confirm = useConfirm();

  const [years, setYears] = useState<MasterListYear[]>([]);
  const [effectiveYear, setEffectiveYear] = useState<string>('');
  const [criteria, setCriteria] = useState<MasterListAdmin | null>(null);
  const [form, setForm] = useState<GenerateMasterListRequest>(emptyForm(''));

  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [errors, setErrors] = useState<FormErrors>({});

  useEffect(() => {
    let cancelled = false;
    setLoading(true);

    API.configuration
      .getMasterListYears()
      .then((data) => {
        if (cancelled) return;
        setYears(data);
        const initialYear =
          data.find((y) => y.current)?.effectiveYear ?? data[0]?.effectiveYear ?? '';
        setEffectiveYear(initialYear);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        const message = apiErrorMessage(err);
        display({
          kind: 'error',
          title: "We couldn't load master list years",
          subtitle: message,
          timeout: 9000,
        });
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [display]);

  useEffect(() => {
    if (!effectiveYear) return;
    let cancelled = false;
    setLoading(true);

    API.masterListAdmin
      .getMasterList(effectiveYear)
      .then((data) => {
        if (cancelled) return;
        setCriteria(data);
        setErrors({});
        setForm({
          effectiveYear: data.effectiveYear,
          minHarvestCompleteDate: data.minHarvestCompleteDate,
          maxHarvestCompleteDate: data.maxHarvestCompleteDate,
          minOpeningGrossAreaHa: data.minOpeningGrossAreaHa,
          maxSitesPerDistrict: data.maxSitesPerDistrict,
          comments: data.generationComments,
        });
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        const message = apiErrorMessage(err);
        display({
          kind: 'error',
          title: "We couldn't load master list criteria",
          subtitle: message,
          timeout: 9000,
        });
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [display, effectiveYear]);

  const handleGenerate = async () => {
    const validationErrors = validateGenerateForm(form);
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) {
      display({
        kind: 'error',
        title: 'Please fix the highlighted fields',
        subtitle: 'Some eligibility criteria are missing or out of range.',
        timeout: 9000,
      });
      return;
    }
    setGenerating(true);
    try {
      const response = await API.masterListAdmin.generate(form);
      setCriteria(response);
      display({
        kind: 'success',
        title: 'Master list generated',
        subtitle: `Year ${response.effectiveYear} generated with ${response.generationStats.length} districts.`,
        timeout: 6000,
      });
    } catch (err) {
      display({
        kind: 'error',
        title: 'Generation failed',
        subtitle: apiErrorMessage(err),
        timeout: 9000,
      });
    } finally {
      setGenerating(false);
    }
  };

  const runMutation = async (action: () => Promise<MasterListAdmin>, successTitle: string) => {
    setGenerating(true);
    try {
      setCriteria(await action());
      display({ kind: 'success', title: successTitle, timeout: 5000 });
    } catch (err) {
      display({
        kind: 'error',
        title: 'Action failed',
        subtitle: apiErrorMessage(err),
        timeout: 9000,
      });
    } finally {
      setGenerating(false);
    }
  };

  const handleSaveComments = () =>
    runMutation(
      () => API.masterListAdmin.saveComments(effectiveYear, form.comments ?? ''),
      'Comments saved',
    );

  const handleDelete = async () => {
    if (
      !(await confirm({
        title: 'Delete master list?',
        message: `Delete the generated master list for ${effectiveYear}? This can't be undone.`,
      }))
    )
      return;
    void runMutation(
      () => API.masterListAdmin.deleteMasterList(effectiveYear),
      'Master list deleted',
    );
  };

  // Legacy FREP700 button-gating (Frep700ButtonManager), keyed on resource_evaluation_ind:
  //  Generate enabled only when no list ('');  Delete locked once evaluations exist ('Y');
  //  Save comments only meaningful once a list exists.
  const evalInd = criteria?.resourceEvaluationInd ?? '';
  const hasList = evalInd !== '';
  const locked = evalInd === 'Y';

  return (
    <Grid fullWidth className="default-grid master-list-admin-grid">
      <Column sm={4} md={8} lg={16}>
        <h1 className="master-list-admin__title">Generate Master List</h1>
        <p className="master-list-admin__subtitle">
          Sys-admin only. Review eligibility criteria for a master list year and (re-)generate the
          provincial random list of evaluation sites.
        </p>
      </Column>

      <Column sm={4} md={8} lg={16}>
        <Tile className="master-list-admin__panel">
          {!loading && criteria && locked && (
            <InlineNotification
              className="master-list-admin__lock-note"
              kind="info"
              title="Year locked"
              subtitle="Resource evaluations are under way for this year — the list is locked, so it can't be (re-)generated or deleted."
              hideCloseButton
              lowContrast
            />
          )}
          <h2>Eligibility criteria</h2>
          <div className="master-list-admin__year">
            <div className="master-list-admin__year-select">
              <Select
                id="master-list-admin-year"
                labelText="Master list year"
                value={effectiveYear}
                onChange={(e) => setEffectiveYear(e.target.value)}
                disabled={loading || years.length === 0}
              >
                {years.map((year) => (
                  <SelectItem
                    key={year.effectiveYear}
                    value={year.effectiveYear}
                    text={year.label}
                  />
                ))}
              </Select>
            </div>
            {!loading && criteria && (
              <div className="master-list-admin__generated">
                <span className="master-list-admin__label">Status</span>
                <Tag type={evalStateTag(evalInd).type} size="sm">
                  {evalStateTag(evalInd).label}
                </Tag>
              </div>
            )}
          </div>

          {loading && <SkeletonText paragraph lineCount={6} />}

          {!loading && criteria && (
            <>
              <div className="master-list-admin__form">
                <DatePicker
                  className="master-list-admin__date"
                  datePickerType="single"
                  dateFormat="Y-m-d"
                  value={form.minHarvestCompleteDate ? [form.minHarvestCompleteDate] : []}
                  onChange={(dates: Date[]) =>
                    setForm({ ...form, minHarvestCompleteDate: toIsoDate(dates[0]) })
                  }
                >
                  <DatePickerInput
                    id="mla-min-date"
                    labelText="Min harvest-complete date"
                    placeholder="YYYY-MM-DD"
                    disabled={generating || hasList}
                    invalid={!!errors.minDate}
                    invalidText={errors.minDate}
                  />
                </DatePicker>
                <DatePicker
                  className="master-list-admin__date"
                  datePickerType="single"
                  dateFormat="Y-m-d"
                  value={form.maxHarvestCompleteDate ? [form.maxHarvestCompleteDate] : []}
                  onChange={(dates: Date[]) =>
                    setForm({ ...form, maxHarvestCompleteDate: toIsoDate(dates[0]) })
                  }
                >
                  <DatePickerInput
                    id="mla-max-date"
                    labelText="Max harvest-complete date"
                    placeholder="YYYY-MM-DD"
                    disabled={generating || hasList}
                    invalid={!!errors.maxDate}
                    invalidText={errors.maxDate}
                  />
                </DatePicker>
                <NumberInput
                  id="mla-min-area"
                  label="Min opening gross area (ha)"
                  value={form.minOpeningGrossAreaHa ?? 0}
                  onChange={(_e, { value }) =>
                    setForm({
                      ...form,
                      minOpeningGrossAreaHa: typeof value === 'number' ? value : Number(value),
                    })
                  }
                  step={0.5}
                  disabled={generating || hasList}
                  invalid={!!errors.minArea}
                  invalidText={errors.minArea}
                />
                <NumberInput
                  id="mla-max-sites"
                  label="Max sites per district"
                  value={form.maxSitesPerDistrict ?? 0}
                  onChange={(_e, { value }) =>
                    setForm({
                      ...form,
                      maxSitesPerDistrict: typeof value === 'number' ? value : Number(value),
                    })
                  }
                  step={1}
                  disabled={generating || hasList}
                  invalid={!!errors.maxSites}
                  invalidText={errors.maxSites}
                />
                <TextArea
                  id="mla-comments"
                  labelText="Generation comments"
                  rows={3}
                  value={form.comments ?? ''}
                  onChange={(e) => setForm({ ...form, comments: e.target.value })}
                  maxLength={MAX_COMMENTS_LENGTH}
                  invalid={!!errors.comments}
                  invalidText={errors.comments}
                />
              </div>
              <div className="master-list-admin__actions">
                <Button onClick={() => void handleGenerate()} disabled={generating || hasList}>
                  Generate master list
                </Button>
                <Button
                  kind="tertiary"
                  onClick={() => void handleSaveComments()}
                  disabled={generating || !hasList}
                >
                  Save comments
                </Button>
                {hasList && (
                  <Button
                    kind="danger--tertiary"
                    onClick={() => void handleDelete()}
                    disabled={generating || locked}
                  >
                    Delete list
                  </Button>
                )}
              </div>
            </>
          )}
        </Tile>
      </Column>

      {!loading && criteria && (
        <>
          <Column sm={4} md={8} lg={16}>
            {criteria.generationStats.length === 0 ? (
              <p>No generation stats yet — generate the list to see per-district counts.</p>
            ) : (
              <TableContainer>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableHeader>District</TableHeader>
                      <TableHeader>Name</TableHeader>
                      <TableHeader>Eligible</TableHeader>
                      <TableHeader>Selected</TableHeader>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {criteria.generationStats.map((stat) => (
                      <TableRow key={stat.orgUnitNo || stat.orgUnitCode}>
                        <TableCell>{stat.orgUnitCode}</TableCell>
                        <TableCell>{stat.orgUnitName}</TableCell>
                        <TableCell>{stat.eligibleSites}</TableCell>
                        <TableCell>{stat.selectedSites}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Column>
        </>
      )}
    </Grid>
  );
};

export default MasterListAdminPage;

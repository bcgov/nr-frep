import {
  Button,
  Column,
  Grid,
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
  TextInput,
  Tile,
} from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import TableHeaderBar from '@/components/core/TableHeaderBar';

import type { MasterListYear } from '@/types/configuration';
import type { GenerateMasterListRequest, MasterListAdmin } from '@/types/masterListAdmin';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

import './masterListAdmin.scss';

const emptyForm = (effectiveYear: string): GenerateMasterListRequest => ({
  effectiveYear,
  minHarvestCompleteDate: '',
  maxHarvestCompleteDate: '',
  minOpeningGrossAreaHa: 5,
  maxSitesPerDistrict: 12,
  comments: '',
});

/** Legacy FREP700 lock state from resource_evaluation_ind: '' none, 'N' generated, 'Y' locked. */
const evalStateTag = (ind: string): { type: 'gray' | 'teal' | 'red'; label: string } => {
  if (ind === 'Y') return { type: 'red', label: 'Evaluations under way (locked)' };
  if (ind === 'N') return { type: 'teal', label: 'Generated' };
  return { type: 'gray', label: 'No list yet' };
};

const MasterListAdminPage: FC = () => {
  const { display } = useNotification();

  const [years, setYears] = useState<MasterListYear[]>([]);
  const [effectiveYear, setEffectiveYear] = useState<string>('');
  const [criteria, setCriteria] = useState<MasterListAdmin | null>(null);
  const [form, setForm] = useState<GenerateMasterListRequest>(emptyForm(''));

  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);

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
        const message = err instanceof Error ? err.message : 'Unknown error';
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
        const message = err instanceof Error ? err.message : 'Unknown error';
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
      const message = err instanceof Error ? err.message : 'Unknown error';
      display({
        kind: 'error',
        title: 'Generation failed',
        subtitle: message,
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
        subtitle: err instanceof Error ? err.message : 'Unknown error',
        timeout: 9000,
      });
    } finally {
      setGenerating(false);
    }
  };

  const handleRegenerateDistrict = (orgUnitNo: string) =>
    runMutation(
      () => API.masterListAdmin.regenerateDistrict(effectiveYear, orgUnitNo),
      `District ${orgUnitNo} regenerated`,
    );

  const handleSaveComments = () =>
    runMutation(
      () => API.masterListAdmin.saveComments(effectiveYear, form.comments ?? ''),
      'Comments saved',
    );

  const handleDelete = () => {
    if (!window.confirm(`Delete the generated master list for ${effectiveYear}?`)) return;
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
                <TextInput
                  id="mla-min-date"
                  labelText="Min harvest-complete date"
                  placeholder="YYYY-MM-DD"
                  value={form.minHarvestCompleteDate ?? ''}
                  onChange={(e) => setForm({ ...form, minHarvestCompleteDate: e.target.value })}
                />
                <TextInput
                  id="mla-max-date"
                  labelText="Max harvest-complete date"
                  placeholder="YYYY-MM-DD"
                  value={form.maxHarvestCompleteDate ?? ''}
                  onChange={(e) => setForm({ ...form, maxHarvestCompleteDate: e.target.value })}
                />
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
                />
                <TextArea
                  id="mla-comments"
                  labelText="Generation comments"
                  rows={3}
                  value={form.comments ?? ''}
                  onChange={(e) => setForm({ ...form, comments: e.target.value })}
                />
              </div>
              {locked && (
                <p className="master-list-admin__lock-note">
                  Resource evaluations are under way for this year — the list is locked, so it
                  can&apos;t be (re-)generated or deleted. Use per-district Regenerate where
                  allowed.
                </p>
              )}
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
                    onClick={handleDelete}
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
                <TableHeaderBar title="Generation results" />
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableHeader>District</TableHeader>
                      <TableHeader>Name</TableHeader>
                      <TableHeader>Eligible</TableHeader>
                      <TableHeader>Selected</TableHeader>
                      <TableHeader>Action</TableHeader>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {criteria.generationStats.map((stat) => (
                      <TableRow key={stat.orgUnitNo || stat.orgUnitCode}>
                        <TableCell>{stat.orgUnitCode}</TableCell>
                        <TableCell>{stat.orgUnitName}</TableCell>
                        <TableCell>{stat.eligibleSites}</TableCell>
                        <TableCell>{stat.selectedSites}</TableCell>
                        <TableCell>
                          <Button
                            kind="ghost"
                            size="sm"
                            disabled={generating || !stat.orgUnitNo}
                            onClick={() => void handleRegenerateDistrict(stat.orgUnitNo)}
                          >
                            Regenerate
                          </Button>
                        </TableCell>
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

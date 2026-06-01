import {
  Button,
  Column,
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
  TextInput,
  Tile,
} from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

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
  resourceEvaluatedInd: 'BIO,RIP,WAT,CHR',
  comments: '',
});

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
          resourceEvaluatedInd: data.resourceEvaluatedInd,
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
        <InlineNotification
          kind="info"
          title="Stub generation"
          subtitle="This screen calls a stub backend; it does not currently mutate any data."
          hideCloseButton
          lowContrast
        />
      </Column>

      <Column sm={4} md={8} lg={16}>
        <Select
          id="master-list-admin-year"
          labelText="Master list year"
          value={effectiveYear}
          onChange={(e) => setEffectiveYear(e.target.value)}
          disabled={loading || years.length === 0}
        >
          {years.map((year) => (
            <SelectItem key={year.effectiveYear} value={year.effectiveYear} text={year.label} />
          ))}
        </Select>
      </Column>

      {loading && (
        <Column sm={4} md={8} lg={16}>
          <SkeletonText paragraph lineCount={6} />
        </Column>
      )}

      {!loading && criteria && (
        <>
          <Column sm={4} md={8} lg={10}>
            <Tile className="master-list-admin__panel">
              <h2>Eligibility criteria</h2>
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
                <TextInput
                  id="mla-protocols"
                  labelText="Protocols evaluated"
                  helperText="Comma-separated, e.g. BIO,RIP,WAT,CHR"
                  value={form.resourceEvaluatedInd ?? ''}
                  onChange={(e) => setForm({ ...form, resourceEvaluatedInd: e.target.value })}
                />
                <TextArea
                  id="mla-comments"
                  labelText="Generation comments"
                  rows={3}
                  value={form.comments ?? ''}
                  onChange={(e) => setForm({ ...form, comments: e.target.value })}
                />
              </div>
              <div className="master-list-admin__actions">
                <Button onClick={() => void handleGenerate()} disabled={generating}>
                  {criteria.generated ? 'Re-generate master list' : 'Generate master list'}
                </Button>
              </div>
            </Tile>
          </Column>

          <Column sm={4} md={8} lg={6}>
            <Tile className="master-list-admin__panel">
              <h2>Status</h2>
              <p>
                <span className="master-list-admin__label">Year</span>
                <span>{criteria.effectiveYear}</span>
              </p>
              <p>
                <span className="master-list-admin__label">Generated</span>
                {criteria.generated ? (
                  <Tag type="green" size="sm">
                    Yes
                  </Tag>
                ) : (
                  <Tag type="gray" size="sm">
                    Not yet
                  </Tag>
                )}
              </p>
              <p>
                <span className="master-list-admin__label">Comments</span>
                <span>{criteria.generationComments || '—'}</span>
              </p>
            </Tile>
          </Column>

          <Column sm={4} md={8} lg={16}>
            {criteria.generationStats.length === 0 ? (
              <p>No generation stats yet — generate the list to see per-district counts.</p>
            ) : (
              <TableContainer title="Generation results" description="Sites generated per district">
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
                      <TableRow key={stat.orgUnitCode}>
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

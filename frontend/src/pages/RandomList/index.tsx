import {
  Button,
  Column,
  DataTable,
  Grid,
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
} from '@carbon/react';
import { useCallback, useEffect, useMemo, useState, type FC } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import type { MasterListYear, OrgUnit } from '@/types/configuration';
import type { RandomListSite } from '@/types/randomList';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

import './randomList.scss';

const TABLE_HEADERS = [
  { key: 'openingNumber', header: 'Opening' },
  { key: 'orgUnitCode', header: 'District' },
  { key: 'licenceId', header: 'Licence' },
  { key: 'cuttingPermitId', header: 'CP' },
  { key: 'cutBlockId', header: 'Cut block' },
  { key: 'grossArea', header: 'Gross area (ha)' },
  { key: 'netArea', header: 'Net area (ha)' },
  { key: 'disturbanceEndDate', header: 'Harvest complete' },
  { key: 'existingChecklists', header: 'Existing checklists' },
  { key: 'underReview', header: 'Status' },
] as const;

const formatArea = (value: number | null): string => (value == null ? '' : value.toFixed(1));

function toTableRows(sites: RandomListSite[]) {
  return sites.map((site) => ({
    id: site.frepSelectedSiteId,
    openingNumber: site.openingNumber,
    orgUnitCode: site.orgUnitCode,
    licenceId: site.licenceId,
    cuttingPermitId: site.cuttingPermitId,
    cutBlockId: site.cutBlockId,
    grossArea: formatArea(site.grossArea),
    netArea: formatArea(site.netArea),
    disturbanceEndDate: site.disturbanceEndDate ?? '',
    existingChecklists: site.existingChecklists.join(', '),
    underReview: site.underReview,
  }));
}

const RandomListPage: FC = () => {
  const { display } = useNotification();

  const [masterListYears, setMasterListYears] = useState<MasterListYear[]>([]);
  const [orgUnits, setOrgUnits] = useState<OrgUnit[]>([]);
  const [configLoading, setConfigLoading] = useState(true);

  const [effectiveYear, setEffectiveYear] = useState<string>('');
  const [orgUnit, setOrgUnit] = useState<string>('');

  const [sites, setSites] = useState<RandomListSite[]>([]);
  const [loading, setLoading] = useState(false);
  const [hasError, setHasError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setConfigLoading(true);

    Promise.all([API.configuration.getMasterListYears(), API.configuration.getOrgUnits()])
      .then(([years, units]) => {
        if (cancelled) return;
        setMasterListYears(years);
        setOrgUnits(units);

        const defaultYear = years.find((year) => year.current) ?? years[0];
        if (defaultYear) setEffectiveYear(defaultYear.effectiveYear);
        if (units[0]) setOrgUnit(units[0].orgUnitNo);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        const message = err instanceof Error ? err.message : 'Unknown error';
        display({
          kind: 'error',
          title: "We couldn't load filter options",
          subtitle: message,
          timeout: 9000,
        });
      })
      .finally(() => {
        if (!cancelled) setConfigLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [display]);

  const loadRandomList = useCallback(async () => {
    if (!effectiveYear) return;

    setLoading(true);
    setHasError(false);

    try {
      const data = await API.randomList.getRandomList({
        effectiveYear,
        orgUnit: orgUnit || undefined,
      });
      setSites(data);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unknown error';
      display({
        kind: 'error',
        title: "We couldn't load the district random list",
        subtitle: message,
        timeout: 9000,
      });
      setHasError(true);
      setSites([]);
    } finally {
      setLoading(false);
    }
  }, [display, effectiveYear, orgUnit]);

  useEffect(() => {
    void loadRandomList();
  }, [loadRandomList]);

  const tableRows = useMemo(() => toTableRows(sites), [sites]);

  return (
    <Grid fullWidth className="default-grid random-list-grid">
      <Column sm={4} md={8} lg={16}>
        <div className="random-list__header">
          <h1>District Random List</h1>
        </div>
        <p className="random-list__subtitle">
          Randomly selected sites for the chosen master list year and district. Pick a row to view
          site details.
        </p>
      </Column>

      <Column sm={4} md={8} lg={16}>
        <div className="random-list__filters">
          <Select
            id="random-list-year"
            labelText="Master list year"
            value={effectiveYear}
            onChange={(event) => setEffectiveYear(event.target.value)}
            disabled={configLoading || masterListYears.length === 0}
          >
            {masterListYears.map((year) => (
              <SelectItem key={year.effectiveYear} value={year.effectiveYear} text={year.label} />
            ))}
          </Select>
          <Select
            id="random-list-org-unit"
            labelText="Org unit"
            value={orgUnit}
            onChange={(event) => setOrgUnit(event.target.value)}
            disabled={configLoading || orgUnits.length === 0}
          >
            <SelectItem value="" text="All districts" />
            {orgUnits.map((unit) => (
              <SelectItem
                key={unit.orgUnitNo}
                value={unit.orgUnitNo}
                text={`${unit.orgUnitCode} — ${unit.orgUnitName}`}
              />
            ))}
          </Select>
          <Button
            kind="secondary"
            onClick={() => void loadRandomList()}
            disabled={loading || configLoading || !effectiveYear}
          >
            Refresh
          </Button>
        </div>
      </Column>

      <Column sm={4} md={8} lg={16}>
        {(loading || configLoading) && (
          <div aria-busy data-testid="random-list-loading">
            <SkeletonText paragraph lineCount={4} />
          </div>
        )}
        {!loading && !configLoading && hasError && (
          <p data-testid="random-list-error">We couldn&apos;t load the district random list.</p>
        )}
        {!loading && !configLoading && !hasError && sites.length === 0 && (
          <p data-testid="random-list-empty">No sites match the selected filters.</p>
        )}
        {!loading && !configLoading && !hasError && sites.length > 0 && (
          <DataTable rows={tableRows} headers={[...TABLE_HEADERS]} data-testid="random-list-table">
            {({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
              <TableContainer
                title="District random list"
                description="Sites generated for evaluation"
              >
                <Table {...getTableProps()}>
                  <TableHead>
                    <TableRow>
                      {headers.map((header) => (
                        <TableHeader {...getHeaderProps({ header })} key={header.key}>
                          {header.header}
                        </TableHeader>
                      ))}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {rows.map((row) => {
                      const meta = tableRows.find((item) => item.id === row.id);
                      return (
                        <TableRow {...getRowProps({ row })} key={row.id}>
                          {row.cells.map((cell) => {
                            if (cell.info.header === 'openingNumber' && meta) {
                              return (
                                <TableCell key={cell.id}>
                                  <RouterLink to={`/site-detail/${meta.id}`}>
                                    {cell.value}
                                  </RouterLink>
                                </TableCell>
                              );
                            }
                            if (cell.info.header === 'underReview') {
                              return (
                                <TableCell key={cell.id}>
                                  {cell.value ? (
                                    <Tag type="green" size="sm">
                                      In review
                                    </Tag>
                                  ) : (
                                    <Tag type="gray" size="sm">
                                      Pending
                                    </Tag>
                                  )}
                                </TableCell>
                              );
                            }
                            return <TableCell key={cell.id}>{cell.value}</TableCell>;
                          })}
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </DataTable>
        )}
      </Column>
    </Grid>
  );
};

export default RandomListPage;

import { Map as MapIcon } from '@carbon/icons-react';
import {
  Button,
  Column,
  DataTable,
  Grid,
  Pagination,
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

import TableHeaderBar from '@/components/core/TableHeaderBar';
import OpeningMapModal from '@/components/OpeningMapModal';

import type { MasterListYear, OrgUnit } from '@/types/configuration';
import type { RandomListSite, RandomListSummary } from '@/types/randomList';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';
import { requestRandomListCsv, triggerBrowserDownload } from '@/services/reports';

import './randomList.scss';

const TABLE_HEADERS = [
  { key: 'underReview', header: 'Status' },
  { key: 'openingNumber', header: 'Opening' },
  { key: 'orgUnitCode', header: 'Org Unit' },
  { key: 'openingId', header: 'Opening ID' },
  { key: 'licenceId', header: 'Licence' },
  { key: 'cuttingPermitId', header: 'CP' },
  { key: 'cutBlockId', header: 'Blk' },
  { key: 'exhibitArea', header: 'Exhibit A (ha)' },
  { key: 'disturbanceStartDate', header: 'Harvest start date' },
  { key: 'disturbanceEndDate', header: 'Harvest complete date' },
  { key: 'managementUnit', header: 'Mgmt. unit' },
  { key: 'grossArea', header: 'Gross area (ha)' },
  { key: 'netArea', header: 'Net area (ha)' },
  { key: 'existingChecklists', header: 'Existing checklists' },
  { key: 'mapView', header: '' },
] as const;

const formatArea = (value: number | null): string => (value == null ? '' : value.toFixed(1));

// Exhibit-A area is shown at its stored precision (legacy displays up to 4 dp).
const formatExhibit = (value: number | null): string => (value == null ? '' : String(value));

function toTableRows(sites: RandomListSite[]) {
  return (sites ?? []).map((site) => ({
    id: site.frepSelectedSiteId,
    underReview: site.underReview,
    openingNumber: site.openingNumber,
    orgUnitCode: site.orgUnitCode,
    openingId: site.openingId,
    licenceId: site.licenceId,
    cuttingPermitId: site.cuttingPermitId,
    cutBlockId: site.cutBlockId,
    exhibitArea: formatExhibit(site.exhibitArea),
    disturbanceStartDate: site.disturbanceStartDate ?? '',
    disturbanceEndDate: site.disturbanceEndDate ?? '',
    managementUnit: site.managementUnit ?? '',
    grossArea: formatArea(site.grossArea),
    netArea: formatArea(site.netArea),
    existingChecklists: site.existingChecklists.join(', '),
    mapView: site.openingId,
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
  const [summary, setSummary] = useState<RandomListSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [hasError, setHasError] = useState(false);

  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(15);

  // Opening whose polygon map is shown in the modal (null = closed).
  const [mapOpeningId, setMapOpeningId] = useState<string | null>(null);

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
      setSites(data.sites ?? []);
      setSummary(data.summary ?? null);
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
      setSummary(null);
    } finally {
      setLoading(false);
    }
  }, [display, effectiveYear, orgUnit]);

  useEffect(() => {
    void loadRandomList();
  }, [loadRandomList]);

  const exportCsv = useCallback(async () => {
    if (!effectiveYear) return;
    try {
      const { blob, filename } = await requestRandomListCsv(effectiveYear, orgUnit || undefined);
      triggerBrowserDownload(blob, filename);
    } catch (err) {
      display({
        kind: 'error',
        title: "We couldn't export the random list",
        subtitle: err instanceof Error ? err.message : 'Unknown error',
        timeout: 9000,
      });
    }
  }, [display, effectiveYear, orgUnit]);

  const tableRows = useMemo(() => toTableRows(sites), [sites]);

  // Reset to the first page whenever a new result set loads.
  useEffect(() => {
    setPage(1);
  }, [sites]);

  const paginatedRows = useMemo(
    () => tableRows.slice((page - 1) * pageSize, page * pageSize),
    [tableRows, page, pageSize],
  );

  return (
    <Grid fullWidth className="default-grid random-list-grid">
      <Column sm={4} md={8} lg={16}>
        <div className="random-list__header">
          <h1>District Random List</h1>
        </div>
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
          <DataTable
            rows={paginatedRows}
            headers={[...TABLE_HEADERS]}
            data-testid="random-list-table"
          >
            {({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
              <TableContainer>
                <TableHeaderBar
                  title={
                    summary ? (
                      <span className="random-list__summary" data-testid="random-list-summary">
                        <strong># of Sites Accepted</strong>
                        {summary.orgUnitDescription ? ` — ${summary.orgUnitDescription}` : ''}
                        {' — '}
                        Biodiversity: {summary.biodiversity} &nbsp; Cultural Heritage:{' '}
                        {summary.culturalHeritage} &nbsp; Riparian: {summary.riparian} &nbsp; Water:{' '}
                        {summary.water}
                      </span>
                    ) : null
                  }
                  actions={
                    <Button
                      kind="tertiary"
                      size="md"
                      onClick={() => void exportCsv()}
                      disabled={loading || configLoading || !effectiveYear}
                    >
                      Export to CSV
                    </Button>
                  }
                />
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
                            // The Opening value links to the internal FREP site-detail page.
                            // TODO(frep-external-links): Legacy linked Opening ID to the external
                            // openings viewer (ECAS, resultsLnkAction.do?userAction=Opening) and
                            // Licence to FTA (ftaLnkAction.do?userAction=Licence). Those corporate
                            // integrations are not wired in the new app yet, so Opening ID and
                            // Licence render as plain text — wire the real external URLs once
                            // available.
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
                            if (cell.info.header === 'mapView') {
                              return (
                                <TableCell key={cell.id} className="random-list__map-cell">
                                  <Button
                                    kind="ghost"
                                    size="sm"
                                    hasIconOnly
                                    renderIcon={MapIcon}
                                    iconDescription="Site Map"
                                    tooltipPosition="left"
                                    className="random-list__map-btn"
                                    disabled={!cell.value}
                                    onClick={() => setMapOpeningId(String(cell.value))}
                                  />
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
                <Pagination
                  page={page}
                  pageSize={pageSize}
                  pageSizes={[15, 25, 50, 100]}
                  totalItems={tableRows.length}
                  size="md"
                  onChange={({ page: nextPage, pageSize: nextPageSize }) => {
                    setPage(nextPage);
                    setPageSize(nextPageSize);
                  }}
                />
              </TableContainer>
            )}
          </DataTable>
        )}
      </Column>
      <OpeningMapModal openingId={mapOpeningId} onClose={() => setMapOpeningId(null)} />
    </Grid>
  );
};

export default RandomListPage;

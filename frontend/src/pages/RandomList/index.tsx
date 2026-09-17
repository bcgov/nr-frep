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

import { ExternalLink } from '@/components/core/ExternalLink';
import PrintableTable from '@/components/core/PrintableTable';
import TableHeaderBar from '@/components/core/TableHeaderBar';
import OpeningMapModal from '@/components/OpeningMapModal';

import type { MasterListYear, OrgUnit } from '@/types/configuration';
import type { RandomListSite, RandomListSummary } from '@/types/randomList';

import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { randomListCsvFilename } from '@/pages/RandomList/randomListCsvFilename';
import API from '@/services/APIs';
import { requestRandomListCsv, triggerBrowserDownload } from '@/services/reports';
import { apiErrorMessage } from '@/utils/apiError';
import { formatShortDate } from '@/utils/date';
import { silvaOpeningUrl } from '@/utils/silva';

import './randomList.scss';

const TABLE_HEADERS = [
  { key: 'underReview', header: 'Status' },
  { key: 'openingNumber', header: 'Opening' },
  { key: 'orgUnitCode', header: 'Org Unit' },
  { key: 'openingId', header: 'Opening ID' },
  { key: 'licenceId', header: 'Licence' },
  { key: 'cuttingPermitId', header: 'Cutting Permit' },
  { key: 'cutBlockId', header: 'Cut Block' },
  { key: 'exhibitArea', header: 'Exhibit A (ha)' },
  { key: 'disturbanceStartDate', header: 'Harvest start date' },
  { key: 'disturbanceEndDate', header: 'Harvest complete date' },
  { key: 'managementUnit', header: 'Management unit' },
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
    disturbanceStartDate: formatShortDate(site.disturbanceStartDate),
    disturbanceEndDate: formatShortDate(site.disturbanceEndDate),
    managementUnit: site.managementUnit ?? '',
    grossArea: formatArea(site.grossArea),
    netArea: formatArea(site.netArea),
    existingChecklists: site.existingChecklists.join(', '),
    mapView: site.openingId,
  }));
}

const RandomListPage: FC = () => {
  const { display } = useNotification();
  const { user } = useAuth();

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
        const message = apiErrorMessage(err);
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
      const message = apiErrorMessage(err);
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
      const { blob } = await requestRandomListCsv(effectiveYear, orgUnit || undefined);
      // Prefer a descriptive name derived from the selected filters over the backend default.
      triggerBrowserDownload(blob, randomListCsvFilename(effectiveYear, orgUnits, orgUnit));
    } catch (err) {
      display({
        kind: 'error',
        title: "We couldn't export the random list",
        subtitle: apiErrorMessage(err),
        timeout: 9000,
      });
    }
  }, [display, effectiveYear, orgUnit, orgUnits]);

  const tableRows = useMemo(() => toTableRows(sites), [sites]);

  // Reset to the first page whenever a new result set loads.
  useEffect(() => {
    setPage(1);
  }, [sites]);

  const paginatedRows = useMemo(
    () => tableRows.slice((page - 1) * pageSize, page * pageSize),
    [tableRows, page, pageSize],
  );

  // Extracted from the table render so the per-cell handlers aren't nested >4 functions deep
  // (DataTable render-prop → rows.map → cells.map → onClick). Opening number links to site-detail,
  // Opening ID out to SILVA, the review flag renders a tag, and the map cell opens the in-app
  // opening map.
  // TODO(frep-external-links): legacy also linked Licence to FTA; that corporate integration isn't
  // wired yet, so Licence still renders as plain text.
  const renderCell = (
    cell: { id: string; value: string; info: { header: string } },
    meta: { id: string } | undefined,
  ) => {
    if (cell.info.header === 'openingNumber' && meta) {
      return (
        <TableCell key={cell.id}>
          <RouterLink to={`/site-detail/${meta.id}`}>{cell.value}</RouterLink>
        </TableCell>
      );
    }
    // Opening ID deep-links into SILVA with an idp_hint for the signed-in provider, so the user
    // lands on the opening without a second login. Rows without an opening id stay plain text.
    if (cell.info.header === 'openingId') {
      const href = silvaOpeningUrl(cell.value, user?.idpProvider);
      return (
        <TableCell key={cell.id}>
          {href ? <ExternalLink href={href}>{cell.value}</ExternalLink> : cell.value}
        </TableCell>
      );
    }
    if (cell.info.header === 'underReview') {
      return (
        <TableCell key={cell.id}>
          {cell.value ? (
            <Tag type="green" size="sm">
              Accepted
            </Tag>
          ) : (
            <Tag type="gray" size="sm">
              Not accepted
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
  };

  // Print-only full list (legacy "Printer Version"): all rows, no pagination, no map column.
  const printColumns = TABLE_HEADERS.filter((header) => header.key !== 'mapView');
  const printRows = tableRows.map((row) => ({
    ...row,
    underReview: row.underReview ? 'Accepted' : 'Not accepted',
  }));
  const printMeta = [
    summary?.orgUnitDescription,
    effectiveYear ? `Master list ${effectiveYear}` : null,
  ]
    .filter(Boolean)
    .join(' — ');

  return (
    <>
      <Grid fullWidth className="default-grid random-list-grid print-hidden">
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
                          Stand Level Retention: {summary.biodiversity} &nbsp; Cultural Heritage:{' '}
                          {summary.culturalHeritage}
                        </span>
                      ) : null
                    }
                    actions={
                      <>
                        <Button
                          kind="tertiary"
                          size="md"
                          onClick={() => globalThis.print()}
                          disabled={loading || configLoading || tableRows.length === 0}
                        >
                          Print
                        </Button>
                        <Button
                          kind="tertiary"
                          size="md"
                          onClick={() => void exportCsv()}
                          disabled={loading || configLoading || !effectiveYear}
                        >
                          Export to CSV
                        </Button>
                      </>
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
                            {row.cells.map((cell) => renderCell(cell, meta))}
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
      <PrintableTable
        title="District Random List"
        meta={printMeta}
        columns={printColumns}
        rows={printRows}
      />
    </>
  );
};

export default RandomListPage;

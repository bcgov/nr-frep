import { Close, Search as SearchIcon } from '@carbon/icons-react';
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
  TextInput,
} from '@carbon/react';
import { useEffect, useMemo, useRef, useState, type FC } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import ClientSearchModal from '@/components/core/ClientSearchModal';
import TableHeaderBar from '@/components/core/TableHeaderBar';

import type { MasterListYear, OrgUnit, Protocol } from '@/types/configuration';
import type { ChecklistSearchQuery, ChecklistSearchResult } from '@/types/search';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import API from '@/services/APIs';
import { requestChecklistSearchCsv, triggerBrowserDownload } from '@/services/reports';
import { apiErrorMessage } from '@/utils/apiError';
import { STATUS_LABELS, statusLabel, statusTagType } from '@/utils/checklistStatus';
import { formatShortDate } from '@/utils/date';
import { buildExportFilename } from '@/utils/exportFilename';

import './checklistSearch.scss';

const TABLE_HEADERS = [
  { key: 'checklistStatus', header: 'Status' },
  { key: 'checklistId', header: 'Checklist' },
  { key: 'protocolCode', header: 'Protocol' },
  { key: 'effectiveYear', header: 'Year' },
  { key: 'orgUnitCode', header: 'Org. unit' },
  { key: 'licenceId', header: 'Licence' },
  { key: 'cuttingPermitId', header: 'Cutting Permit' },
  { key: 'cutBlockId', header: 'Cut block' },
  { key: 'openingId', header: 'Opening' },
  { key: 'clientNumber', header: 'Client #' },
  { key: 'evaluationDate', header: 'Evaluation date' },
  { key: 'evaluatorName', header: 'Evaluator' },
] as const;

const STATUS_OPTIONS = [
  { value: '', label: 'Any status' },
  ...Object.entries(STATUS_LABELS).map(([value, label]) => ({ value, label })),
];

const PAGE_SIZE_OPTIONS = [20, 50, 100];
const DEFAULT_PAGE_SIZE = 20;

// SLB (legacy) and SLR (going forward) are the same protocol/page → /protocol-checklists/slr/:id.
// CHR has its own route slug (handled below). Any other code has no page and is not linked.
const PROTOCOL_TO_PATH: Record<string, 'slr' | undefined> = {
  SLB: 'slr',
  SLR: 'slr',
};

/**
 * The row's checklist link: CHR opens its own screen, SLB/SLR open the protocol checklist (see
 * {@link PROTOCOL_TO_PATH}); any other (or unknown) protocol has no link.
 */
const checklistLinkFor = (protocolCode: string | undefined, id: string): string | undefined => {
  if (!protocolCode) return undefined;
  if (protocolCode === 'CHR') return `/protocol-checklists/chr/${id}`;
  const protoPath = PROTOCOL_TO_PATH[protocolCode];
  return protoPath ? `/protocol-checklists/${protoPath}/${id}` : undefined;
};

const ChecklistSearchPage: FC = () => {
  const { display } = useNotification();
  const { canEdit, canAnyChr, chrDistricts } = useAuthorization();

  const [masterListYears, setMasterListYears] = useState<MasterListYear[]>([]);
  const [orgUnits, setOrgUnits] = useState<OrgUnit[]>([]);
  const [protocols, setProtocols] = useState<Protocol[]>([]);
  const [configLoading, setConfigLoading] = useState(true);

  const [filters, setFilters] = useState<ChecklistSearchQuery>({});
  // The filters that produced the currently-displayed results. Export uses this (not the live
  // `filters`) so a CSV always matches the visible table even if the user edited a filter without
  // re-searching.
  const [searchedFilters, setSearchedFilters] = useState<ChecklistSearchQuery>({});
  const [results, setResults] = useState<ChecklistSearchResult[]>([]);
  // Server-side paging: page is 0-based (matches the backend); totalElements is the true match count
  // (no 5000 VARRAY cap), so every page is reachable.
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [hasError, setHasError] = useState(false);
  const [clientLookupOpen, setClientLookupOpen] = useState(false);
  // The checklist search filters by client NUMBER (legacy frep_checklist_search.search), but we
  // display the client NAME selected from the lookup — mirroring the legacy FREP400 screen where
  // "Client Name" is a read-only display populated by the FREP410 client picker.
  const [clientName, setClientName] = useState('');

  useEffect(() => {
    let cancelled = false;
    setConfigLoading(true);

    Promise.all([
      API.configuration.getMasterListYears(),
      API.configuration.getOrgUnits(),
      API.configuration.getProtocols(),
    ])
      .then(([years, units, fetchedProtocols]) => {
        if (cancelled) return;
        setMasterListYears(years);
        setOrgUnits(units);
        setProtocols(fetchedProtocols);
        // Default the year filter to the latest year in the list.
        const latestYear = years.reduce<string | undefined>(
          (latest, year) =>
            latest === undefined || Number(year.effectiveYear) > Number(latest)
              ? year.effectiveYear
              : latest,
          undefined,
        );
        if (latestYear) {
          setFilters((current) =>
            current.effectiveYear === undefined
              ? { ...current, effectiveYear: latestYear }
              : current,
          );
        }
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

  // Fetches one page. Search/Clear reset to page 0; the Pagination control passes the target page.
  // queryFilters lets Clear search with the reset filters without waiting for the state update.
  const runSearch = async (
    targetPage = page,
    targetSize = pageSize,
    queryFilters: ChecklistSearchQuery = filters,
  ) => {
    setLoading(true);
    setHasError(false);
    try {
      const data = await API.search.searchChecklistsPaged({
        ...queryFilters,
        pageNumber: targetPage,
        pageSize: targetSize,
      });
      setResults(data.content);
      setSearchedFilters(queryFilters);
      setTotalElements(data.totalElements);
      setPage(data.pageNumber);
      setPageSize(data.pageSize);
    } catch (err) {
      display({
        kind: 'error',
        title: "We couldn't run the search",
        subtitle: apiErrorMessage(err),
        timeout: 9000,
      });
      setHasError(true);
      setResults([]);
      setTotalElements(0);
    } finally {
      setLoading(false);
    }
  };

  const exportCsv = async () => {
    try {
      const { blob } = await requestChecklistSearchCsv(searchedFilters);
      // Descriptive name derived from the filters that produced the results (district code + year),
      // replacing the generic backend default.
      const orgUnitCode = orgUnits.find(
        (unit) => unit.orgUnitNo === searchedFilters.orgUnit,
      )?.orgUnitCode;
      triggerBrowserDownload(
        blob,
        buildExportFilename({
          base: 'FREP_Checklist_Search',
          orgUnitCode,
          effectiveYear: searchedFilters.effectiveYear,
        }),
      );
    } catch (err) {
      display({
        kind: 'error',
        title: "We couldn't export the search results",
        subtitle: apiErrorMessage(err),
        timeout: 9000,
      });
    }
  };

  const initialSearchDone = useRef(false);
  useEffect(() => {
    // Run the first search only after config (incl. the defaulted latest year) has loaded, so the
    // initial results match the selected year.
    if (configLoading || initialSearchDone.current) return;
    initialSearchDone.current = true;
    void runSearch();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [configLoading]);

  const updateFilter = <K extends keyof ChecklistSearchQuery>(
    key: K,
    value: ChecklistSearchQuery[K],
  ) => {
    setFilters((current) => ({ ...current, [key]: value }));
  };

  const tableRows = useMemo(
    () => results.map((row) => ({ id: row.checklistId, ...row })),
    [results],
  );

  // Code → display-name lookups so the cell renderer can resolve protocol / org-unit names with a
  // map get() instead of a nested find() (which pushes the cell function past the 4-level nesting cap).
  const protocolNameByCode = useMemo(
    () => new Map(protocols.map((p) => [p.code, p.name] as const)),
    [protocols],
  );
  const orgUnitNameByCode = useMemo(
    () => new Map(orgUnits.map((u) => [u.orgUnitCode, u.orgUnitName] as const)),
    [orgUnits],
  );

  // Scope the filter dropdowns to what the user can see (results are also filtered server-side): a
  // CHR-only user (no Bio) only gets their districts; the protocol list drops protocols they can't see.
  const districtOptions = useMemo(
    () =>
      canEdit
        ? orgUnits
        : orgUnits.filter((u) => chrDistricts.includes(u.orgUnitCode.toUpperCase())),
    [orgUnits, canEdit, chrDistricts],
  );
  const protocolOptions = useMemo(
    () => protocols.filter((p) => (p.code === 'CHR' ? canAnyChr : canEdit)),
    [protocols, canAnyChr, canEdit],
  );

  return (
    <Grid fullWidth className="default-grid checklist-search-grid">
      <Column sm={4} md={8} lg={16}>
        <h1 className="checklist-search__title">Checklist Search</h1>
        <p className="checklist-search__subtitle">
          Look up FREP checklists by tenure, opening, client, or protocol.
        </p>
      </Column>

      <Column sm={4} md={8} lg={16}>
        <div className="checklist-search__filters">
          <Select
            id="checklist-search-year"
            labelText="Master list year"
            value={filters.effectiveYear ?? ''}
            onChange={(e) => updateFilter('effectiveYear', e.target.value || undefined)}
            disabled={configLoading}
          >
            <SelectItem value="" text="Any year" />
            {masterListYears.map((year) => (
              <SelectItem key={year.effectiveYear} value={year.effectiveYear} text={year.label} />
            ))}
          </Select>
          <Select
            id="checklist-search-org-unit"
            labelText="Org unit"
            value={filters.orgUnit ?? ''}
            onChange={(e) => updateFilter('orgUnit', e.target.value || undefined)}
            disabled={configLoading}
          >
            <SelectItem value="" text="Any district" />
            {districtOptions.map((unit) => (
              <SelectItem
                key={unit.orgUnitNo}
                value={unit.orgUnitNo}
                text={`${unit.orgUnitCode} — ${unit.orgUnitName}`}
              />
            ))}
          </Select>
          <Select
            id="checklist-search-protocol"
            labelText="Protocol"
            value={filters.protocolType ?? ''}
            onChange={(e) => updateFilter('protocolType', e.target.value || undefined)}
            disabled={configLoading}
          >
            <SelectItem value="" text="Any protocol" />
            {protocolOptions.map((protocol) => (
              <SelectItem
                key={protocol.code}
                value={protocol.code}
                text={`${protocol.code} - ${protocol.name}`}
              />
            ))}
          </Select>
          <Select
            id="checklist-search-status"
            labelText="Status"
            value={filters.checklistStatusCode ?? ''}
            onChange={(e) => updateFilter('checklistStatusCode', e.target.value || undefined)}
          >
            {STATUS_OPTIONS.map((opt) => (
              <SelectItem key={opt.value} value={opt.value} text={opt.label} />
            ))}
          </Select>
          <TextInput
            id="checklist-search-opening"
            labelText="Opening ID"
            value={filters.openingId ?? ''}
            onChange={(e) => updateFilter('openingId', e.target.value || undefined)}
          />
          <div className="checklist-search__client">
            <TextInput
              id="checklist-search-client"
              labelText="Client name"
              placeholder="Use the lookup to select a client"
              readOnly
              value={clientName}
            />
            <div className="checklist-search__client-buttons">
              <Button
                hasIconOnly
                kind="tertiary"
                size="md"
                renderIcon={SearchIcon}
                iconDescription="Look up client"
                tooltipPosition="top"
                onClick={() => setClientLookupOpen(true)}
              />
              {filters.clientNumber && (
                <Button
                  hasIconOnly
                  kind="ghost"
                  size="md"
                  renderIcon={Close}
                  iconDescription="Clear client"
                  tooltipPosition="top"
                  onClick={() => {
                    setClientName('');
                    updateFilter('clientNumber', undefined);
                  }}
                />
              )}
            </div>
          </div>
          <div className="checklist-search__actions">
            <Button onClick={() => void runSearch(0)} disabled={loading}>
              Search
            </Button>
            <Button
              kind="ghost"
              onClick={() => {
                setFilters({});
                setClientName('');
                void runSearch(0, pageSize, {});
              }}
            >
              Clear
            </Button>
          </div>
        </div>
      </Column>

      <Column sm={4} md={8} lg={16}>
        {loading && (
          <div aria-busy data-testid="checklist-search-loading">
            <SkeletonText paragraph lineCount={4} />
          </div>
        )}
        {!loading && hasError && (
          <p data-testid="checklist-search-error">We couldn&apos;t run the search.</p>
        )}
        {!loading && !hasError && results.length === 0 && (
          <p data-testid="checklist-search-empty">No checklists match the selected filters.</p>
        )}
        {!loading && !hasError && results.length > 0 && (
          <DataTable
            rows={tableRows}
            headers={[...TABLE_HEADERS]}
            data-testid="checklist-search-table"
          >
            {({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
              <TableContainer>
                <TableHeaderBar
                  title={`Checklists — ${totalElements} match${totalElements === 1 ? '' : 'es'}`}
                  actions={
                    <Button
                      kind="tertiary"
                      size="md"
                      onClick={() => void exportCsv()}
                      disabled={loading || results.length === 0}
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
                      const data = results.find((r) => r.checklistId === row.id);
                      const checklistLink = checklistLinkFor(data?.protocolCode, row.id);
                      return (
                        <TableRow {...getRowProps({ row })} key={row.id}>
                          {row.cells.map((cell) => {
                            if (cell.info.header === 'checklistId' && checklistLink) {
                              return (
                                <TableCell key={cell.id}>
                                  <RouterLink to={checklistLink}>{cell.value}</RouterLink>
                                </TableCell>
                              );
                            }
                            if (cell.info.header === 'evaluationDate') {
                              return (
                                <TableCell key={cell.id}>{formatShortDate(cell.value)}</TableCell>
                              );
                            }
                            if (cell.info.header === 'protocolCode') {
                              const name = protocolNameByCode.get(cell.value);
                              return (
                                <TableCell key={cell.id}>
                                  {cell.value && name ? `${cell.value} - ${name}` : cell.value}
                                </TableCell>
                              );
                            }
                            if (cell.info.header === 'orgUnitCode') {
                              const name = orgUnitNameByCode.get(cell.value);
                              return (
                                <TableCell key={cell.id}>
                                  {cell.value && name ? `${cell.value} — ${name}` : cell.value}
                                </TableCell>
                              );
                            }
                            if (cell.info.header === 'checklistStatus') {
                              const code = data?.checklistStatusCode;
                              const label = statusLabel(code, cell.value);
                              return (
                                <TableCell key={cell.id}>
                                  {label ? (
                                    <Tag type={statusTagType(code)} size="sm">
                                      {label}
                                    </Tag>
                                  ) : null}
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
                  page={page + 1}
                  pageSize={pageSize}
                  pageSizes={PAGE_SIZE_OPTIONS}
                  totalItems={totalElements}
                  disabled={loading}
                  onChange={({ page: nextPage, pageSize: nextSize }) => {
                    // Carbon's page is 1-based; the backend is 0-based. A page-size change resets to
                    // page 1 so the offset stays valid.
                    if (nextSize === pageSize) {
                      void runSearch(nextPage - 1, nextSize);
                    } else {
                      void runSearch(0, nextSize);
                    }
                  }}
                />
              </TableContainer>
            )}
          </DataTable>
        )}
      </Column>

      <ClientSearchModal
        open={clientLookupOpen}
        onClose={() => setClientLookupOpen(false)}
        onSelect={(clientNumber, selectedClientName) => {
          updateFilter('clientNumber', clientNumber || undefined);
          setClientName(selectedClientName);
        }}
      />
    </Grid>
  );
};

export default ChecklistSearchPage;

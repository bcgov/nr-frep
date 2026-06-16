import { Close, Search as SearchIcon } from '@carbon/icons-react';
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
  TextInput,
} from '@carbon/react';
import { useEffect, useMemo, useRef, useState, type FC } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import ClientSearchModal from '@/components/core/ClientSearchModal';
import TableHeaderBar from '@/components/core/TableHeaderBar';

import type { MasterListYear, OrgUnit, Protocol } from '@/types/configuration';
import type { ChecklistSearchQuery, ChecklistSearchResult } from '@/types/search';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';
import { requestChecklistSearchCsv, triggerBrowserDownload } from '@/services/reports';
import { apiErrorMessage } from '@/utils/apiError';

import './checklistSearch.scss';

const TABLE_HEADERS = [
  { key: 'checklistId', header: 'Checklist' },
  { key: 'protocolCode', header: 'Protocol' },
  { key: 'effectiveYear', header: 'Year' },
  { key: 'orgUnitCode', header: 'District' },
  { key: 'licenceId', header: 'Licence' },
  { key: 'cuttingPermitId', header: 'CP' },
  { key: 'cutBlockId', header: 'Cut block' },
  { key: 'openingId', header: 'Opening' },
  { key: 'clientNumber', header: 'Client #' },
  { key: 'evaluationDate', header: 'Eval. date' },
  { key: 'evaluatorUserid', header: 'Evaluator' },
  { key: 'checklistStatus', header: 'Status' },
] as const;

const STATUS_OPTIONS = [
  { value: '', label: 'Any status' },
  { value: 'RDY', label: 'Ready' },
  { value: 'SUB', label: 'Submitted' },
];

// Only biodiversity (legacy SLB) has a protocol-checklist page; CHR is handled separately. Riparian
// (RIP) and Water (WTR) are out of scope and have no pages, so their rows are not linked.
const PROTOCOL_TO_PATH: Record<string, 'biodiversity' | undefined> = {
  SLB: 'biodiversity',
  BIO: 'biodiversity',
};

const ChecklistSearchPage: FC = () => {
  const { display } = useNotification();

  const [masterListYears, setMasterListYears] = useState<MasterListYear[]>([]);
  const [orgUnits, setOrgUnits] = useState<OrgUnit[]>([]);
  const [protocols, setProtocols] = useState<Protocol[]>([]);
  const [configLoading, setConfigLoading] = useState(true);

  const [filters, setFilters] = useState<ChecklistSearchQuery>({});
  const [results, setResults] = useState<ChecklistSearchResult[]>([]);
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

  const runSearch = async () => {
    setLoading(true);
    setHasError(false);
    try {
      const data = await API.search.searchChecklists(filters);
      setResults(data);
    } catch (err) {
      display({
        kind: 'error',
        title: "We couldn't run the search",
        subtitle: apiErrorMessage(err),
        timeout: 9000,
      });
      setHasError(true);
      setResults([]);
    } finally {
      setLoading(false);
    }
  };

  const exportCsv = async () => {
    try {
      const { blob, filename } = await requestChecklistSearchCsv(filters);
      triggerBrowserDownload(blob, filename);
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
            {orgUnits.map((unit) => (
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
            {protocols.map((protocol) => (
              <SelectItem key={protocol.code} value={protocol.code} text={protocol.name} />
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
            <Button onClick={() => void runSearch()} disabled={loading}>
              Search
            </Button>
            <Button
              kind="ghost"
              onClick={() => {
                setFilters({});
                setClientName('');
                void runSearch();
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
                  title={`Checklists — ${results.length} match${results.length === 1 ? '' : 'es'}`}
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
                      const protoPath = data ? PROTOCOL_TO_PATH[data.protocolCode] : undefined;
                      const isChr = data?.protocolCode === 'CHR';
                      const checklistLink = isChr
                        ? `/chr/checklists/${row.id}`
                        : protoPath
                          ? `/protocol-checklists/${protoPath}/${row.id}`
                          : undefined;
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

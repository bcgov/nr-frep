import {
  Button,
  Column,
  DataTable,
  Grid,
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
import { useEffect, useMemo, useState, type FC } from 'react';

import type { ClientSearchQuery, ClientSearchResult } from '@/types/search';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

import './clientSearch.scss';

const TABLE_HEADERS = [
  { key: 'clientNumber', header: 'Client #' },
  { key: 'clientName', header: 'Name' },
  { key: 'clientStatus', header: 'Status' },
  { key: 'locationCount', header: 'Locations' },
] as const;

const ClientSearchPage: FC = () => {
  const { display } = useNotification();

  const [filters, setFilters] = useState<ClientSearchQuery>({});
  const [results, setResults] = useState<ClientSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [hasError, setHasError] = useState(false);

  const runSearch = async () => {
    setLoading(true);
    setHasError(false);
    try {
      const data = await API.search.searchClients(filters);
      setResults(data);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unknown error';
      display({
        kind: 'error',
        title: "We couldn't run the client search",
        subtitle: message,
        timeout: 9000,
      });
      setHasError(true);
      setResults([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void runSearch();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const tableRows = useMemo(() => results.map((r) => ({ id: r.clientNumber, ...r })), [results]);

  return (
    <Grid fullWidth className="default-grid client-search-grid">
      <Column sm={4} md={8} lg={16}>
        <h1 className="client-search__title">Client Search</h1>
        <p className="client-search__subtitle">
          Find Forest Client records by client number or name (substring, case-insensitive).
        </p>
      </Column>

      <Column sm={4} md={8} lg={16}>
        <div className="client-search__filters">
          <TextInput
            id="client-search-number"
            labelText="Client number"
            placeholder="e.g. 00010001"
            value={filters.clientNumber ?? ''}
            onChange={(e) => setFilters({ ...filters, clientNumber: e.target.value || undefined })}
          />
          <TextInput
            id="client-search-name"
            labelText="Client name"
            placeholder="e.g. tolko"
            value={filters.clientName ?? ''}
            onChange={(e) => setFilters({ ...filters, clientName: e.target.value || undefined })}
          />
          <div className="client-search__actions">
            <Button onClick={() => void runSearch()} disabled={loading}>
              Search
            </Button>
            <Button
              kind="ghost"
              onClick={() => {
                setFilters({});
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
          <div aria-busy data-testid="client-search-loading">
            <SkeletonText paragraph lineCount={4} />
          </div>
        )}
        {!loading && hasError && (
          <p data-testid="client-search-error">We couldn&apos;t run the search.</p>
        )}
        {!loading && !hasError && results.length === 0 && (
          <p data-testid="client-search-empty">No clients match the selected filters.</p>
        )}
        {!loading && !hasError && results.length > 0 && (
          <DataTable
            rows={tableRows}
            headers={[...TABLE_HEADERS]}
            data-testid="client-search-table"
          >
            {({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
              <TableContainer
                title="Clients"
                description={`${results.length} match${results.length === 1 ? '' : 'es'}`}
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
                    {rows.map((row) => (
                      <TableRow {...getRowProps({ row })} key={row.id}>
                        {row.cells.map((cell) => {
                          if (cell.info.header === 'clientStatus') {
                            const active = cell.value === 'ACT';
                            return (
                              <TableCell key={cell.id}>
                                <Tag type={active ? 'green' : 'gray'} size="sm">
                                  {active ? 'Active' : 'Deactivated'}
                                </Tag>
                              </TableCell>
                            );
                          }
                          return <TableCell key={cell.id}>{cell.value}</TableCell>;
                        })}
                      </TableRow>
                    ))}
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

export default ClientSearchPage;

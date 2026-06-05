import {
  Button,
  DataTable,
  Modal,
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
import { useCallback, useEffect, useMemo, useState, type FC } from 'react';

import type { ClientSearchQuery, ClientSearchResult } from '@/types/search';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

import './clientSearchModal.scss';

const TABLE_HEADERS = [
  { key: 'clientAcronym', header: 'Acronym' },
  { key: 'clientNumber', header: 'Client #' },
  { key: 'clientLocnCode', header: 'Locn' },
  { key: 'clientName', header: 'Name' },
  { key: 'clientLocnName', header: 'Location' },
  { key: 'city', header: 'City' },
  { key: 'clientStatus', header: 'Status' },
] as const;

type ClientSearchModalProps = {
  open: boolean;
  onClose: () => void;
  /** Invoked with the selected client number; the modal closes after selection. */
  onSelect: (clientNumber: string) => void;
};

/**
 * Client lookup launched from the Checklist Search "Client #" field, mirroring the legacy FREP410
 * sub-search: search Forest Client records, then select one to populate the client number.
 */
const ClientSearchModal: FC<ClientSearchModalProps> = ({ open, onClose, onSelect }) => {
  const { display } = useNotification();

  const [filters, setFilters] = useState<ClientSearchQuery>({});
  const [results, setResults] = useState<ClientSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [hasError, setHasError] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);

  const runSearch = useCallback(
    async (query: ClientSearchQuery) => {
      setLoading(true);
      setHasError(false);
      setHasSearched(true);
      try {
        const data = await API.search.searchClients(query);
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
    },
    [display],
  );

  // Reset the lookup each time it is opened so it never shows a stale prior search.
  useEffect(() => {
    if (open) {
      setFilters({});
      setResults([]);
      setHasError(false);
      setHasSearched(false);
    }
  }, [open]);

  const tableRows = useMemo(
    () =>
      results.map((r, index) => ({
        id: `${r.clientNumber}-${r.clientLocnCode}-${index}`,
        ...r,
      })),
    [results],
  );

  return (
    <Modal
      open={open}
      onRequestClose={onClose}
      modalHeading="Client Search"
      passiveModal
      size="lg"
      className="client-search-modal"
    >
      <p className="client-search-modal__hint">
        Find a Forest Client by acronym, client number, or name (substring, case-insensitive), then
        select a row to use its client number.
      </p>

      <div className="client-search-modal__filters">
        <TextInput
          id="client-modal-acronym"
          labelText="Client acronym"
          placeholder="e.g. TOLKO"
          value={filters.clientAcronym ?? ''}
          onChange={(e) => setFilters({ ...filters, clientAcronym: e.target.value || undefined })}
        />
        <TextInput
          id="client-modal-number"
          labelText="Client number"
          placeholder="e.g. 00010001"
          value={filters.clientNumber ?? ''}
          onChange={(e) => setFilters({ ...filters, clientNumber: e.target.value || undefined })}
        />
        <TextInput
          id="client-modal-name"
          labelText="Client / last name"
          placeholder="e.g. tolko"
          value={filters.clientName ?? ''}
          onChange={(e) => setFilters({ ...filters, clientName: e.target.value || undefined })}
        />
        <TextInput
          id="client-modal-first-name"
          labelText="First name"
          value={filters.legalFirstName ?? ''}
          onChange={(e) => setFilters({ ...filters, legalFirstName: e.target.value || undefined })}
        />
        <TextInput
          id="client-modal-middle-name"
          labelText="Middle name"
          value={filters.legalMiddleName ?? ''}
          onChange={(e) => setFilters({ ...filters, legalMiddleName: e.target.value || undefined })}
        />
        <div className="client-search-modal__actions">
          <Button onClick={() => void runSearch(filters)} disabled={loading}>
            Search
          </Button>
          <Button
            kind="ghost"
            onClick={() => {
              setFilters({});
              void runSearch({});
            }}
          >
            Clear
          </Button>
        </div>
      </div>

      {loading && (
        <div aria-busy data-testid="client-modal-loading">
          <SkeletonText paragraph lineCount={4} />
        </div>
      )}
      {!loading && hasError && (
        <p data-testid="client-modal-error">We couldn&apos;t run the search.</p>
      )}
      {!loading && !hasError && hasSearched && results.length === 0 && (
        <p data-testid="client-modal-empty">No clients match the selected filters.</p>
      )}
      {!loading && !hasError && results.length > 0 && (
        <DataTable rows={tableRows} headers={[...TABLE_HEADERS]} data-testid="client-modal-table">
          {({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
            <TableContainer>
              <Table {...getTableProps()}>
                <TableHead>
                  <TableRow>
                    {headers.map((header) => (
                      <TableHeader {...getHeaderProps({ header })} key={header.key}>
                        {header.header}
                      </TableHeader>
                    ))}
                    <TableHeader aria-label="Select client" />
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
                      <TableCell>
                        <Button
                          kind="ghost"
                          size="sm"
                          onClick={() => {
                            const clientNumberCell = row.cells.find(
                              (c) => c.info.header === 'clientNumber',
                            );
                            onSelect(String(clientNumberCell?.value ?? ''));
                            onClose();
                          }}
                        >
                          Select
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </DataTable>
      )}
    </Modal>
  );
};

export default ClientSearchModal;

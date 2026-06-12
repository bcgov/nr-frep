import {
  Button,
  DataTable,
  Pagination,
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
import { useCallback, useEffect, useState, type FC } from 'react';

import type { CodeOption, EvaluatorSearchResult } from '@/types/configuration';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';
import { apiErrorMessage } from '@/utils/apiError';

const PAGE_SIZE = 10;

const TABLE_HEADERS = [
  { key: 'userId', header: 'User ID' },
  { key: 'name', header: 'Name' },
  { key: 'actions', header: '' },
] as const;

type Filters = { userId: string; firstName: string; lastName: string };

type EvaluatorSearchProps = {
  /** Invoked with the selected FREP editor (code = IDIR username) and whether to add them as lead. */
  onSelect: (user: CodeOption, asTeamLead: boolean) => void;
  /** IDIR usernames already on the team — shown as "Added" (can't be added again). */
  excludeUserIds?: string[];
  /** When a team lead is already assigned, hide "Add as team lead" (only one lead is allowed). */
  leadAssigned?: boolean;
  disabled?: boolean;
};

/**
 * Inline "Add evaluator" search — searches IDIR users holding the FREP editor role via FAM
 * (GET /external/v1/users?role=FREP_EDITOR), paginated. Used instead of a dropdown because FAM's
 * role endpoint caps results at 100/page. Auto-loads all FREP editors (page 1) on mount.
 */
const EvaluatorSearch: FC<EvaluatorSearchProps> = ({
  onSelect,
  excludeUserIds,
  leadAssigned,
  disabled,
}) => {
  const { display } = useNotification();

  const excluded = new Set((excludeUserIds ?? []).map((id) => id.toUpperCase()));

  const [userId, setUserId] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  // null until the user runs a search — no auto-search on mount.
  const [submitted, setSubmitted] = useState<Filters | null>(null);
  const [page, setPage] = useState(1);
  const [result, setResult] = useState<EvaluatorSearchResult | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (submitted === null) return;
    let cancelled = false;
    setLoading(true);
    API.configuration
      .searchEvaluators({ ...submitted, page, size: PAGE_SIZE })
      .then((data) => {
        if (!cancelled) setResult(data);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setResult({ users: [], total: 0, page, size: PAGE_SIZE });
        display({
          kind: 'error',
          title: 'Evaluator search failed',
          subtitle: apiErrorMessage(err),
          timeout: 9000,
        });
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [submitted, page, display]);

  const runSearch = useCallback(() => {
    setPage(1);
    setSubmitted({ userId, firstName, lastName });
  }, [userId, firstName, lastName]);

  const clear = useCallback(() => {
    setUserId('');
    setFirstName('');
    setLastName('');
    setPage(1);
    setSubmitted(null);
    setResult(null);
  }, []);

  const rows = (result?.users ?? []).map((u, index) => ({
    id: `${u.code}-${index}`,
    userId: u.code,
    name: u.description,
  }));

  return (
    <div className="evaluator-search">
      <p className="evaluator-search__hint">
        Search IDIR users with the FREP editor role (via FAM). Leave the fields blank to browse all,
        or filter by user ID or name.
      </p>

      <div className="evaluator-search__filters">
        <TextInput
          id="evaluator-userid"
          labelText="User ID"
          value={userId}
          onChange={(e) => setUserId(e.target.value)}
        />
        <TextInput
          id="evaluator-first-name"
          labelText="First name"
          value={firstName}
          onChange={(e) => setFirstName(e.target.value)}
        />
        <TextInput
          id="evaluator-last-name"
          labelText="Last name"
          value={lastName}
          onChange={(e) => setLastName(e.target.value)}
        />
        <Button onClick={runSearch} disabled={loading}>
          Search
        </Button>
        <Button kind="ghost" onClick={clear} disabled={loading}>
          Clear
        </Button>
      </div>

      {loading && <SkeletonText paragraph lineCount={4} data-testid="evaluator-loading" />}
      {!loading && result && rows.length === 0 && (
        <p data-testid="evaluator-empty">No FREP editors match the search.</p>
      )}
      {!loading && rows.length > 0 && (
        <>
          <DataTable rows={rows} headers={[...TABLE_HEADERS]}>
            {({ rows: dataRows, headers, getTableProps, getHeaderProps, getRowProps }) => (
              <TableContainer>
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
                    {dataRows.map((row) => {
                      const meta = rows.find((r) => r.id === row.id);
                      return (
                        <TableRow {...getRowProps({ row })} key={row.id}>
                          {row.cells.map((cell) =>
                            cell.info.header === 'actions' ? (
                              <TableCell key={cell.id}>
                                {meta && excluded.has(meta.userId.toUpperCase()) ? (
                                  <span className="evaluator-search__added">Added</span>
                                ) : (
                                  <div className="evaluator-search__row-actions">
                                    {!leadAssigned && (
                                      <Button
                                        size="sm"
                                        kind="tertiary"
                                        disabled={disabled}
                                        onClick={() =>
                                          meta &&
                                          onSelect(
                                            { code: meta.userId, description: meta.name },
                                            true,
                                          )
                                        }
                                      >
                                        Add as team lead
                                      </Button>
                                    )}
                                    <Button
                                      size="sm"
                                      kind="tertiary"
                                      disabled={disabled}
                                      onClick={() =>
                                        meta &&
                                        onSelect(
                                          { code: meta.userId, description: meta.name },
                                          false,
                                        )
                                      }
                                    >
                                      Add as team member
                                    </Button>
                                  </div>
                                )}
                              </TableCell>
                            ) : (
                              <TableCell key={cell.id}>{cell.value}</TableCell>
                            ),
                          )}
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </DataTable>
          {result && result.total > PAGE_SIZE && (
            <Pagination
              page={page}
              pageSize={PAGE_SIZE}
              pageSizes={[PAGE_SIZE]}
              totalItems={result.total}
              onChange={({ page: nextPage }) => setPage(nextPage)}
            />
          )}
        </>
      )}
    </div>
  );
};

export default EvaluatorSearch;

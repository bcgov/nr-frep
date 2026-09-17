import { ArrowLeft } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  Column,
  DatePicker,
  DatePickerInput,
  Grid,
  InlineLoading,
  InlineNotification,
  Pagination,
  Select,
  SelectItem,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react';
import { useEffect, useRef, useState, type FC } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import './addTargetSite.scss';

import ClientCombo from '@/components/core/ClientCombo';
import { ExternalLink } from '@/components/core/ExternalLink';

import type { OpeningSearchQuery, OpeningSearchResult } from '@/types/acceptedSite';
import type { CodeOption } from '@/types/configuration';

import { useAuth } from '@/context/auth/useAuth';
import API from '@/services/APIs';
import { apiErrorMessage } from '@/utils/apiError';
import { CLIENT_UNRESOLVED_MESSAGE, isClientTermUnresolved } from '@/utils/clientSearch';
import { silvaOpeningUrl } from '@/utils/silva';

type Filters = {
  forestFileId: string;
  cuttingPermitId: string;
  cutBlockId: string;
  openingId: string;
  timberMark: string;
  licenseeOpeningId: string;
  openingNumber1: string;
  openingNumber2: string;
  openingNumber3: string;
  openingNumber4: string;
  clientNumber: string;
  blockStatusSt: string;
  openCategoryCode: string;
  openingStatusCode: string;
  dateType: string;
  dateFrom: string;
  dateTo: string;
  sortBy: string;
  includeAllP87Ind: boolean;
};

const EMPTY_FILTERS: Filters = {
  forestFileId: '',
  cuttingPermitId: '',
  cutBlockId: '',
  openingId: '',
  timberMark: '',
  licenseeOpeningId: '',
  openingNumber1: '',
  openingNumber2: '',
  openingNumber3: '',
  openingNumber4: '',
  clientNumber: '',
  blockStatusSt: '',
  openCategoryCode: '',
  openingStatusCode: '',
  dateType: '',
  dateFrom: '',
  dateTo: '',
  sortBy: 'O',
  includeAllP87Ind: false,
};

const DATE_TYPES = ['Disturbance', 'Regen Delay', 'Free Growing', 'Update'];

/** A search result can have several cut-block rows per opening; key a row by both. */
const rowKey = (o: OpeningSearchResult) => `${o.openingId}-${o.cutBlockId}`;

/** Resolve a code to its description from a code list; falls back to the code, or '—' when blank. */
const labelFor = (codes: CodeOption[], code: string) =>
  code ? (codes.find((c) => c.code === code)?.description ?? code) : '—';

/**
 * A single Carbon date picker bound to a {@code YYYY-MM-DD} string. Same loop-avoidance guards as the
 * CHR DateField: pass a plain string (never an empty array) and only propagate a real change, so the
 * flatpickr re-sync on mount doesn't feed an update loop. Fills its container via {@code frep-date-picker}.
 */
const DateBox: FC<{
  id: string;
  labelText: string;
  value: string;
  onChange: (next: string) => void;
}> = ({ id, labelText, value, onChange }) => (
  <DatePicker
    className="frep-date-picker"
    datePickerType="single"
    dateFormat="Y-m-d"
    value={value || undefined}
    onChange={(dates: Date[]) => {
      const next = dates[0] ? dates[0].toISOString().slice(0, 10) : '';
      if (next !== value) onChange(next);
    }}
  >
    <DatePickerInput id={id} labelText={labelText} placeholder="YYYY-MM-DD" />
  </DatePicker>
);

/** Fixed page-size choices; the backend caps each call at 100 rows. */
const PAGE_SIZE_OPTIONS = [25, 50, 100];
const DEFAULT_PAGE_SIZE = 100;

/**
 * The URL carries the whole search, not just the targeting context.
 *
 * <p>Adding a site navigates away to the create flow, and Back lands here again. Holding the filters
 * in component state meant that remount produced an empty form and no results — the user had to
 * re-enter and re-run a search they had already done. Reading them back out of the query string
 * restores the page as they left it, and makes a search shareable and survivable across a refresh.
 *
 * <p>`orgUnit`, `orgUnitName` and `year` are the targeting context and are NOT filters; they arrive
 * from Accepted Sites and are preserved untouched.
 */
const CONTEXT_PARAMS = ['orgUnit', 'orgUnitName', 'year'] as const;

const filtersFromParams = (params: URLSearchParams): Filters => {
  const next = { ...EMPTY_FILTERS };
  for (const key of Object.keys(EMPTY_FILTERS) as (keyof Filters)[]) {
    const value = params.get(key);
    if (value === null) continue;
    if (key === 'includeAllP87Ind') next.includeAllP87Ind = value === 'true';
    else (next[key] as string) = value;
  }
  return next;
};

/** True when the URL carries a search to restore, as opposed to just the targeting context. */
const hasSearchInParams = (params: URLSearchParams): boolean =>
  (Object.keys(EMPTY_FILTERS) as (keyof Filters)[]).some((key) => params.get(key) !== null);

/**
 * "Add target site" page — a full port of the legacy SIL56 Opening Tenure Search. Reached from the
 * Accepted Sites page (which supplies the district context via query params). Searches the opening
 * inventory by tenure / opening number / status / dates / client, then validates a chosen opening for
 * targeting (FREP_200_ACCEPTED_SITES.ADD_TARGETED_SITE) and, on success, hands off to the site-detail
 * create flow.
 */
const AddTargetSitePage: FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  // Only for the SILVA deep link's idp_hint, so the user lands on the opening without a second login.
  const { user } = useAuth();
  const orgUnit = searchParams.get('orgUnit') ?? '';
  const orgUnitName = searchParams.get('orgUnitName') ?? '';
  const year = searchParams.get('year') ?? '';

  // Lazy initialisers: the URL is the source of truth on mount, so Back restores the search.
  const [filters, setFilters] = useState<Filters>(() => filtersFromParams(searchParams));
  const [results, setResults] = useState<OpeningSearchResult[] | null>(null);
  const [page, setPage] = useState(() => Number(searchParams.get('page') ?? 0));
  const [pageSize, setPageSize] = useState(
    () => Number(searchParams.get('size') ?? DEFAULT_PAGE_SIZE) || DEFAULT_PAGE_SIZE,
  );
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [validatingKey, setValidatingKey] = useState<string | null>(null);
  const [validationErrors, setValidationErrors] = useState<string[] | null>(null);
  const [blockStatuses, setBlockStatuses] = useState<CodeOption[]>([]);
  const [openCategories, setOpenCategories] = useState<CodeOption[]>([]);
  const [openingStatuses, setOpeningStatuses] = useState<CodeOption[]>([]);
  // Client filter: searched by name via the lookup, which fills the client number used by the query.
  // The lookup returns a label the query does not carry, so it rides in the URL too — otherwise a
  // restored search shows a client filter with an empty name field.
  const [clientName, setClientName] = useState(() => searchParams.get('clientName') ?? '');
  // Raw text in the client field, and whether the user has tried to search with it unresolved.
  const [clientTerm, setClientTerm] = useState(() => searchParams.get('clientName') ?? '');
  const [showClientError, setShowClientError] = useState(false);

  // Load the dropdown code lists on mount. Each loads independently so one failure doesn't block the
  // others. No mount-guard ref: it interacts badly with StrictMode's mount/unmount/remount, which
  // would set the ref on the first run and discard its results, leaving the lists empty. The
  // `cancelled` flag alone is correct, and the page mounts once per visit.
  useEffect(() => {
    let cancelled = false;
    const load = (fetcher: () => Promise<CodeOption[]>, setter: (codes: CodeOption[]) => void) =>
      void fetcher()
        .then((codes) => {
          if (!cancelled) setter(codes);
        })
        .catch(() => {});
    load(() => API.configuration.getBlockStatusCodes(), setBlockStatuses);
    load(() => API.configuration.getOpenCategoryCodes(), setOpenCategories);
    load(() => API.configuration.getOpeningStatusCodes(), setOpeningStatuses);
    return () => {
      cancelled = true;
    };
  }, []);

  // Restore the results for a search carried in the URL — the Back case. Runs once: `runSearch`
  // rewrites the params, so depending on them would loop.
  const restored = useRef(false);
  useEffect(() => {
    if (restored.current || !hasSearchInParams(searchParams)) return;
    restored.current = true;
    void runSearch(page, pageSize);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const reset = () => {
    setFilters(EMPTY_FILTERS);
    setClientName('');
    setClientTerm('');
    setShowClientError(false);
    setResults(null);
    setPage(0);
    setPageSize(DEFAULT_PAGE_SIZE);
    setTotalElements(0);
    setError(null);
    setValidatingKey(null);
    setValidationErrors(null);
    const context = new URLSearchParams();
    for (const key of CONTEXT_PARAMS) {
      const value = searchParams.get(key);
      if (value) context.set(key, value);
    }
    setSearchParams(context, { replace: true });
  };

  const setField = (patch: Partial<Filters>) => setFilters((f) => ({ ...f, ...patch }));

  // Map the single date range onto the proc's date-type-specific parameters.
  const dateParams = (): Partial<OpeningSearchQuery> => {
    const { dateType, dateFrom, dateTo } = filters;
    if (!dateType) return {};
    const from = dateFrom.trim() || undefined;
    const to = dateTo.trim() || undefined;
    switch (dateType) {
      case 'Disturbance':
        return { dateType, distStartDate: from, distEndDate: to };
      case 'Regen Delay':
        return { dateType, dueLateDateFrom: from, dueLateDateTo: to };
      case 'Free Growing':
        return { dateType, fgDueEarlyDate: from, fgDueLateDate: to };
      case 'Update':
        return { dateType, updateDateFrom: from, updateDateTo: to };
      default:
        return {};
    }
  };

  const buildQuery = (targetPage: number, targetSize: number): OpeningSearchQuery => {
    const tv = (s: string) => s.trim() || undefined;
    return {
      orgUnit,
      forestFileId: tv(filters.forestFileId),
      cuttingPermitId: tv(filters.cuttingPermitId),
      cutBlockId: tv(filters.cutBlockId),
      openingId: tv(filters.openingId),
      timberMark: tv(filters.timberMark),
      licenseeOpeningId: tv(filters.licenseeOpeningId),
      openingNumber1: tv(filters.openingNumber1),
      openingNumber2: tv(filters.openingNumber2),
      openingNumber3: tv(filters.openingNumber3),
      openingNumber4: tv(filters.openingNumber4),
      clientNumber: tv(filters.clientNumber),
      blockStatusSt: tv(filters.blockStatusSt),
      openCategoryCode: tv(filters.openCategoryCode),
      openingStatusCode: tv(filters.openingStatusCode),
      sortBy: filters.sortBy || undefined,
      includeAllP87Ind: filters.includeAllP87Ind ? 'Y' : undefined,
      ...dateParams(),
      pageNumber: targetPage,
      pageSize: targetSize,
    };
  };

  /** The current search as query params: targeting context, non-empty filters, and the page. */
  const searchToParams = (targetPage: number, targetSize: number): URLSearchParams => {
    const params = new URLSearchParams();
    for (const key of CONTEXT_PARAMS) {
      const value = searchParams.get(key);
      if (value) params.set(key, value);
    }
    for (const [key, value] of Object.entries(filters)) {
      // Only what the user actually set — an empty filter in the URL is noise, and `false` for the
      // checkbox is its default.
      if (value !== '' && value !== false) params.set(key, String(value));
    }
    if (clientName) params.set('clientName', clientName);
    params.set('page', String(targetPage));
    params.set('size', String(targetSize));
    return params;
  };

  // Fetch one page. Search resets to page 0; the Pagination control passes the target page/size.
  /**
   * Refuses to search while the client field holds an unresolved term — see the same guard on
   * Checklist Search. An absent filter silently widens the search rather than narrowing it.
   */
  const handleSearch = () => {
    if (isClientTermUnresolved(clientTerm, clientName, filters.clientNumber)) {
      setShowClientError(true);
      return;
    }
    void runSearch(0);
  };

  const runSearch = async (targetPage: number, targetSize = pageSize) => {
    setLoading(true);
    setError(null);
    setValidationErrors(null);
    try {
      const data = await API.acceptedSites.searchOpenings(buildQuery(targetPage, targetSize));
      setResults(data.content);
      setTotalElements(data.totalElements);
      setPage(data.pageNumber);
      setPageSize(data.pageSize);
      // `replace`, not push: a search is not a navigation step. Pushing would make the browser Back
      // button walk backwards through every search the user ran before leaving the page.
      setSearchParams(searchToParams(data.pageNumber, data.pageSize), { replace: true });
    } catch (err) {
      setResults(null);
      setError(apiErrorMessage(err, 'The opening search failed. Please try again.'));
    } finally {
      setLoading(false);
    }
  };

  const target = async (opening: OpeningSearchResult) => {
    setValidatingKey(rowKey(opening));
    setValidationErrors(null);
    try {
      const result = await API.acceptedSites.validateTargetedSite(opening.openingId, orgUnit);
      if (result.valid) {
        // Hand off to the site-detail create flow with the opening + targeting context.
        const params = new URLSearchParams({ openingId: opening.openingId, orgUnit, year });
        navigate(`/site-detail/new?${params.toString()}`);
      } else {
        setValidationErrors(result.messages);
      }
    } catch (err) {
      setValidationErrors([
        apiErrorMessage(err, "We couldn't validate that opening. Please try again."),
      ]);
    } finally {
      setValidatingKey(null);
    }
  };

  return (
    <Grid fullWidth className="default-grid add-target-site-grid">
      <Column sm={4} md={8} lg={16}>
        <div className="add-target-site__header">
          <button
            type="button"
            className="add-target-site__back"
            onClick={() => navigate(-1)}
            aria-label="Back"
          >
            <ArrowLeft /> Back
          </button>
          <h1>Add target site</h1>
          <p className="add-target-site__subtitle">
            District {orgUnit}
            {orgUnitName ? ` - ${orgUnitName}` : ''}
          </p>
        </div>

        <p className="opening-search__intro">
          Search for an opening to target for evaluation, then select it. Only openings in this
          district that aren&apos;t in an active harvest status can be targeted.
        </p>

        <div className="opening-search__filters">
          <TextInput
            id="opening-search-licence"
            labelText="Licence"
            value={filters.forestFileId}
            onChange={(e) => setField({ forestFileId: e.target.value })}
          />
          <TextInput
            id="opening-search-cp"
            labelText="Cutting permit"
            value={filters.cuttingPermitId}
            onChange={(e) => setField({ cuttingPermitId: e.target.value })}
          />
          <TextInput
            id="opening-search-timber-mark"
            labelText="Timber mark"
            value={filters.timberMark}
            onChange={(e) => setField({ timberMark: e.target.value })}
          />
          <TextInput
            id="opening-search-block"
            labelText="Cut block"
            value={filters.cutBlockId}
            onChange={(e) => setField({ cutBlockId: e.target.value })}
          />
          <TextInput
            id="opening-search-opening"
            labelText="Opening ID"
            value={filters.openingId}
            onChange={(e) => setField({ openingId: e.target.value })}
          />
          <TextInput
            id="opening-search-licensee-opening"
            labelText="Licensee opening ID"
            value={filters.licenseeOpeningId}
            onChange={(e) => setField({ licenseeOpeningId: e.target.value })}
          />
          <div className="opening-search__client">
            <ClientCombo
              id="opening-search-client"
              titleText="Client"
              selectedLabel={clientName}
              onSelect={(clientNumber, selectedClientName) => {
                setField({ clientNumber });
                setClientName(selectedClientName);
                setShowClientError(false);
              }}
              onTermChange={(term) => {
                setClientTerm(term);
                if (showClientError) setShowClientError(false);
              }}
              invalid={showClientError}
              invalidText={CLIENT_UNRESOLVED_MESSAGE}
            />
          </div>
          <Select
            id="opening-search-block-status"
            labelText="Block status"
            value={filters.blockStatusSt}
            onChange={(e) => setField({ blockStatusSt: e.target.value })}
          >
            <SelectItem value="" text="All" />
            {blockStatuses.map((c) => (
              <SelectItem key={c.code} value={c.code} text={c.description || c.code} />
            ))}
          </Select>
          <Select
            id="opening-search-category"
            labelText="Open category"
            value={filters.openCategoryCode}
            onChange={(e) => setField({ openCategoryCode: e.target.value })}
          >
            <SelectItem value="" text="All" />
            {openCategories.map((c) => (
              <SelectItem key={c.code} value={c.code} text={c.description || c.code} />
            ))}
          </Select>
          <Select
            id="opening-search-status"
            labelText="Opening status"
            value={filters.openingStatusCode}
            onChange={(e) => setField({ openingStatusCode: e.target.value })}
          >
            <SelectItem value="" text="All" />
            {openingStatuses.map((c) => (
              <SelectItem key={c.code} value={c.code} text={c.description || c.code} />
            ))}
          </Select>
        </div>

        <div className="opening-search__filters">
          {/* Opening number: a normal labelled field whose input is four mapsheet segment boxes
              (sized 4/3/3/4). Hidden per-box labels are for screen readers; placeholders show the
              format (093A 045 1 23). */}
          <div className="opening-search__opening">
            <span className="cds--label">Opening number</span>
            <div className="opening-search__opening-parts">
              <TextInput
                id="opening-search-on1"
                labelText="Mapsheet grid and letter"
                hideLabel
                maxLength={4}
                placeholder="093A"
                value={filters.openingNumber1}
                onChange={(e) => setField({ openingNumber1: e.target.value })}
              />
              <TextInput
                id="opening-search-on2"
                labelText="Mapsheet square"
                hideLabel
                maxLength={3}
                placeholder="045"
                value={filters.openingNumber2}
                onChange={(e) => setField({ openingNumber2: e.target.value })}
              />
              <TextInput
                id="opening-search-on3"
                labelText="Quad and sub-quad"
                hideLabel
                maxLength={3}
                placeholder="1"
                value={filters.openingNumber3}
                onChange={(e) => setField({ openingNumber3: e.target.value })}
              />
              <TextInput
                id="opening-search-on4"
                labelText="Opening number"
                hideLabel
                maxLength={4}
                placeholder="23"
                value={filters.openingNumber4}
                onChange={(e) => setField({ openingNumber4: e.target.value })}
              />
            </div>
          </div>
          <Select
            id="opening-search-date-type"
            labelText="Date type"
            value={filters.dateType}
            onChange={(e) => setField({ dateType: e.target.value })}
          >
            <SelectItem value="" text="None" />
            {DATE_TYPES.map((type) => (
              <SelectItem key={type} value={type} text={type} />
            ))}
          </Select>
          {/* Shown always; the dates only filter once a Date type is chosen. */}
          <DateBox
            id="opening-search-date-from"
            labelText="From"
            value={filters.dateFrom}
            onChange={(v) => setField({ dateFrom: v })}
          />
          <DateBox
            id="opening-search-date-to"
            labelText="To"
            value={filters.dateTo}
            onChange={(v) => setField({ dateTo: v })}
          />
          <Select
            id="opening-search-sort"
            labelText="Sort by"
            value={filters.sortBy}
            onChange={(e) => setField({ sortBy: e.target.value })}
          >
            <SelectItem value="O" text="Opening" />
            <SelectItem value="L" text="Licence" />
          </Select>
          {/* A filter like any other, so it sits in the grid rather than beside the buttons. The
              wrapper gives it the same top offset as a labelled field, so it lines up with the
              inputs on its row instead of with their labels. */}
          <div className="opening-search__checkbox">
            <Checkbox
              id="opening-search-p87"
              labelText="Include all P87's"
              checked={filters.includeAllP87Ind}
              onChange={(_evt, { checked }) => setField({ includeAllP87Ind: checked })}
            />
          </div>
        </div>

        <div className="opening-search__actions">
          <Button onClick={handleSearch} disabled={loading}>
            Search
          </Button>
          <Button kind="ghost" onClick={reset} disabled={loading}>
            Clear
          </Button>
        </div>

        {error && (
          <InlineNotification
            kind="error"
            title="Search failed"
            subtitle={error}
            hideCloseButton
            lowContrast
          />
        )}

        {validationErrors && validationErrors.length > 0 && (
          <InlineNotification
            kind="error"
            title="This opening can't be targeted"
            subtitle={validationErrors.join(' ')}
            hideCloseButton
            lowContrast
          />
        )}

        {loading && <InlineLoading description="Searching openings…" />}

        {results && !loading && results.length === 0 && (
          <p className="opening-search__empty">No openings match those filters in this district.</p>
        )}

        {results && results.length > 0 && (
          <Table size="sm" className="opening-search__table">
            <TableHead>
              <TableRow>
                <TableHeader>Opening ID</TableHeader>
                <TableHeader>Opening</TableHeader>
                <TableHeader>Licence</TableHeader>
                <TableHeader>Cutting permit</TableHeader>
                <TableHeader>Timber mark</TableHeader>
                <TableHeader>Cut block</TableHeader>
                <TableHeader>Category</TableHeader>
                <TableHeader>Status</TableHeader>
                <TableHeader>Gross area (ha)</TableHeader>
                {/* No visible header — the row action speaks for itself. aria-label keeps the
                    column announced, matching the other action columns in the app. */}
                <TableHeader className="opening-search__col-actions" aria-label="Actions" />
              </TableRow>
            </TableHead>
            <TableBody>
              {results.map((opening) => {
                // Null when there is no opening id to link to; the cell then shows plain text.
                const silvaHref = silvaOpeningUrl(opening.openingId, user?.idpProvider);
                return (
                  <TableRow key={rowKey(opening)}>
                    <TableCell>
                      {silvaHref ? (
                        <ExternalLink href={silvaHref}>{opening.openingId}</ExternalLink>
                      ) : (
                        opening.openingId || '—'
                      )}
                    </TableCell>
                    <TableCell>{opening.openingNumber || '—'}</TableCell>
                    <TableCell>{opening.forestFileId || '—'}</TableCell>
                    <TableCell>{opening.cuttingPermitId || '—'}</TableCell>
                    <TableCell>{opening.timberMark || '—'}</TableCell>
                    <TableCell>{opening.cutBlockId || '—'}</TableCell>
                    <TableCell>{labelFor(openCategories, opening.openCategoryCode)}</TableCell>
                    <TableCell>{labelFor(openingStatuses, opening.openingStatusCode)}</TableCell>
                    <TableCell>{opening.grossArea || '—'}</TableCell>
                    <TableCell className="opening-search__col-actions">
                      {validatingKey === rowKey(opening) ? (
                        <InlineLoading description="Checking…" />
                      ) : (
                        <Button kind="ghost" size="sm" onClick={() => void target(opening)}>
                          Add
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}

        {results && results.length > 0 && (
          <Pagination
            page={page + 1}
            pageSize={pageSize}
            pageSizes={PAGE_SIZE_OPTIONS}
            totalItems={totalElements}
            disabled={loading}
            onChange={({ page: nextPage, pageSize: nextSize }) => {
              // Carbon's page is 1-based; the backend is 0-based. A page-size change resets to page 1
              // so the offset stays valid.
              if (nextSize === pageSize) {
                void runSearch(nextPage - 1, nextSize);
              } else {
                void runSearch(0, nextSize);
              }
            }}
          />
        )}
      </Column>
    </Grid>
  );
};

export default AddTargetSitePage;

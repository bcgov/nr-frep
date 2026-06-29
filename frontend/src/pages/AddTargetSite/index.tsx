import { Add, ArrowLeft, Close, Search } from '@carbon/icons-react';
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
import { useEffect, useState, type FC } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import './addTargetSite.scss';

import ClientSearchModal from '@/components/core/ClientSearchModal';

import type { OpeningSearchQuery, OpeningSearchResult } from '@/types/acceptedSite';
import type { CodeOption } from '@/types/configuration';

import API from '@/services/APIs';

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
 * "Add target site" page — a full port of the legacy SIL56 Opening Tenure Search. Reached from the
 * Accepted Sites page (which supplies the district context via query params). Searches the opening
 * inventory by tenure / opening number / status / dates / client, then validates a chosen opening for
 * targeting (FREP_200_ACCEPTED_SITES.ADD_TARGETED_SITE) and, on success, hands off to the site-detail
 * create flow.
 */
const AddTargetSitePage: FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const orgUnit = searchParams.get('orgUnit') ?? '';
  const orgUnitName = searchParams.get('orgUnitName') ?? '';
  const year = searchParams.get('year') ?? '';

  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);
  const [results, setResults] = useState<OpeningSearchResult[] | null>(null);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [validatingKey, setValidatingKey] = useState<string | null>(null);
  const [validationErrors, setValidationErrors] = useState<string[] | null>(null);
  const [blockStatuses, setBlockStatuses] = useState<CodeOption[]>([]);
  const [openCategories, setOpenCategories] = useState<CodeOption[]>([]);
  const [openingStatuses, setOpeningStatuses] = useState<CodeOption[]>([]);
  // Client filter: searched by name via the lookup, which fills the client number used by the query.
  const [clientLookupOpen, setClientLookupOpen] = useState(false);
  const [clientName, setClientName] = useState('');

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

  const reset = () => {
    setFilters(EMPTY_FILTERS);
    setClientName('');
    setResults(null);
    setPage(0);
    setPageSize(DEFAULT_PAGE_SIZE);
    setTotalElements(0);
    setError(null);
    setValidatingKey(null);
    setValidationErrors(null);
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

  // Fetch one page. Search resets to page 0; the Pagination control passes the target page/size.
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
    } catch (err) {
      setResults(null);
      setError(err instanceof Error ? err.message : 'The opening search failed. Please try again.');
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
        err instanceof Error ? err.message : "We couldn't validate that opening. Please try again.",
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
            <TextInput
              id="opening-search-client"
              labelText="Client"
              placeholder="Use the lookup to search by client name"
              readOnly
              value={clientName}
            />
            <div className="opening-search__client-buttons">
              <Button
                hasIconOnly
                kind="tertiary"
                size="md"
                renderIcon={Search}
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
                    setField({ clientNumber: '' });
                  }}
                />
              )}
            </div>
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
            labelText="From (YYYY-MM-DD)"
            value={filters.dateFrom}
            onChange={(v) => setField({ dateFrom: v })}
          />
          <DateBox
            id="opening-search-date-to"
            labelText="To (YYYY-MM-DD)"
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
        </div>

        <div className="opening-search__actions">
          <Checkbox
            id="opening-search-p87"
            labelText="Include all P87's"
            checked={filters.includeAllP87Ind}
            onChange={(_evt, { checked }) => setField({ includeAllP87Ind: checked })}
          />
          <div className="opening-search__action-buttons">
            <Button onClick={() => void runSearch(0)} disabled={loading}>
              Search
            </Button>
            <Button kind="ghost" onClick={reset} disabled={loading}>
              Clear
            </Button>
          </div>
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
                <TableHeader>Opening</TableHeader>
                <TableHeader>Licence</TableHeader>
                <TableHeader>Cutting permit</TableHeader>
                <TableHeader>Timber mark</TableHeader>
                <TableHeader>Cut block</TableHeader>
                <TableHeader>Category</TableHeader>
                <TableHeader>Status</TableHeader>
                <TableHeader>Gross area (ha)</TableHeader>
                <TableHeader className="opening-search__col-actions">Actions</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {results.map((opening) => (
                <TableRow key={rowKey(opening)}>
                  <TableCell>{opening.openingNumber || opening.openingId}</TableCell>
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
                        <span className="opening-search__add-label">
                          <Add /> Add
                        </span>
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
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

      <ClientSearchModal
        open={clientLookupOpen}
        onClose={() => setClientLookupOpen(false)}
        onSelect={(clientNumber, selectedClientName) => {
          setField({ clientNumber });
          setClientName(selectedClientName);
        }}
      />
    </Grid>
  );
};

export default AddTargetSitePage;

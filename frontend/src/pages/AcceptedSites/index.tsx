import { Add, Map as MapIcon } from '@carbon/icons-react';
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
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';

import PrintableTable from '@/components/core/PrintableTable';
import TableHeaderBar from '@/components/core/TableHeaderBar';
import OpeningMapModal from '@/components/OpeningMapModal';

import type { AcceptedSite } from '@/types/acceptedSite';
import type { MasterListYear, OrgUnit, Protocol } from '@/types/configuration';

import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import API from '@/services/APIs';
import { apiErrorMessage } from '@/utils/apiError';
import { statusLabel, statusTagType } from '@/utils/checklistStatus';
import { formatShortDate } from '@/utils/date';
import { silvaOpeningUrl } from '@/utils/silva';

import './acceptedSites.scss';

const ALL_PROTOCOLS_VALUE = '';

// Maps the record's DB protocol code to the biodiversity checklist route slug. SLB (legacy) and SLR
// (going forward) are the same protocol/page, so both route to /protocol-checklists/slr/:id. CHR has
// its own route slug (handled below). Any other code has no page and is not linked.
const PROTOCOL_TO_PATH: Record<string, 'slr' | undefined> = {
  SLB: 'slr',
  SLR: 'slr',
};

const TABLE_HEADERS = [
  { key: 'checklistStatus', header: 'Status' },
  { key: 'checklistType', header: 'Protocol' },
  { key: 'targeted', header: 'T' },
  { key: 'openingNumber', header: 'Opening' },
  { key: 'openingId', header: 'Opening ID' },
  { key: 'licenceId', header: 'License' },
  { key: 'cuttingPermitId', header: 'Cutting Permit' },
  { key: 'cutBlockId', header: 'Cut block' },
  { key: 'harvestCompleteDate', header: 'Harvest complete' },
  { key: 'mapView', header: '' },
] as const;

function toTableRows(sites: AcceptedSite[]) {
  return sites.map((site) => ({
    id: site.checklistId,
    // Protocol column — same "code - name" format as the Checklist Search page.
    checklistType: `${site.protocolCode} - ${site.protocolName}`,
    targeted: site.targeted ? 'T' : '',
    openingNumber: site.openingNumber,
    openingId: site.openingId,
    licenceId: site.licenceId,
    cuttingPermitId: site.cuttingPermitId,
    cutBlockId: site.cutBlockId,
    harvestCompleteDate: formatShortDate(site.harvestCompleteDate),
    checklistStatus: statusLabel(site.checklistStatusCode, site.checklistStatus),
    statusCode: site.checklistStatusCode,
    protocolCode: site.protocolCode,
    mapView: site.openingId,
  }));
}

type AcceptedSiteRow = ReturnType<typeof toTableRows>[number];

/**
 * The row's checklist link (legacy FREP200 "Checklist" link): CHR opens its own screen, SLB/SLR
 * open the protocol checklist (see {@link PROTOCOL_TO_PATH}); any other protocol has no link.
 */
const checklistLinkFor = (rowMeta: AcceptedSiteRow | undefined): string | undefined => {
  if (!rowMeta) return undefined;
  if (rowMeta.protocolCode === 'CHR') return `/protocol-checklists/chr/${rowMeta.id}`;
  const protoPath = PROTOCOL_TO_PATH[rowMeta.protocolCode];
  return protoPath ? `/protocol-checklists/${protoPath}/${rowMeta.id}` : undefined;
};

const AcceptedSitesPage: FC = () => {
  const { display } = useNotification();
  const navigate = useNavigate();
  const { canEdit, canAnyChr, chrDistricts } = useAuthorization();
  const { user } = useAuth();

  const [masterListYears, setMasterListYears] = useState<MasterListYear[]>([]);
  const [orgUnits, setOrgUnits] = useState<OrgUnit[]>([]);
  const [protocols, setProtocols] = useState<Protocol[]>([]);
  const [configLoading, setConfigLoading] = useState(true);

  // Scope the filter dropdowns to what the user can see (rows are also filtered server-side): a
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

  // Filters are seeded from the URL query string so the browser Back button (e.g. returning from a
  // BIO/CHR checklist) restores the district/year/protocol the user was working in, instead of
  // re-defaulting to the current master list. They're kept in sync with the URL below.
  const [searchParams, setSearchParams] = useSearchParams();
  const [effectiveYear, setEffectiveYear] = useState<string>(() => searchParams.get('year') ?? '');
  const [orgUnit, setOrgUnit] = useState<string>(() => searchParams.get('orgUnit') ?? '');
  const [protocolType, setProtocolType] = useState<string>(
    () => searchParams.get('protocol') ?? ALL_PROTOCOLS_VALUE,
  );

  // Opening whose polygon map is shown in the modal (null = closed).
  const [mapOpeningId, setMapOpeningId] = useState<string | null>(null);
  // "Add target site" opens the opening-search page with the current district context.
  const goToAddTargetSite = () => {
    const params = new URLSearchParams({ orgUnit, year: effectiveYear });
    const name = orgUnits.find((u) => u.orgUnitNo === orgUnit)?.orgUnitName;
    if (name) params.set('orgUnitName', name);
    navigate(`/add-target-site?${params.toString()}`);
  };

  const [sites, setSites] = useState<AcceptedSite[]>([]);
  const [loading, setLoading] = useState(false);
  const [hasError, setHasError] = useState(false);

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
        // Selectable protocols: biodiversity (SLR — the go-forward code) and CHR. SLB is not offered as
        // a separate option; the backend treats the SLR filter as the whole biodiversity family, so
        // historical SLB accepted sites still appear under SLR (and open read-only).
        const IN_SCOPE = new Set(['SLR', 'CHR']);
        setProtocols(fetchedProtocols.filter((p) => IN_SCOPE.has(p.code)));

        // Default only when the filter wasn't already seeded from the URL (a fresh visit), so a Back
        // navigation that carries year/orgUnit in the query string keeps the user's selection.
        const defaultYear = years.find((year) => year.current) ?? years[0];
        if (defaultYear) setEffectiveYear((prev) => prev || defaultYear.effectiveYear);
        // Default to the first district the user can see (a CHR-only user must not default to a
        // district they lack access to, which would just show an empty list).
        const defaultUnits = canEdit
          ? units
          : units.filter((u) => chrDistricts.includes(u.orgUnitCode.toUpperCase()));
        if (defaultUnits[0]) setOrgUnit((prev) => prev || defaultUnits[0].orgUnitNo);
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
    // canEdit/chrDistricts (stable per-user from useAuthorization) drive the default district choice.
  }, [display, canEdit, chrDistricts]);

  // Mirror the active filters into the URL query string (replace, so we don't stack history entries).
  // This is what makes the selection survive a Back navigation from a checklist detail page.
  useEffect(() => {
    if (!effectiveYear && !orgUnit) return; // pre-config: nothing to persist yet
    const next = new URLSearchParams();
    if (effectiveYear) next.set('year', effectiveYear);
    if (orgUnit) next.set('orgUnit', orgUnit);
    if (protocolType) next.set('protocol', protocolType);
    setSearchParams(next, { replace: true });
  }, [effectiveYear, orgUnit, protocolType, setSearchParams]);

  const loadAcceptedSites = useCallback(async () => {
    if (!effectiveYear || !orgUnit) return;

    setLoading(true);
    setHasError(false);

    try {
      const data = await API.acceptedSites.getAcceptedSites({
        effectiveYear,
        orgUnit,
        protocolType: protocolType || undefined,
      });
      setSites(data);
    } catch (err) {
      const message = apiErrorMessage(err);
      display({
        kind: 'error',
        title: "We couldn't load accepted sites",
        subtitle: message,
        timeout: 9000,
      });
      setHasError(true);
      setSites([]);
    } finally {
      setLoading(false);
    }
  }, [display, effectiveYear, orgUnit, protocolType]);

  useEffect(() => {
    void loadAcceptedSites();
  }, [loadAcceptedSites]);

  const tableRows = useMemo(() => toTableRows(sites), [sites]);

  // Extracted from the table render so the per-cell handlers aren't nested >4 functions deep
  // (DataTable render-prop → rows.map → cells.map → onClick). First cell links to the checklist,
  // Opening ID out to SILVA, and the map cell opens the in-app opening map.
  const renderCell = (
    cell: { id: string; value: string; info: { header: string } },
    checklistLink: string | undefined,
    statusCode: string | undefined,
  ) => {
    if (cell.info.header === 'checklistStatus') {
      const label = statusLabel(statusCode, cell.value);
      return (
        <TableCell key={cell.id}>
          {label ? (
            <Tag type={statusTagType(statusCode)} size="sm">
              {label}
            </Tag>
          ) : null}
        </TableCell>
      );
    }
    if (cell.info.header === 'checklistType' && checklistLink) {
      return (
        <TableCell key={cell.id}>
          <RouterLink to={checklistLink}>{cell.value}</RouterLink>
        </TableCell>
      );
    }
    // Opening ID deep-links into SILVA with an idp_hint for the signed-in provider, so the user
    // lands on the opening without a second login. Rows without an opening id stay plain text.
    if (cell.info.header === 'openingId') {
      const href = silvaOpeningUrl(cell.value, user?.idpProvider);
      return (
        <TableCell key={cell.id}>
          {href ? (
            <a href={href} target="_blank" rel="noopener noreferrer">
              {cell.value}
            </a>
          ) : (
            cell.value
          )}
        </TableCell>
      );
    }
    if (cell.info.header === 'mapView') {
      return (
        <TableCell key={cell.id} className="accepted-sites__map-cell">
          <Button
            kind="ghost"
            size="sm"
            hasIconOnly
            renderIcon={MapIcon}
            iconDescription="Site Map"
            tooltipPosition="left"
            className="accepted-sites__map-btn"
            disabled={!cell.value}
            onClick={() => setMapOpeningId(String(cell.value))}
          />
        </TableCell>
      );
    }
    return <TableCell key={cell.id}>{cell.value}</TableCell>;
  };

  // Print-only full list (legacy "Printer Version"): all rows, no map column.
  const printColumns = TABLE_HEADERS.filter((header) => header.key !== 'mapView');
  const selectedOrgUnit = orgUnits.find((unit) => unit.orgUnitNo === orgUnit);
  const printMeta = [
    selectedOrgUnit ? `${selectedOrgUnit.orgUnitCode} — ${selectedOrgUnit.orgUnitName}` : null,
    effectiveYear ? `Master list ${effectiveYear}` : null,
  ]
    .filter(Boolean)
    .join(' — ');

  return (
    <>
      <Grid fullWidth className="default-grid accepted-sites-grid print-hidden">
        <Column sm={4} md={8} lg={16}>
          <div className="accepted-sites__header">
            <h1>Accepted Sites</h1>
          </div>
          <p className="accepted-sites__subtitle">
            Read-only view of accepted sites for the selected master list year, district, and
            protocol.
          </p>
        </Column>

        <Column sm={4} md={8} lg={16}>
          <div className="accepted-sites__filters">
            <Select
              id="accepted-sites-year"
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
              id="accepted-sites-org-unit"
              labelText="Org unit"
              value={orgUnit}
              onChange={(event) => setOrgUnit(event.target.value)}
              disabled={configLoading || districtOptions.length === 0}
            >
              {districtOptions.map((unit) => (
                <SelectItem
                  key={unit.orgUnitNo}
                  value={unit.orgUnitNo}
                  text={`${unit.orgUnitCode} — ${unit.orgUnitName}`}
                />
              ))}
            </Select>
            <Select
              id="accepted-sites-protocol"
              labelText="Protocol"
              value={protocolType}
              onChange={(event) => setProtocolType(event.target.value)}
              disabled={configLoading}
            >
              <SelectItem value={ALL_PROTOCOLS_VALUE} text="All protocols" />
              {protocolOptions.map((protocol) => (
                <SelectItem
                  key={protocol.code}
                  value={protocol.code}
                  text={`${protocol.code} - ${protocol.name}`}
                />
              ))}
            </Select>
            <Button
              kind="secondary"
              onClick={() => void loadAcceptedSites()}
              disabled={loading || configLoading || !effectiveYear || !orgUnit}
            >
              Refresh
            </Button>
          </div>
        </Column>

        <Column sm={4} md={8} lg={16}>
          {(loading || configLoading) && (
            <div aria-busy data-testid="accepted-sites-loading">
              <SkeletonText paragraph lineCount={4} />
            </div>
          )}
          {!loading && !configLoading && hasError && (
            <p data-testid="accepted-sites-error">We couldn&apos;t load accepted sites.</p>
          )}
          {!loading && !configLoading && !hasError && sites.length === 0 && (
            <p data-testid="accepted-sites-empty">No accepted sites match the selected filters.</p>
          )}
          {!loading && !configLoading && !hasError && sites.length > 0 && (
            <DataTable
              rows={tableRows}
              headers={[...TABLE_HEADERS]}
              data-testid="accepted-sites-table"
            >
              {({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
                <TableContainer>
                  <TableHeaderBar
                    title={`Accepted sites — ${tableRows.length} result${
                      tableRows.length === 1 ? '' : 's'
                    }`}
                    actions={
                      <>
                        {/* Add Target Site: enabled once a district is selected (legacy gate). */}
                        <Button
                          kind="tertiary"
                          size="md"
                          renderIcon={Add}
                          onClick={goToAddTargetSite}
                          disabled={configLoading || !orgUnit}
                        >
                          Add target site
                        </Button>
                        <Button
                          kind="tertiary"
                          size="md"
                          onClick={() => globalThis.print()}
                          disabled={loading || configLoading || tableRows.length === 0}
                        >
                          Print
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
                        const rowMeta = tableRows.find((item) => item.id === row.id);
                        const checklistLink = checklistLinkFor(rowMeta);

                        return (
                          <TableRow {...getRowProps({ row })} key={row.id}>
                            {row.cells.map((cell) =>
                              renderCell(cell, checklistLink, rowMeta?.statusCode),
                            )}
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
        <OpeningMapModal openingId={mapOpeningId} onClose={() => setMapOpeningId(null)} />
      </Grid>
      <PrintableTable
        title="Accepted Sites"
        meta={printMeta}
        columns={printColumns}
        rows={tableRows}
      />
    </>
  );
};

export default AcceptedSitesPage;

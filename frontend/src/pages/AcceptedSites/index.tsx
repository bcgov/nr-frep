import { Map as MapIcon } from '@carbon/icons-react';
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
} from '@carbon/react';
import { useCallback, useEffect, useMemo, useState, type FC } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import PrintableTable from '@/components/core/PrintableTable';
import TableHeaderBar from '@/components/core/TableHeaderBar';
import OpeningMapModal from '@/components/OpeningMapModal';

import type { AcceptedSite } from '@/types/acceptedSite';
import type { MasterListYear, OrgUnit, Protocol } from '@/types/configuration';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

import './acceptedSites.scss';

const ALL_PROTOCOLS_VALUE = '';

// Maps a resource-value type code to its checklist route. Codes match the legacy app
// (`Constants.java`: SLB = biodiversity, RIP = riparian, WTR = water, CHR handled separately).
// BIO/WAT aliases kept as a safety net for any friendly-code source.
// Only biodiversity (legacy SLB) has a protocol-checklist page; CHR is handled separately. Riparian
// (RIP) and Water (WTR) are out of scope and have no pages, so their rows are not linked.
const PROTOCOL_TO_PATH: Record<string, 'biodiversity' | undefined> = {
  SLB: 'biodiversity',
  BIO: 'biodiversity',
};

const TABLE_HEADERS = [
  { key: 'checklistType', header: 'Checklist type' },
  { key: 'targeted', header: 'T' },
  { key: 'openingNumber', header: 'Opening' },
  { key: 'openingId', header: 'Opening ID' },
  { key: 'licenceId', header: 'License' },
  { key: 'cuttingPermitId', header: 'Cutting Permit' },
  { key: 'cutBlockId', header: 'Cut block' },
  { key: 'harvestCompleteDate', header: 'Harvest complete' },
  { key: 'checklistStatus', header: 'Status' },
  { key: 'mapView', header: '' },
] as const;

function formatChecklistType(site: AcceptedSite): string {
  if (site.sampleNumber) {
    return `${site.checklistType} (${site.sampleNumber})`;
  }
  return site.checklistType;
}

function toTableRows(sites: AcceptedSite[]) {
  return sites.map((site) => ({
    id: site.checklistId,
    checklistType: formatChecklistType(site),
    targeted: site.targeted ? 'T' : '',
    openingNumber: site.openingNumber,
    openingId: site.openingId,
    licenceId: site.licenceId,
    cuttingPermitId: site.cuttingPermitId,
    cutBlockId: site.cutBlockId,
    harvestCompleteDate: site.harvestCompleteDate,
    checklistStatus: site.checklistStatus,
    statusCode: site.checklistStatusCode,
    protocolCode: site.protocolCode,
    mapView: site.openingId,
  }));
}

const AcceptedSitesPage: FC = () => {
  const { display } = useNotification();

  const [masterListYears, setMasterListYears] = useState<MasterListYear[]>([]);
  const [orgUnits, setOrgUnits] = useState<OrgUnit[]>([]);
  const [protocols, setProtocols] = useState<Protocol[]>([]);
  const [configLoading, setConfigLoading] = useState(true);

  const [effectiveYear, setEffectiveYear] = useState<string>('');
  const [orgUnit, setOrgUnit] = useState<string>('');
  const [protocolType, setProtocolType] = useState<string>(ALL_PROTOCOLS_VALUE);

  // Opening whose polygon map is shown in the modal (null = closed).
  const [mapOpeningId, setMapOpeningId] = useState<string | null>(null);

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
        // Riparian (RIP) and Water (WTR) are out of scope — keep them out of the protocol filter.
        setProtocols(fetchedProtocols.filter((p) => p.code !== 'RIP' && p.code !== 'WTR'));

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
      const message = err instanceof Error ? err.message : 'Unknown error';
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
  // (DataTable render-prop → rows.map → cells.map → onClick). First cell links to the checklist;
  // the map cell opens the in-app opening map.
  const renderCell = (
    cell: { id: string; value: string; info: { header: string } },
    idx: number,
    checklistLink: string | undefined,
  ) => {
    if (idx === 0 && checklistLink) {
      return (
        <TableCell key={cell.id}>
          <RouterLink to={checklistLink}>{cell.value}</RouterLink>
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
              disabled={configLoading || orgUnits.length === 0}
            >
              {orgUnits.map((unit) => (
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
              {protocols.map((protocol) => (
                <SelectItem key={protocol.code} value={protocol.code} text={protocol.name} />
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
                    title="Accepted sites"
                    actions={
                      <Button
                        kind="tertiary"
                        size="md"
                        onClick={() => globalThis.print()}
                        disabled={loading || configLoading || tableRows.length === 0}
                      >
                        Print
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
                        const rowMeta = tableRows.find((item) => item.id === row.id);
                        const isSubmitted = rowMeta?.statusCode === 'SUB';
                        // Open the row's checklist (legacy FREP200 "Checklist" link): CHR → its own
                        // screen, BIO/RIP/WAT → the protocol checklist; otherwise no link.
                        const protoPath = rowMeta
                          ? PROTOCOL_TO_PATH[rowMeta.protocolCode]
                          : undefined;
                        const checklistLink =
                          rowMeta && rowMeta.protocolCode === 'CHR'
                            ? `/chr/checklists/${rowMeta.id}`
                            : protoPath
                              ? `/protocol-checklists/${protoPath}/${rowMeta?.id}`
                              : undefined;

                        return (
                          <TableRow
                            {...getRowProps({ row })}
                            key={row.id}
                            className={isSubmitted ? 'accepted-sites__row--submitted' : undefined}
                          >
                            {row.cells.map((cell, idx) => renderCell(cell, idx, checklistLink))}
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

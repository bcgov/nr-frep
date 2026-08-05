import {
  Button,
  Column,
  DataTable,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
} from '@carbon/react';
import { useEffect, useMemo, useState, type FC, type ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import type { OfflineChecklist } from '@/services/offline/chrDb';

import { useConfirm } from '@/context/confirm/useConfirm';
import { useNotification } from '@/context/notification/useNotification';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';
import API from '@/services/APIs';
import { chrOfflineRepo } from '@/services/offline/chrOfflineRepo';
import { classifyStaleness, isStale, type StalenessVerdict } from '@/services/offline/chrStaleness';
import { apiErrorMessage } from '@/utils/apiError';

// All offline records come from the CHR store (chrOfflineRepo / the "frep-chr" IndexedDB).
const PROTOCOL_LABEL = 'Cultural Heritage';

type StatusDisplay = { label: string; tag: 'red' | 'magenta' | 'green' | 'cool-gray' };

/**
 * Row status: server staleness takes priority (a superseded copy can't be uploaded, so its local
 * sync state is moot), then unverified, then the local sync state. `verdict` is undefined while the
 * server probe is still in flight — fall back to the sync state so the row isn't blank.
 */
const statusDisplay = (dirty: boolean, verdict: StalenessVerdict | undefined): StatusDisplay => {
  if (verdict && isStale(verdict)) return { label: 'Out of date', tag: 'red' };
  if (verdict === 'UNVERIFIED') return { label: 'Unverified', tag: 'cool-gray' };
  return dirty ? { label: 'Unsynced changes', tag: 'magenta' } : { label: 'Synced', tag: 'green' };
};

const TABLE_HEADERS = [
  { key: 'checklist', header: 'Checklist' },
  { key: 'protocol', header: 'Protocol' },
  { key: 'openingId', header: 'Opening ID' },
  { key: 'status', header: 'Status' },
  { key: 'actions', header: '' },
] as const;

type OfflineDataTableCell = { id: string; value: ReactNode; info: { header: string } };

/**
 * Renders a single DataTable cell for the offline list. Extracted to module scope (rather than
 * inlined in the nested row/cell maps) so the "Remove" click handler doesn't sit five closures deep,
 * and so `remove` can be passed by reference instead of a fresh arrow at each cell.
 */
const OfflineChecklistCell: FC<{
  cell: OfflineDataTableCell;
  rowId: string;
  statusTag: StatusDisplay['tag'];
  online: boolean;
  onRemove: (id: string) => void | Promise<void>;
}> = ({ cell, rowId, statusTag, online, onRemove }) => {
  if (cell.info.header === 'checklist') {
    return (
      <TableCell>
        <RouterLink to={`/protocol-checklists/chr/${rowId}`}>{cell.value}</RouterLink>
      </TableCell>
    );
  }
  if (cell.info.header === 'status') {
    return (
      <TableCell>
        <Tag type={statusTag} size="sm">
          {cell.value}
        </Tag>
      </TableCell>
    );
  }
  if (cell.info.header === 'actions') {
    return (
      <TableCell>
        <Button
          size="sm"
          kind="danger--tertiary"
          disabled={!online}
          title={
            online
              ? undefined
              : 'Connect to the internet to remove — this releases the checkout so the checklist can be edited online.'
          }
          onClick={() => void onRemove(rowId)}
        >
          Remove from device
        </Button>
      </TableCell>
    );
  }
  return <TableCell>{cell.value}</TableCell>;
};

/** Lists CHR checklists currently stored offline in this browser, with quick links to open them. */
const ChrOfflineListPage: FC = () => {
  const confirm = useConfirm();
  const { display } = useNotification();
  const online = useOnlineStatus();
  const [records, setRecords] = useState<OfflineChecklist[]>([]);
  // checklistId → staleness verdict from reconciling each offline copy against the server.
  const [verdicts, setVerdicts] = useState<Record<string, StalenessVerdict>>({});

  useEffect(() => {
    let cancelled = false;
    void chrOfflineRepo.listOffline().then((items) => {
      if (!cancelled) setRecords(items);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // Probe each offline copy against the server so the Status column can flag superseded copies. Reuses
  // the full GET (fine at the low offline-copy volume) — see chrStaleness. Offline → all unverified.
  useEffect(() => {
    if (records.length === 0) return undefined;
    if (!online) {
      setVerdicts(Object.fromEntries(records.map((r) => [r.checklistId, 'UNVERIFIED'])));
      return undefined;
    }
    let cancelled = false;
    void Promise.all(
      records.map(async (record): Promise<readonly [string, StalenessVerdict]> => {
        try {
          const server = await API.chrChecklist.getChecklist(record.checklistId);
          return [record.checklistId, classifyStaleness(record.deviceCheckoutGuid, server)];
        } catch (err) {
          const status = (err as { status?: number })?.status;
          return [record.checklistId, status === 404 ? 'GONE' : 'UNVERIFIED'];
        }
      }),
    ).then((entries) => {
      if (!cancelled) setVerdicts(Object.fromEntries(entries));
    });
    return () => {
      cancelled = true;
    };
  }, [records, online]);

  const rows = useMemo(
    () =>
      records.map((record) => {
        const display = statusDisplay(record.dirty, verdicts[record.checklistId]);
        return {
          id: record.checklistId,
          checklist: `Checklist ${record.checklistId}`,
          protocol: PROTOCOL_LABEL,
          openingId: record.checkList.openingID || '—',
          status: display.label,
          statusTag: display.tag,
        };
      }),
    [records, verdicts],
  );

  const remove = async (id: string) => {
    if (
      !(await confirm({
        title: 'Remove from device?',
        message:
          'Remove this offline copy from this device? Any unsynced local changes will be lost.',
        confirmButtonText: 'Remove',
      }))
    )
      return;
    // Release the server checkout (RDO → ACT) first so the online copy isn't stranded read-only. The
    // backend is guid-guarded + idempotent (no-ops for a stale copy). On failure, keep the local copy
    // so it stays recoverable rather than orphaning the checkout. Remove is disabled offline.
    const record = records.find((r) => r.checklistId === id);
    if (record?.deviceCheckoutGuid) {
      try {
        await API.chrChecklist.release(id, record.deviceCheckoutGuid);
      } catch (err) {
        display({
          kind: 'error',
          title: 'Could not release the checkout',
          subtitle: apiErrorMessage(err),
          timeout: 9000,
        });
        return;
      }
    }
    await chrOfflineRepo.remove(id);
    setRecords((prev) => prev.filter((r) => r.checklistId !== id));
  };

  return (
    <Grid fullWidth className="default-grid">
      <Column sm={4} md={8} lg={16}>
        <h1>Offline checklists</h1>
        <p>Checklists saved on this device for offline editing.</p>
      </Column>
      <Column sm={4} md={8} lg={16}>
        {rows.length === 0 ? (
          <p>No checklists are stored offline.</p>
        ) : (
          <DataTable
            rows={rows}
            headers={[...TABLE_HEADERS]}
            data-testid="offline-checklists-table"
          >
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
                      const meta = rows.find((item) => item.id === row.id);
                      return (
                        <TableRow {...getRowProps({ row })} key={row.id}>
                          {row.cells.map((cell) => (
                            <OfflineChecklistCell
                              key={cell.id}
                              cell={cell}
                              rowId={row.id}
                              statusTag={meta?.statusTag ?? 'green'}
                              online={online}
                              onRemove={remove}
                            />
                          ))}
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
    </Grid>
  );
};

export default ChrOfflineListPage;

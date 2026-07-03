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
import { useEffect, useMemo, useState, type FC } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import type { OfflineChecklist } from '@/services/offline/chrDb';

import { useConfirm } from '@/context/confirm/useConfirm';
import { chrOfflineRepo } from '@/services/offline/chrOfflineRepo';

// All offline records come from the CHR store (chrOfflineRepo / the "frep-chr" IndexedDB).
const PROTOCOL_LABEL = 'Cultural Heritage';

const TABLE_HEADERS = [
  { key: 'checklist', header: 'Checklist' },
  { key: 'protocol', header: 'Protocol' },
  { key: 'openingId', header: 'Opening ID' },
  { key: 'status', header: 'Status' },
  { key: 'actions', header: '' },
] as const;

/** Lists CHR checklists currently stored offline in this browser, with quick links to open them. */
const ChrOfflineListPage: FC = () => {
  const confirm = useConfirm();
  const [records, setRecords] = useState<OfflineChecklist[]>([]);

  useEffect(() => {
    let cancelled = false;
    void chrOfflineRepo.listOffline().then((items) => {
      if (!cancelled) setRecords(items);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const rows = useMemo(
    () =>
      records.map((record) => ({
        id: record.checklistId,
        checklist: `Checklist ${record.checklistId}`,
        protocol: PROTOCOL_LABEL,
        openingId: record.checkList.openingID || '—',
        status: record.dirty ? 'Unsynced changes' : 'Synced',
        dirty: record.dirty,
      })),
    [records],
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
                          {row.cells.map((cell) => {
                            if (cell.info.header === 'checklist') {
                              return (
                                <TableCell key={cell.id}>
                                  <RouterLink to={`/protocol-checklists/chr/${row.id}`}>
                                    {cell.value}
                                  </RouterLink>
                                </TableCell>
                              );
                            }
                            if (cell.info.header === 'status') {
                              return (
                                <TableCell key={cell.id}>
                                  <Tag type={meta?.dirty ? 'magenta' : 'green'} size="sm">
                                    {cell.value}
                                  </Tag>
                                </TableCell>
                              );
                            }
                            if (cell.info.header === 'actions') {
                              return (
                                <TableCell key={cell.id}>
                                  <Button
                                    size="sm"
                                    kind="danger--tertiary"
                                    onClick={() => void remove(row.id)}
                                  >
                                    Remove from device
                                  </Button>
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
    </Grid>
  );
};

export default ChrOfflineListPage;

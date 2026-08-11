import {
  Button,
  Column,
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

import { useConfirm } from '@/context/confirm/useConfirm';
import { useNotification } from '@/context/notification/useNotification';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';
import API from '@/services/APIs';
import { bioOfflineRepo } from '@/services/offline/bioOfflineRepo';
import { bioRowStatus, type OfflineRowStatus } from '@/services/offline/bioOfflineStatus';
import { classifyFromCheckoutState, type StalenessVerdict } from '@/services/offline/staleness';
import { apiErrorMessage } from '@/utils/apiError';

import type { OfflineBioChecklist } from '@/services/offline/bioDb';

const PROTOCOL_LABEL = 'Stand Level Retention';

type Row = {
  record: OfflineBioChecklist;
  status: OfflineRowStatus;
  pending: number;
  rejected: number;
};

/** Stand Level Retention checklists held on this device for offline editing. */
const BioOfflineListPage: FC = () => {
  const confirm = useConfirm();
  const { display } = useNotification();
  const online = useOnlineStatus();
  const [records, setRecords] = useState<OfflineBioChecklist[]>([]);
  const [verdicts, setVerdicts] = useState<Record<string, StalenessVerdict>>({});
  const [queueCounts, setQueueCounts] = useState<Record<string, { pending: number; rejected: number }>>({});

  const reload = async () => {
    const items = await bioOfflineRepo.listOffline();
    setRecords(items);
    const counts = await Promise.all(
      items.map(async (record) => {
        const [pending, rejected] = await Promise.all([
          bioOfflineRepo.pendingAttachmentOps(record.checklistId),
          bioOfflineRepo.rejectedAttachmentOps(record.checklistId),
        ]);
        return [record.checklistId, { pending: pending.length, rejected: rejected.length }] as const;
      }),
    );
    setQueueCounts(Object.fromEntries(counts));
  };

  useEffect(() => {
    void reload();
    // Loaded once on mount; every mutation below re-reads explicitly.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /**
   * Reconcile each copy against the server so the Status column can flag a superseded one.
   *
   * Uses the checkout-state read, which compares tokens server-side and answers "do I still hold
   * this?" — so a reclaimed checkout (the most common stale case) is caught without the server ever
   * returning its token to a client.
   */
  useEffect(() => {
    if (records.length === 0) return undefined;
    if (!online) {
      setVerdicts(Object.fromEntries(records.map((r) => [r.checklistId, 'UNVERIFIED' as const])));
      return undefined;
    }
    let cancelled = false;
    void Promise.all(
      records.map(async (record): Promise<readonly [string, StalenessVerdict]> => {
        try {
          const state = await API.protocolChecklist.getCheckoutState(
            record.checklistId, record.deviceCheckoutGuid);
          return [record.checklistId, classifyFromCheckoutState(state)];
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

  const rows = useMemo<Row[]>(
    () =>
      records.map((record) => {
        const counts = queueCounts[record.checklistId] ?? { pending: 0, rejected: 0 };
        return {
          record,
          pending: counts.pending,
          rejected: counts.rejected,
          status: bioRowStatus({
            syncState: record.syncState,
            verdict: verdicts[record.checklistId],
            pendingAttachments: counts.pending,
            rejectedAttachments: counts.rejected,
            conflictReason: record.conflictReason,
          }),
        };
      }),
    [records, verdicts, queueCounts],
  );

  /**
   * Remove a copy from this device.
   *
   * Releases the server checkout first so the online copy isn't left stranded read-only, and — copied
   * deliberately from CHR — **returns early if that fails**, keeping the local copy recoverable
   * rather than orphaning the checkout. The release is idempotent server-side, so a copy the server
   * already reclaimed still removes cleanly.
   */
  const remove = async (record: OfflineBioChecklist) => {
    const unsynced = (queueCounts[record.checklistId]?.pending ?? 0)
      + (queueCounts[record.checklistId]?.rejected ?? 0);
    if (
      !(await confirm({
        title: 'Remove from device?',
        message: unsynced > 0
          ? `This copy still has ${unsynced} file(s) that have not reached the server. Removing it deletes them permanently. Continue?`
          : 'Remove this offline copy from this device? Any unsynced local changes will be lost.',
        confirmButtonText: 'Remove',
      }))
    ) {
      return;
    }

    if (record.deviceCheckoutGuid) {
      try {
        await API.protocolChecklist.releaseCheckout(record.checklistId, record.deviceCheckoutGuid);
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
    await bioOfflineRepo.remove(record.checklistId);
    await reload();
  };

  return (
    <Grid fullWidth className="default-grid">
      <Column sm={4} md={8} lg={16}>
        <h1>Offline checklists</h1>
        <p>Stand Level Retention checklists saved on this device for offline editing.</p>
      </Column>
      <Column sm={4} md={8} lg={16}>
        {rows.length === 0 ? (
          <p>No checklists are stored offline.</p>
        ) : (
          <TableContainer>
            <Table aria-label="Offline Stand Level Retention checklists">
              <TableHead>
                <TableRow>
                  <TableHeader>Checklist</TableHeader>
                  <TableHeader>Protocol</TableHeader>
                  <TableHeader>Status</TableHeader>
                  <TableHeader />
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map(({ record, status }) => (
                  <TableRow key={record.checklistId}>
                    <TableCell>
                      <RouterLink to={`/protocol-checklists/biodiversity/${record.checklistId}`}>
                        {`Checklist ${record.checklistId}`}
                      </RouterLink>
                    </TableCell>
                    <TableCell>{PROTOCOL_LABEL}</TableCell>
                    <TableCell>
                      <Tag type={status.tag}>{status.label}</Tag>
                      {status.detail ? <div className="offline-list__detail">{status.detail}</div> : null}
                    </TableCell>
                    <TableCell>
                      <Button
                        kind="ghost"
                        size="sm"
                        disabled={!online}
                        onClick={() => void remove(record)}
                      >
                        Remove from device
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Column>
    </Grid>
  );
};

export default BioOfflineListPage;

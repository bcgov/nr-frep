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
import { useCallback, useEffect, useMemo, useState, type FC } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import type { OfflineBioChecklist } from '@/services/offline/bioDb';
import type { OfflineChecklist } from '@/services/offline/chrDb';

import { useConfirm } from '@/context/confirm/useConfirm';
import { useNotification } from '@/context/notification/useNotification';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';
import API from '@/services/APIs';
import { bioOfflineRepo } from '@/services/offline/bioOfflineRepo';
import { chrOfflineRepo } from '@/services/offline/chrOfflineRepo';
import {
  bioRowStatus,
  chrRowStatus,
  type OfflineRowStatus,
} from '@/services/offline/offlineRowStatus';
import {
  classifyFromCheckoutState,
  classifyStaleness,
  type StalenessVerdict,
} from '@/services/offline/staleness';
import { apiErrorMessage } from '@/utils/apiError';

/**
 * The single offline list, covering every protocol that can be taken offline.
 *
 * The two protocols keep separate IndexedDB stores (`frep-chr`, `frep-bio`) with genuinely
 * different records — CHR holds a checklist document and a `dirty` flag, SLR holds a graph snapshot,
 * a sync state machine and an attachment queue — so this page reads both and normalises each into a
 * common row rather than pretending one store can hold the other. What it must not do is let either
 * store's failure hide the other's copies: an unreachable store contributes no rows, never an empty
 * page.
 */

type OfflineProtocol = 'CHR' | 'SLR';

const PROTOCOL_LABEL: Record<OfflineProtocol, string> = {
  CHR: 'Cultural Heritage',
  SLR: 'Stand Level Retention',
};

/** Where a row's checklist opens. Distinct per protocol — they are different pages, not one route. */
const detailPath = (protocol: OfflineProtocol, checklistId: string) =>
  protocol === 'CHR'
    ? `/protocol-checklists/chr/${checklistId}`
    : `/protocol-checklists/slr/${checklistId}`;

type OfflineRow = {
  /** Unique across both stores — a CHR and an SLR checklist id are drawn from separate sequences. */
  key: string;
  checklistId: string;
  protocol: OfflineProtocol;
  openingId: string;
  status: OfflineRowStatus;
  updatedAt: number;
  /** Files held only on this device (SLR's attachment queue). Always 0 for CHR. */
  unsyncedFiles: number;
};

const rowKey = (protocol: OfflineProtocol, checklistId: string) => `${protocol}-${checklistId}`;

/** A store that can't be opened contributes nothing rather than failing the whole page. */
const orEmpty = <T,>(promise: Promise<T[]>): Promise<T[]> => promise.catch(() => []);

/** Lists every checklist saved on this device for offline editing, across protocols. */
const OfflineListPage: FC = () => {
  const confirm = useConfirm();
  const { display } = useNotification();
  const online = useOnlineStatus();
  const [chrRecords, setChrRecords] = useState<OfflineChecklist[]>([]);
  const [bioRecords, setBioRecords] = useState<OfflineBioChecklist[]>([]);
  // rowKey → staleness verdict from reconciling each offline copy against the server.
  const [verdicts, setVerdicts] = useState<Record<string, StalenessVerdict>>({});
  // SLR checklistId → attachment queue depth. CHR has no queue.
  const [queueCounts, setQueueCounts] = useState<
    Record<string, { pending: number; rejected: number }>
  >({});

  const reload = useCallback(async () => {
    const [chr, bio] = await Promise.all([
      orEmpty(chrOfflineRepo.listOffline()),
      orEmpty(bioOfflineRepo.listOffline()),
    ]);
    setChrRecords(chr);
    setBioRecords(bio);
    const counts = await Promise.all(
      bio.map(async (record) => {
        const [pending, rejected] = await Promise.all([
          orEmpty(bioOfflineRepo.pendingAttachmentOps(record.checklistId)),
          orEmpty(bioOfflineRepo.rejectedAttachmentOps(record.checklistId)),
        ]);
        return [
          record.checklistId,
          { pending: pending.length, rejected: rejected.length },
        ] as const;
      }),
    );
    setQueueCounts(Object.fromEntries(counts));
  }, []);

  useEffect(() => {
    void reload();
    // Loaded once on mount; every mutation below re-reads explicitly.
  }, [reload]);

  /**
   * Reconcile each copy against the server so the Status column can flag a superseded one.
   *
   * The probe differs by protocol and deliberately stays that way. CHR re-reads the checklist and
   * compares the checkout token client-side; SLR asks the dedicated checkout-state endpoint, which
   * compares server-side and so catches a reclaimed checkout without ever handing a token back to a
   * client. Offline → everything is unverified, and neither endpoint is called.
   */
  useEffect(() => {
    if (chrRecords.length === 0 && bioRecords.length === 0) return undefined;
    if (!online) {
      setVerdicts(
        Object.fromEntries([
          ...chrRecords.map((r) => [rowKey('CHR', r.checklistId), 'UNVERIFIED' as const]),
          ...bioRecords.map((r) => [rowKey('SLR', r.checklistId), 'UNVERIFIED' as const]),
        ]),
      );
      return undefined;
    }
    let cancelled = false;
    const probe = async (
      protocol: OfflineProtocol,
      checklistId: string,
      classify: () => Promise<StalenessVerdict>,
    ): Promise<readonly [string, StalenessVerdict]> => {
      try {
        return [rowKey(protocol, checklistId), await classify()];
      } catch (err) {
        const status = (err as { status?: number })?.status;
        return [rowKey(protocol, checklistId), status === 404 ? 'GONE' : 'UNVERIFIED'];
      }
    };
    void Promise.all([
      ...chrRecords.map((record) =>
        probe('CHR', record.checklistId, async () =>
          classifyStaleness(
            record.deviceCheckoutGuid,
            await API.chrChecklist.getChecklist(record.checklistId),
          ),
        ),
      ),
      ...bioRecords.map((record) =>
        probe('SLR', record.checklistId, async () =>
          classifyFromCheckoutState(
            await API.protocolChecklist.getCheckoutState(
              record.checklistId,
              record.deviceCheckoutGuid,
            ),
          ),
        ),
      ),
    ]).then((entries) => {
      if (!cancelled) setVerdicts(Object.fromEntries(entries));
    });
    return () => {
      cancelled = true;
    };
  }, [chrRecords, bioRecords, online]);

  const rows = useMemo<OfflineRow[]>(() => {
    const chrRows = chrRecords.map<OfflineRow>((record) => ({
      key: rowKey('CHR', record.checklistId),
      checklistId: record.checklistId,
      protocol: 'CHR',
      openingId: record.checkList?.openingID || '—',
      updatedAt: record.updatedAt ?? 0,
      unsyncedFiles: 0,
      status: chrRowStatus({
        dirty: record.dirty,
        verdict: verdicts[rowKey('CHR', record.checklistId)],
      }),
    }));
    const bioRows = bioRecords.map<OfflineRow>((record) => {
      const counts = queueCounts[record.checklistId] ?? { pending: 0, rejected: 0 };
      return {
        key: rowKey('SLR', record.checklistId),
        checklistId: record.checklistId,
        protocol: 'SLR',
        // The header's numeric Opening ID, captured at take-offline time — not the opening *number*,
        // which is a different field the SLR page also shows. Copies taken before this was stored
        // have no value and still read "—".
        openingId: record.openingId || '—',
        updatedAt: record.updatedAt ?? 0,
        unsyncedFiles: counts.pending + counts.rejected,
        status: bioRowStatus({
          syncState: record.syncState,
          verdict: verdicts[rowKey('SLR', record.checklistId)],
          pendingAttachments: counts.pending,
          rejectedAttachments: counts.rejected,
          conflictReason: record.conflictReason,
        }),
      };
    });
    // Most recently touched first, so the copy the user was just working on is at the top.
    return [...chrRows, ...bioRows].sort((a, b) => b.updatedAt - a.updatedAt);
  }, [chrRecords, bioRecords, verdicts, queueCounts]);

  /**
   * Remove a copy from this device.
   *
   * Releases the server checkout first so the online copy isn't left stranded read-only, and
   * **returns early if that fails**, keeping the local copy recoverable rather than orphaning the
   * checkout. Both releases are guid-guarded and idempotent server-side, so a copy the server has
   * already reclaimed still removes cleanly. Disabled while offline — there is no one to release to.
   */
  const remove = async (row: OfflineRow) => {
    const record =
      row.protocol === 'CHR'
        ? chrRecords.find((r) => r.checklistId === row.checklistId)
        : bioRecords.find((r) => r.checklistId === row.checklistId);
    if (
      !(await confirm({
        title: 'Are you sure you want to remove this checklist from your device?',
        // Not a deletion — the checklist stays on the server — so this says what is actually lost
        // rather than borrowing the "permanently deleted" wording. Files that never reached the
        // server are called out by count: they are the only bytes held nowhere else.
        message: (
          <>
            <strong>This offline copy</strong> will be removed from this device. Any changes that
            have not been synced will be lost.
            {row.unsyncedFiles > 0
              ? ` That includes ${row.unsyncedFiles} file(s) that have not reached the server; removing this copy deletes them permanently.`
              : ''}
          </>
        ),
        confirmButtonText: 'Remove',
      }))
    ) {
      return;
    }

    if (record?.deviceCheckoutGuid) {
      try {
        if (row.protocol === 'CHR') {
          await API.chrChecklist.release(row.checklistId, record.deviceCheckoutGuid);
        } else {
          await API.protocolChecklist.releaseCheckout(row.checklistId, record.deviceCheckoutGuid);
        }
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
    if (row.protocol === 'CHR') {
      await chrOfflineRepo.remove(row.checklistId);
    } else {
      await bioOfflineRepo.remove(row.checklistId);
    }
    await reload();
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
          <TableContainer data-testid="offline-checklists-table">
            <Table aria-label="Offline checklists">
              <TableHead>
                <TableRow>
                  <TableHeader>Checklist</TableHeader>
                  <TableHeader>Protocol</TableHeader>
                  <TableHeader>Opening ID</TableHeader>
                  <TableHeader>Status</TableHeader>
                  <TableHeader />
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.key}>
                    <TableCell>
                      <RouterLink to={detailPath(row.protocol, row.checklistId)}>
                        {`Checklist ${row.checklistId}`}
                      </RouterLink>
                    </TableCell>
                    <TableCell>{PROTOCOL_LABEL[row.protocol]}</TableCell>
                    <TableCell>{row.openingId}</TableCell>
                    <TableCell>
                      <Tag type={row.status.tag} size="sm">
                        {row.status.label}
                      </Tag>
                      {row.status.detail ? (
                        <div className="offline-list__detail">{row.status.detail}</div>
                      ) : null}
                    </TableCell>
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
                        onClick={() => void remove(row)}
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

export default OfflineListPage;

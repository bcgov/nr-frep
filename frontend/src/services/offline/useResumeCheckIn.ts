import { useEffect } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';
import { checkInBioChecklist, resumableCheckIns } from '@/services/offline/bioCheckIn';

/**
 * Finish a check-in that was cut short.
 *
 * A check-in is not only interrupted by crashes. `ensureSessionFresh` hard-redirects to the IDIR
 * login when the refresh token has died — which it will have after hours offline — and that takes the
 * whole page with it, mid-flush. The sync state and the queue live in IndexedDB, so the *intent*
 * survives the redirect; without this hook nothing ever acts on it, and the copy sits in
 * `FLUSHING_ATTACHMENTS` looking stuck.
 *
 * Safe to re-run: resuming re-enters the same loop, and the per-op `syncedAt` markers mean nothing
 * already sent goes twice.
 *
 * Runs once per app load, and only while online — a device still in the field should be left alone.
 */
export const useResumeCheckIn = (enabled: boolean): void => {
  const online = useOnlineStatus();
  const { display } = useNotification();

  useEffect(() => {
    if (!enabled || !online) return;
    let cancelled = false;

    void (async () => {
      const stalled = await resumableCheckIns();
      for (const record of stalled) {
        if (cancelled) return;
        try {
          await checkInBioChecklist(record.checklistId);
          if (!cancelled) {
            display({
              kind: 'success',
              title: `Checklist ${record.checklistId} finished checking in`,
              timeout: 6000,
            });
          }
        } catch {
          // Deliberately quiet: the copy is already marked CONFLICT with its reason, and the offline
          // list shows it. A toast on every app load for a copy the user has not asked about would
          // be noise they cannot act on from wherever they happen to be.
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [enabled, online, display]);
};

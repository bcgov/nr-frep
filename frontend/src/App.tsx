import { useEffect, type FC } from 'react';

import SessionTimeout from '@/components/SessionTimeout';
import AppRoutes from '@/routes/AppRoutes';

import { useAuth } from '@/context/auth/useAuth';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';
import { prefetchChrCodeLists } from '@/pages/ChrChecklist/useChrCodeLists';

const App: FC = () => {
  const { isLoggedIn } = useAuth();
  const online = useOnlineStatus();

  /**
   * Put the CHR code lists on disk as soon as there is a session to fetch them with.
   *
   * They are the only thing a device-local checklist still needs the API for, so a device that goes
   * into the field without them has empty dropdowns and cannot give a feature its class or
   * information source. Warming them here rather than when a checklist is taken offline keeps that
   * button as fast as it was — six round trips in front of it were plainly noticeable — and covers
   * every route into offline use, including checkouts started from the list page.
   *
   * Gated on a session: these endpoints require a token, and calling them signed out would take the
   * 401 path. Unawaited, and failures are swallowed inside; lists already cached are skipped, so an
   * online/offline flip costs nothing.
   */
  useEffect(() => {
    if (!isLoggedIn || !online) return;
    void prefetchChrCodeLists();
  }, [isLoggedIn, online]);

  return (
    <>
      {/* Inactivity auto-logout (warns first) — only runs while authenticated. */}
      {isLoggedIn && <SessionTimeout />}
      <AppRoutes />
    </>
  );
};

export default App;

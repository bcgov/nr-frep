import { type FC } from 'react';

import SessionTimeout from '@/components/SessionTimeout';
import AppRoutes from '@/routes/AppRoutes';

import { useAuth } from '@/context/auth/useAuth';
import { useResumeCheckIn } from '@/services/offline/useResumeCheckIn';

const App: FC = () => {
  const { isLoggedIn } = useAuth();
  // Finish any SLR check-in cut short by the IDIR re-login redirect, which takes the page with it
  // mid-flush. Needs a session, so it waits for login.
  useResumeCheckIn(isLoggedIn);
  return (
    <>
      {/* Inactivity auto-logout (warns first) — only runs while authenticated. */}
      {isLoggedIn && <SessionTimeout />}
      <AppRoutes />
    </>
  );
};

export default App;

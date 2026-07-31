import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { type FC } from 'react';

import SessionTimeout from '@/components/SessionTimeout';
import AppRoutes from '@/routes/AppRoutes';

import { useAuth } from '@/context/auth/useAuth';

const App: FC = () => {
  const { isLoggedIn } = useAuth();
  return (
    <>
      {/* Inactivity auto-logout (warns first) — only runs while authenticated. */}
      {isLoggedIn && <SessionTimeout />}
      <AppRoutes />
      <ReactQueryDevtools initialIsOpen={false} buttonPosition="bottom-left" />
    </>
  );
};

export default App;

import { Loading } from '@carbon/react';
import { Suspense, useEffect, useMemo, type FC } from 'react';
import { RouterProvider, createBrowserRouter } from 'react-router-dom';

import { LayoutProvider } from '@/context/layout/LayoutProvider';
import {
  getNoRoleRoutes,
  getOfflineRoutes,
  getProtectedRoutes,
  getPublicRoutes,
} from '@/routes/routePaths';

import { useAuth } from '@/context/auth/useAuth';
import { usePageTitle } from '@/context/pageTitle/usePageTitle';
import { env } from '@/env';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';

/**
 * Top-level router. Switches between three route sets based on auth state:
 * public (Landing, 404), no-role (only /unauthorized), and protected
 * (Dashboard, Projects, etc.). A user who authenticates but lacks both
 * FREP_ADMIN, FREP_EDITOR, or FREP_VIEW_ONLY lands on /unauthorized rather than NotFound.
 */
const AppRoutes: FC = () => {
  const { isLoggedIn, isLoading, user } = useAuth();
  const { setPageTitle } = usePageTitle();
  const online = useOnlineStatus();

  const displayLoading = () => <Loading data-testid="loading" withOverlay={true} />;

  const hasAnyRole = (user?.roles?.length ?? 0) > 0;

  const routesToUse = useMemo(() => {
    // Offline + not logged in: IDIR login can't run, so serve the offline route set (FREP IMS
    // landing + device-local CHR checklists) instead of the public marketing Landing.
    if (!isLoggedIn) return online ? getPublicRoutes() : getOfflineRoutes();
    if (!hasAnyRole) return getNoRoleRoutes();
    return getProtectedRoutes();
  }, [isLoggedIn, hasAnyRole, online]);

  const basename = env.VITE_BASE_PATH || '/';
  const browserRouter = useMemo(
    () => createBrowserRouter(routesToUse, { basename }),
    [routesToUse, basename],
  );

  useEffect(() => {
    const currentRoute = routesToUse.find((route) => route.path === window.location.pathname);
    if (currentRoute) {
      setPageTitle(currentRoute.id || '', 1);
    }
  }, [routesToUse, setPageTitle]);

  if (isLoading) {
    return displayLoading();
  }

  return (
    // Above the router on purpose: each route mounts its own <Layout>, so layout state held inside
    // one would reset on every navigation (the side nav springing back open mid-task). The per-route
    // providers pass through to this one — see LayoutProvider.
    <LayoutProvider>
      <Suspense fallback={displayLoading()}>
        <RouterProvider router={browserRouter} />
      </Suspense>
    </LayoutProvider>
  );
};

export default AppRoutes;

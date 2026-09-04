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
 * FREP_ADMIN or FREP_EDITOR lands on /unauthorized rather than NotFound.
 */
const AppRoutes: FC = () => {
  const { isLoggedIn, isLoading, user } = useAuth();
  const { setPageTitle } = usePageTitle();
  const online = useOnlineStatus();

  const displayLoading = () => <Loading data-testid="loading" withOverlay={true} />;

  const hasAnyRole = (user?.roles?.length ?? 0) > 0;

  // Which of the four sets is in play. Also the RouterProvider key — see below.
  const routeSetId = !isLoggedIn
    ? online
      ? 'public'
      : 'offline'
    : hasAnyRole
      ? 'protected'
      : 'no-role';

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
        {/* Keyed so a change of route set remounts the provider. RouterProvider subscribes to the
            router it is first given and ignores a later one, so swapping the prop alone left the
            previous set's page on screen. Every other transition between sets happens through a
            full page load (the federated logout chain, the OAuth redirect), which hid this — until
            offline sign-out, which has to swap sets in place because a real navigation offline
            depends on the service worker. Only fires when the set changes, never on navigation. */}
        <RouterProvider key={routeSetId} router={browserRouter} />
      </Suspense>
    </LayoutProvider>
  );
};

export default AppRoutes;

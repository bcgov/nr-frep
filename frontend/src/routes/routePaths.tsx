/**
 * Route definitions for the FREP application.
 *
 * PUBLIC_ROUTES  — unauthenticated pages (Landing, 404, Unauthorized).
 * PROTECTED_ROUTES — authenticated pages wrapped in <Layout> (Dashboard).
 *
 * Three route sets correspond to three auth states (selected in AppRoutes):
 *   not logged in            -> getPublicRoutes()
 *   logged in, no FREP role  -> getNoRoleRoutes()
 *   logged in, has role(s)   -> getProtectedRoutes()
 *
 * Exported helpers:
 *   getPublicRoutes()        — returns PUBLIC_ROUTES as-is.
 *   getNoRoleRoutes()        — Layout-wrapped /unauthorized + catch-all redirect to it.
 *   getProtectedRoutes()     — returns PROTECTED_ROUTES with role-restricted routes wrapped in <ProtectedRoute>.
 *   getMenuEntries(roles)    — returns sidebar menu items filtered by role.
 */

import {
  CloudOffline,
  DashboardReference,
  DocumentTasks,
  Document,
  ListChecked,
  Search,
  SettingsAdjust,
  TableSplit,
  Tree,
} from '@carbon/icons-react';
import { Navigate, type RouteObject } from 'react-router-dom';

import Layout from '@/components/Layout';
import AcceptedSitesPage from '@/pages/AcceptedSites';
import ChecklistSearchPage from '@/pages/ChecklistSearch';
import ChrChecklistPage from '@/pages/ChrChecklist';
import ChrOfflineListPage from '@/pages/ChrChecklist/OfflineList';
import DashboardPage from '@/pages/Dashboard';
import GlobalErrorPage from '@/pages/GlobalError';
import LandingPage from '@/pages/Landing';
import MasterListAdminPage from '@/pages/MasterListAdmin';
import NotFoundPage from '@/pages/NotFound';
import ProtocolChecklistPage from '@/pages/ProtocolChecklist';
import RandomListPage from '@/pages/RandomList';
import ReportsPage from '@/pages/Reports';
import RoleErrorPage from '@/pages/RoleError';
import SiteDetailPage from '@/pages/SiteDetail';

import ProtectedRoute from './ProtectedRoute';

import type { ROLE_TYPE } from '@/context/auth/types';

export type RouteDescription = {
  id: string;
  path: string;
  element: React.ReactNode;
  icon?: React.ComponentType;
  isSideMenu: boolean;
  children?: RouteDescription[];
  roles?: ROLE_TYPE[];
} & RouteObject;

export type MenuItem = Pick<RouteDescription, 'id' | 'path' | 'icon'> & {
  children?: MenuItem[];
};

// --- Route arrays --------------------------------------------------------

/** Unauthenticated routes — shown when the user is not logged in. */
export const PUBLIC_ROUTES: RouteDescription[] = [
  {
    path: '/',
    id: 'Landing',
    element: <LandingPage />,
    isSideMenu: false,
  },
  {
    path: '/unauthorized',
    id: 'Unauthorized',
    element: <RoleErrorPage />,
    isSideMenu: false,
  },
  {
    path: '*',
    id: 'Not Found',
    element: <NotFoundPage />,
    isSideMenu: false,
  },
];

/** Authenticated routes — shown when the user is logged in. */
export const PROTECTED_ROUTES: RouteDescription[] = [
  {
    path: '/',
    id: 'RedirectWhileLoggedIn',
    element: <Navigate to="/dashboard" replace />,
    isSideMenu: false,
  },
  {
    path: '/dashboard',
    id: 'Dashboard',
    icon: DashboardReference,
    element: (
      <Layout>
        <DashboardPage />
      </Layout>
    ),
    isSideMenu: true,
  },
  {
    path: '/random-list',
    id: 'District Random List',
    icon: ListChecked,
    element: (
      <Layout>
        <RandomListPage />
      </Layout>
    ),
    isSideMenu: true,
  },
  {
    path: '/accepted-sites',
    id: 'Accepted Sites',
    icon: TableSplit,
    element: (
      <Layout>
        <AcceptedSitesPage />
      </Layout>
    ),
    isSideMenu: true,
  },
  {
    path: '/site-detail/:id',
    id: 'Site Details',
    icon: DocumentTasks,
    element: (
      <Layout>
        <SiteDetailPage />
      </Layout>
    ),
    isSideMenu: false,
  },
  {
    path: '/protocol-checklists/:type/:id',
    id: 'Protocol Checklist',
    icon: Tree,
    element: (
      <Layout>
        <ProtocolChecklistPage />
      </Layout>
    ),
    isSideMenu: false,
  },
  {
    path: '/chr/checklists/:id',
    id: 'CHR Checklist',
    icon: Tree,
    element: (
      <Layout>
        <ChrChecklistPage />
      </Layout>
    ),
    isSideMenu: false,
  },
  {
    path: '/chr/offline',
    id: 'Offline Checklists',
    icon: CloudOffline,
    element: (
      <Layout>
        <ChrOfflineListPage />
      </Layout>
    ),
    isSideMenu: true,
  },
  {
    path: '/search/checklists',
    id: 'Checklist Search',
    icon: Search,
    element: (
      <Layout>
        <ChecklistSearchPage />
      </Layout>
    ),
    isSideMenu: true,
  },
  {
    path: '/reports',
    id: 'Reports',
    icon: Document,
    element: (
      <Layout>
        <ReportsPage />
      </Layout>
    ),
    isSideMenu: true,
  },
  {
    path: '/admin/master-list',
    id: 'Generate Master List',
    icon: SettingsAdjust,
    element: (
      <Layout>
        <MasterListAdminPage />
      </Layout>
    ),
    isSideMenu: true,
    roles: ['FREP_ADMIN'],
  },
  {
    path: '/unauthorized',
    id: 'Unauthorized',
    element: (
      <Layout>
        <RoleErrorPage />
      </Layout>
    ),
    isSideMenu: false,
  },
  {
    path: '*',
    id: 'Not Found',
    element: (
      <Layout>
        <NotFoundPage />
      </Layout>
    ),
    isSideMenu: false,
  },
];

// --- Helpers --------------------------------------------------------------

/** Returns sidebar menu items the user is allowed to see based on their roles. */
export const getMenuEntries = (roles: string[]): MenuItem[] => {
  return PROTECTED_ROUTES.filter((route) => route.isSideMenu)
    .filter((route) => !route.roles || route.roles.some((r) => roles.includes(r)))
    .map(({ id, path, icon }) => ({ id, path, icon }));
};

/** Returns the public (unauthenticated) route array. */
export const getPublicRoutes = (): RouteDescription[] => PUBLIC_ROUTES;

/**
 * Returns the route set for an authenticated user who has no recognized FREP
 * role. They can reach the Layout-wrapped RoleErrorPage and nothing else;
 * every other path redirects to /unauthorized.
 */
export const getNoRoleRoutes = (): RouteDescription[] => [
  {
    path: '/unauthorized',
    id: 'Unauthorized',
    element: (
      <Layout>
        <RoleErrorPage />
      </Layout>
    ),
    isSideMenu: false,
  },
  {
    path: '*',
    id: 'UnauthorizedRedirect',
    element: <Navigate to="/unauthorized" replace />,
    isSideMenu: false,
  },
];

/** Returns the protected route array with role-gated routes wrapped in <ProtectedRoute>. */
export const getProtectedRoutes = (): RouteDescription[] => {
  return PROTECTED_ROUTES.map((route) => ({
    ...route,
    element: route.roles ? (
      <ProtectedRoute roles={route.roles}>{route.element}</ProtectedRoute>
    ) : (
      route.element
    ),
    errorElement: (
      <Layout>
        <GlobalErrorPage />
      </Layout>
    ),
  }));
};

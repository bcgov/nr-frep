import { render, screen } from '@testing-library/react';
import { vi, describe, afterEach, it, expect } from 'vitest';

import PageTitleProvider from '@/context/pageTitle/PageTitleProvider';

import AppRoutes from './AppRoutes';

import * as useAuthModule from '@/context/auth/useAuth';

vi.mock('@/context/auth/useAuth', () => ({
  useAuth: vi.fn(),
}));

// Mutable so the offline case can simulate a device with no connection.
const { onlineRef } = vi.hoisted(() => ({ onlineRef: { current: true } }));
vi.mock('@/hooks/useOnlineStatus', () => ({ useOnlineStatus: () => onlineRef.current }));

vi.mock('@/routes/routePaths', () => ({
  getPublicRoutes: () => [{ path: '/', element: <div>Public Page</div> }],
  getNoRoleRoutes: () => [{ path: '/', element: <div>No Role Page</div> }],
  getProtectedRoutes: () => [
    { path: '/', element: <div>Protected Page</div> },
    { path: '/protocol-checklists/chr/:id', element: <div>Checklist Page</div> },
  ],
  // Mirrors the real offline set: a landing at '/', the checklist route, and a catch-all home.
  getOfflineRoutes: () => [
    { path: '/', element: <div>Offline Landing</div> },
    { path: '/protocol-checklists/chr/:id', element: <div>Offline Checklist Page</div> },
    { path: '*', element: <div>Offline Catch-all</div> },
  ],
}));

describe('AppRoutes', () => {
  afterEach(() => {
    vi.clearAllMocks();
    onlineRef.current = true;
  });

  it('renders loading spinner when auth is loading', () => {
    (useAuthModule.useAuth as ReturnType<typeof vi.fn>).mockReturnValue({
      isLoading: true,
      isLoggedIn: false,
    });

    render(
      <PageTitleProvider>
        <AppRoutes />
      </PageTitleProvider>,
    );

    const status = screen.getByTestId('loading');
    expect(status).toBeTruthy();
    expect(status.textContent?.toLowerCase()).toContain('loading');
  });

  it('renders public routes if not logged in', async () => {
    (useAuthModule.useAuth as ReturnType<typeof vi.fn>).mockReturnValue({
      isLoading: false,
      isLoggedIn: false,
    });

    render(
      <PageTitleProvider>
        <AppRoutes />
      </PageTitleProvider>,
    );

    const content = await screen.findByText('Public Page');
    expect(content).toBeTruthy();
  });

  it('renders protected routes if logged in with a recognized role', async () => {
    (useAuthModule.useAuth as ReturnType<typeof vi.fn>).mockReturnValue({
      isLoading: false,
      isLoggedIn: true,
      user: { roles: ['FREP_EDITOR'] },
    });

    render(
      <PageTitleProvider>
        <AppRoutes />
      </PageTitleProvider>,
    );

    const content = await screen.findByText('Protected Page');
    expect(content).toBeTruthy();
  });

  it('renders no-role routes if logged in without any recognized role', async () => {
    (useAuthModule.useAuth as ReturnType<typeof vi.fn>).mockReturnValue({
      isLoading: false,
      isLoggedIn: true,
      user: { roles: [] },
    });

    render(
      <PageTitleProvider>
        <AppRoutes />
      </PageTitleProvider>,
    );

    const content = await screen.findByText('No Role Page');
    expect(content).toBeTruthy();
  });

  it('follows an offline sign-out from a checklist to the landing, in place', async () => {
    // Offline sign-out clears the user in place — no federated redirect, no reload. RouterProvider
    // keeps the router it was first handed, so without a key the previous set's page stayed on
    // screen and logging out looked like a dead control.
    (useAuthModule.useAuth as ReturnType<typeof vi.fn>).mockReturnValue({
      isLoading: false,
      isLoggedIn: true,
      user: { roles: ['FREP_ADMIN'] },
    });

    onlineRef.current = false; // WiFi off, as in the field
    // Start where the user was: signed in, on a checklist.
    window.history.replaceState({}, '', '/protocol-checklists/chr/1030');
    const { rerender } = render(
      <PageTitleProvider>
        <AppRoutes />
      </PageTitleProvider>,
    );
    expect(await screen.findByText('Checklist Page')).toBeTruthy();

    // Offline sign-out: the URL moves to the landing and the user is cleared, no page load.
    (useAuthModule.useAuth as ReturnType<typeof vi.fn>).mockReturnValue({
      isLoading: false,
      isLoggedIn: false,
      user: undefined,
    });
    window.history.replaceState({}, '', '/');
    rerender(
      <PageTitleProvider>
        <AppRoutes />
      </PageTitleProvider>,
    );

    expect(await screen.findByText('Offline Landing')).toBeTruthy();
    expect(screen.queryByText('Checklist Page')).toBeNull();
  });
});

import { act, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';

import { AuthProvider } from '@/context/auth/AuthProvider';

import { LayoutSideNav } from './index';

// Mutable so each case can set (or clear) the support address; the component reads it per render.
const { envMock } = vi.hoisted(() => ({ envMock: {} as Record<string, string> }));
vi.mock('@/env', () => ({ env: envMock }));

vi.mock('@/context/layout/useLayout', () => ({
  useLayout: () => ({ isSideNavExpanded: true, closeSideNav: () => {} }),
}));

vi.mock('@/context/auth/useAuth', () => ({
  useAuth: () => ({ user: { roles: ['admin'] }, isLoggedIn: true }),
}));

vi.mock('@/routes/routePaths', () => ({
  getOfflineMenuEntries: () => [{ id: 'Offline Checklists', path: '/chr/offline' }],
  getMenuEntries: () => [
    {
      id: 'Dashboard',
      path: '/dashboard',
      isMenuItem: true,
    },
    {
      id: 'Settings',
      path: '/settings',
      isMenuItem: true,
      children: [
        {
          id: 'Profile',
          path: 'profile',
          isMenuItem: true,
        },
      ],
    },
  ],
}));

const renderWithProviders = async (pathname = '/dashboard') => {
  window.history.pushState({}, '', pathname);
  await act(async () =>
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={[pathname]}>
          <LayoutSideNav />
        </MemoryRouter>
      </AuthProvider>,
    ),
  );
};

describe('LayoutSideNav', () => {
  it('renders menu links and menu items', async () => {
    await renderWithProviders('/dashboard');
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Settings')).toBeInTheDocument();
    expect(screen.getByText('Profile')).toBeInTheDocument();
    expect(screen.queryByText('Admin')).not.toBeInTheDocument();
    expect(screen.queryByText('Hidden')).not.toBeInTheDocument();
  });

  it('marks the correct link as active', async () => {
    await renderWithProviders('/settings/profile');
    const profileLink = screen.getByText('Profile').closest('a');
    expect(profileLink).toHaveClass('cds--side-nav__link--current');
  });

  it('offers "Report an issue" when a support mailbox is configured', async () => {
    // The app tells users to contact the FREP help desk when something fails; this is the how.
    envMock.VITE_SUPPORT_EMAIL = 'frep@gov.bc.ca';
    await renderWithProviders();

    const link = screen.getByTestId('side-nav-link-email-support');
    expect(link.getAttribute('href')).toBe('mailto:frep@gov.bc.ca');
    expect(screen.getByText('Support')).toBeTruthy();
  });

  it('hides the link when no mailbox is configured', async () => {
    // Better no link than a mailto: that goes nowhere — there is no in-code default address.
    delete envMock.VITE_SUPPORT_EMAIL;
    await renderWithProviders();

    expect(screen.queryByTestId('side-nav-link-email-support')).toBeNull();
    expect(screen.queryByText('Support')).toBeNull();
  });

  it('ignores a whitespace-only value', async () => {
    envMock.VITE_SUPPORT_EMAIL = '   ';
    await renderWithProviders();

    expect(screen.queryByTestId('side-nav-link-email-support')).toBeNull();
  });
});

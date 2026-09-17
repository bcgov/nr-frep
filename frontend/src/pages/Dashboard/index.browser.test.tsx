import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import DashboardPage from './index';

// Mutable so a case can drop the mailbox; the component reads it per render.
const { envMock } = vi.hoisted(() => ({ envMock: {} as Record<string, string> }));
vi.mock('@/env', () => ({ env: envMock }));

vi.mock('@/context/auth/useAuth', () => ({ useAuth: () => ({ isLoggedIn: true }) }));
vi.mock('@/hooks/useAuthorization', () => ({ useAuthorization: () => ({ isSysAdmin: false }) }));
vi.mock('@/hooks/useOnlineStatus', () => ({ useOnlineStatus: () => true }));

const renderPage = () =>
  render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>,
  );

describe('DashboardPage — welcome banner', () => {
  beforeEach(() => {
    window.localStorage.clear();
    envMock.VITE_SUPPORT_EMAIL = 'frep@gov.bc.ca';
  });
  afterEach(() => window.localStorage.clear());

  it('greets the user and points them at the support mailbox', () => {
    renderPage();

    expect(screen.getByText('Welcome to the new FREP IMS!')).toBeTruthy();
    // The same shared mailbox the side nav's "Report an issue" uses, shown as its own address.
    expect(screen.getByRole('link', { name: 'frep@gov.bc.ca' }).getAttribute('href')).toBe(
      'mailto:frep@gov.bc.ca',
    );
  });

  it('stays closed once it has been dismissed', async () => {
    const { unmount } = renderPage();

    await userEvent.click(screen.getByRole('button', { name: /close notification/i }));
    expect(screen.queryByTestId('dashboard-welcome')).toBeNull();

    // A welcome that returns on every visit to the dashboard is not really dismissible.
    unmount();
    renderPage();
    expect(screen.queryByTestId('dashboard-welcome')).toBeNull();
  });

  it('drops the invitation when no support mailbox is configured', () => {
    // Same rule as the side nav: no mailbox, no mailto: that goes nowhere.
    delete envMock.VITE_SUPPORT_EMAIL;
    renderPage();

    expect(screen.getByText('Welcome to the new FREP IMS!')).toBeTruthy();
    expect(screen.queryByRole('link', { name: /@/ })).toBeNull();
    expect(screen.getByText(/Your feedback helps us improve the system\./)).toBeTruthy();
  });
});

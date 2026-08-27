import { render, screen, fireEvent, act } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import HeaderPanelProfile from './index';

vi.mock('@/components/Layout/AvatarImage', () => ({
  __esModule: true,
  default: ({ userName, size }: { userName: string; size: string }) => (
    <div data-testid="avatar-image">
      {userName}-{size}
    </div>
  ),
}));

const mockToggleTheme = vi.fn();
const mockLogout = vi.fn();
const idirUser = {
  firstName: 'Jane',
  lastName: 'Doe',
  idpProvider: 'IDIR',
  userName: 'jdoe',
  email: 'jane@example.com',
};
let mockUser: typeof idirUser = idirUser;

vi.mock('@/context/auth/useAuth', () => ({
  useAuth: () => ({ logout: mockLogout, user: mockUser }),
}));
vi.mock('@/context/theme/useTheme', () => ({
  useTheme: () => ({ theme: 'g100', toggleTheme: mockToggleTheme }),
}));
let mockOnline = true;
vi.mock('@/hooks/useOnlineStatus', () => ({ useOnlineStatus: () => mockOnline }));

const renderWithProviders = async () => {
  await act(async () => {
    render(<HeaderPanelProfile />);
  });
};

describe('HeaderPanelProfile', () => {
  beforeEach(() => {
    mockUser = idirUser;
    mockOnline = true;
  });

  it('renders user info and avatar', async () => {
    await renderWithProviders();
    expect(screen.getByText('Jane Doe')).toBeInTheDocument();
    // Component renders `<providerLabel>: <userName>` (see HeaderPanelProfile/index.tsx).
    expect(screen.getByText('IDIR: jdoe')).toBeInTheDocument();
    expect(screen.getByText('Email: jane@example.com')).toBeInTheDocument();
    expect(screen.getByTestId('avatar-image')).toHaveTextContent('Jane Doe-large');
  });

  it('labels a BCeID Business user as "Business BCeID"', async () => {
    mockUser = { ...idirUser, idpProvider: 'BCEIDBUSINESS', userName: 'jdoe-bceid' };
    await renderWithProviders();
    expect(screen.getByText('Business BCeID: jdoe-bceid')).toBeInTheDocument();
  });

  it('calls toggleTheme when Change theme is clicked', async () => {
    await renderWithProviders();
    fireEvent.click(screen.getByText('Change theme'));
    expect(mockToggleTheme).toHaveBeenCalled();
  });

  it('calls logout when Log out is clicked', async () => {
    await renderWithProviders();
    fireEvent.click(screen.getByText('Log out'));
    expect(mockLogout).toHaveBeenCalled();
  });

  it('leaves out the identity block offline, keeping the panel usable', async () => {
    // Offline the session can't be refreshed, so a reload leaves no user and the panel used to
    // render "undefined undefined". Nothing here is needed on-device — an offline copy is already
    // checked out to whoever took it.
    mockOnline = false;
    await renderWithProviders();

    expect(screen.queryByText('Jane Doe')).not.toBeInTheDocument();
    expect(screen.queryByText(/Email:/)).not.toBeInTheDocument();
    expect(screen.queryByText(/undefined/)).not.toBeInTheDocument();
    // The actions the panel exists for are still there.
    expect(screen.getByText('Change theme')).toBeInTheDocument();
  });

  it('never prints "undefined" when there is no user', async () => {
    mockUser = undefined as unknown as typeof idirUser;
    await renderWithProviders();

    expect(screen.queryByText(/undefined/)).not.toBeInTheDocument();
  });
});

import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';

import LandingPage from './index';

const mockLogin = vi.fn();
const mockNavigate = vi.fn();
let mockOnline = true;

vi.mock('@/context/auth/useAuth', () => ({
  useAuth: () => ({ login: mockLogin }),
}));
vi.mock('@/context/theme/useTheme', () => ({
  useTheme: () => ({ theme: 'white' }),
}));
vi.mock('@/hooks/useOnlineStatus', () => ({
  useOnlineStatus: () => mockOnline,
}));
vi.mock('@/hooks/useBreakpoint', () => ({
  __esModule: true,
  default: () => 'lg',
}));
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => mockNavigate,
}));

const renderPage = () =>
  render(
    <MemoryRouter>
      <LandingPage />
    </MemoryRouter>,
  );

describe('LandingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockOnline = true;
  });

  it('online: shows both IDIR and BCeID login buttons that trigger the matching provider', () => {
    renderPage();

    const idir = screen.getByTestId('landing-button__idir');
    const bceid = screen.getByTestId('landing-button__bceid');
    expect(idir).toBeInTheDocument();
    expect(bceid).toBeInTheDocument();

    fireEvent.click(idir);
    expect(mockLogin).toHaveBeenCalledWith('idir');

    fireEvent.click(bceid);
    expect(mockLogin).toHaveBeenCalledWith('bceid');
  });

  it('offline: hides login buttons and shows the Get started entry point', () => {
    mockOnline = false;
    renderPage();

    expect(screen.queryByTestId('landing-button__idir')).toBeNull();
    expect(screen.queryByTestId('landing-button__bceid')).toBeNull();

    fireEvent.click(screen.getByTestId('landing-button__offline'));
    expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
  });
});

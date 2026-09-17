import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';

// The button's own geometry is under test, so it needs the global Carbon overrides as well as the
// page styles that ./index brings with it.
import '@/styles/index.scss';

import LandingPage from './index';

import { APP_FULL_NAME } from '@/constants/appName';

const mockLogin = vi.fn();
const mockNavigate = vi.fn();
let mockOnline = true;

// Mutable so a case can clear the mailbox; the component reads it per render.
const { envMock } = vi.hoisted(() => ({ envMock: {} as Record<string, string> }));
vi.mock('@/env', () => ({ env: envMock }));

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
    envMock.VITE_ACCESS_REQUEST_EMAIL = 'frep.access@gov.bc.ca';
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

  it('puts the login icon after its label, against the far edge', () => {
    // These two buttons are the exception to the app-wide "icon leads the label" rule: a sign-in
    // glyph points the way the button takes you, so it trails. The fixed 20rem width means the
    // arrangement has to be stated — `space-between` is what pushes the icon to the edge.
    renderPage();

    const button = screen.getByTestId('landing-button__idir');
    const icon = button.querySelector('svg') as SVGElement;
    const text = Array.from(button.childNodes).find(
      (n) => n.nodeType === Node.TEXT_NODE && n.textContent?.trim(),
    ) as Text;
    const range = document.createRange();
    range.selectNodeContents(text);

    // The icon starts after the label ends — the ordering, which geometry can see.
    expect(icon.getBoundingClientRect().left).toBeGreaterThanOrEqual(range.getBoundingClientRect().right);

    // Asserted on the computed style: the button only reaches its 20rem in the real layout, so in
    // here the gap collapses and geometry cannot tell "against the edge" from "just after the text".
    expect(getComputedStyle(button).justifyContent).toBe('space-between');
  });

  it('spells out the app name under the acronym', () => {
    renderPage();
    expect(screen.getByTestId('landing-subtitle').textContent).toBe(APP_FULL_NAME);
  });

  it('offers "Request access" and names the mailbox in the dialog', () => {
    renderPage();

    fireEvent.click(screen.getByTestId('landing-request-access'));
    // The address is configuration, so the dialog has to read it rather than state one.
    expect(screen.getByRole('link', { name: 'frep.access@gov.bc.ca' }).getAttribute('href')).toBe(
      'mailto:frep.access@gov.bc.ca',
    );
    // CHR editing is granted per district, so the request has to name them.
    expect(screen.getByText(/district\(s\) you need to edit CHR checklists for/)).toBeTruthy();
  });

  it('hides "Request access" when no mailbox is configured', () => {
    // Better no link than one that opens an empty mailto: — same rule as the side nav support link.
    // Note this is its own mailbox, not the support address.
    delete envMock.VITE_ACCESS_REQUEST_EMAIL;
    renderPage();

    expect(screen.queryByTestId('landing-request-access')).toBeNull();
  });

  it('offline: hides login buttons and shows the Get started entry point', () => {
    mockOnline = false;
    renderPage();

    expect(screen.queryByTestId('landing-button__idir')).toBeNull();
    expect(screen.queryByTestId('landing-button__bceid')).toBeNull();

    fireEvent.click(screen.getByTestId('landing-button__offline'));
    expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
    // Requesting access means sending mail, which is not going to work offline either.
    expect(screen.queryByTestId('landing-request-access')).toBeNull();
  });
});

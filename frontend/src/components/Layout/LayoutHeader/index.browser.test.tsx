import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect } from 'vitest';

import { LayoutHeader } from '@/components/Layout/LayoutHeader';
import { AuthProvider } from '@/context/auth/AuthProvider';
import { LayoutProvider } from '@/context/layout/LayoutProvider';
import { PreferenceProvider } from '@/context/preference/PreferenceProvider';
import ThemeProvider from '@/context/theme/ThemeProvider';

import { APP_NAME } from '@/constants/appName';

const renderWithProviders = async () => {
  const qc = new QueryClient();
  await act(async () =>
    render(
      <AuthProvider>
        <QueryClientProvider client={qc}>
          <MemoryRouter>
            <PreferenceProvider>
              <ThemeProvider>
                <LayoutProvider>
                  <LayoutHeader />
                </LayoutProvider>
              </ThemeProvider>
            </PreferenceProvider>
          </MemoryRouter>
        </QueryClientProvider>
      </AuthProvider>,
    ),
  );
};

describe('LayoutHeader', () => {
  it('renders the header with the app name', async () => {
    await renderWithProviders();
    const header = await screen.findByTestId('bc-header__header');
    expect(header).toBeInTheDocument();

    const title = await screen.findByText(APP_NAME);
    expect(title).toBeInTheDocument();
  });

  it('toggles side nav when menu button is clicked', async () => {
    await renderWithProviders();

    const toggleButton = await screen.findByLabelText(/open menu/i);
    expect(toggleButton).toBeInTheDocument();

    fireEvent.click(toggleButton);

    // After toggling once, aria-label should change to "Close menu"
    expect(screen.getByLabelText(/close menu/i)).toBeInTheDocument();
  });

  /**
   * Carbon's `--active` treatment for a header action is a light panel background with a dark icon,
   * which rendered the open-state X as a grey box punched into the blue bar. The header colour never
   * changes, so the button stays transparent and the icon white — matching FSPTS. Asserted on the
   * computed style because it is a CSS override of a Carbon default: the class stays exactly the
   * same whether or not our rule wins.
   */
  it('keeps the menu toggle transparent with a white icon while open', async () => {
    await renderWithProviders();
    fireEvent.click(await screen.findByLabelText(/open menu/i));

    const style = getComputedStyle(screen.getByLabelText(/close menu/i));
    expect(style.backgroundColor).toBe('rgba(0, 0, 0, 0)');
    expect(style.color).toBe('rgb(255, 255, 255)');
  });
});

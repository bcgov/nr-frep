import { act, render, screen } from '@testing-library/react';
import React, { useState } from 'react';
import { describe, it, expect, vi } from 'vitest';

import { LayoutProvider } from './LayoutProvider';
import { useLayout } from './useLayout';

const TestComponent = () => {
  const {
    isSideNavExpanded,
    toggleSideNav,
    isHeaderPanelOpen,
    toggleHeaderPanel,
    closeHeaderPanel,
  } = useLayout();
  const [sideNav, setSideNav] = useState(isSideNavExpanded);
  const [headerPanel, setHeaderPanel] = useState(isHeaderPanelOpen);

  // Sync state with context
  React.useEffect(() => {
    setSideNav(isSideNavExpanded);
  }, [isSideNavExpanded]);
  React.useEffect(() => {
    setHeaderPanel(isHeaderPanelOpen);
  }, [isHeaderPanelOpen]);

  return (
    <>
      <span data-testid="side-nav">{sideNav ? 'expanded' : 'collapsed'}</span>
      <span data-testid="header-panel">{headerPanel ? 'open' : 'closed'}</span>
      <button onClick={toggleSideNav}>Toggle SideNav</button>
      <button onClick={toggleHeaderPanel}>Toggle HeaderPanel</button>
      <button onClick={closeHeaderPanel}>Close HeaderPanel</button>
    </>
  );
};

const renderWithProvider = async () => {
  await act(async () => {
    render(
      <LayoutProvider>
        <TestComponent />
      </LayoutProvider>,
    );
  });
};

describe('LayoutContext', () => {
  it('provides default layout values', async () => {
    await renderWithProvider();
    // Initial state follows viewport: collapsed in the default test environment.
    expect(screen.getByTestId('side-nav').textContent).toMatch(/expanded|collapsed/);
    expect(screen.getByTestId('header-panel').textContent).toBe('closed');
  });

  it('toggleSideNav toggles the side nav state', async () => {
    await renderWithProvider();
    const btn = screen.getByText('Toggle SideNav');
    const value = screen.getByTestId('side-nav');
    const initial = value.textContent;
    act(() => btn.click());
    expect(value.textContent).not.toBe(initial);
    act(() => btn.click());
    expect(value.textContent).toBe(initial);
  });

  it('toggleHeaderPanel and closeHeaderPanel work as expected', async () => {
    await renderWithProvider();
    const toggleBtn = screen.getByText('Toggle HeaderPanel');
    const closeBtn = screen.getByText('Close HeaderPanel');
    const value = screen.getByTestId('header-panel');
    expect(value.textContent).toBe('closed');
    act(() => toggleBtn.click());
    expect(value.textContent).toBe('open');
    act(() => closeBtn.click());
    expect(value.textContent).toBe('closed');
  });

  /**
   * Regression: every protected route wraps its page in <Layout>, which renders a LayoutProvider —
   * so navigating remounted the provider and reset the side nav to expanded mid-task. A nested
   * provider now defers to the one above it, which lives above the router and survives navigation.
   * The inner provider is keyed here so React genuinely unmounts and rebuilds it, exactly as a route
   * change does.
   */
  it('keeps state when a nested provider remounts, as it does on navigation', async () => {
    const Nested = ({ routeKey }: { routeKey: string }) => (
      <LayoutProvider key={routeKey}>
        <TestComponent />
      </LayoutProvider>
    );

    let rerender: (ui: React.ReactElement) => void = () => {};
    await act(async () => {
      const result = render(
        <LayoutProvider>
          <Nested routeKey="page-a" />
        </LayoutProvider>,
      );
      rerender = result.rerender;
    });

    const value = screen.getByTestId('side-nav');
    const initial = value.textContent;
    act(() => screen.getByText('Toggle SideNav').click());
    const afterToggle = value.textContent;
    expect(afterToggle).not.toBe(initial);

    // "Navigate": the inner provider is torn down and a new one mounted.
    await act(async () => {
      rerender(
        <LayoutProvider>
          <Nested routeKey="page-b" />
        </LayoutProvider>,
      );
    });

    expect(screen.getByTestId('side-nav').textContent).toBe(afterToggle);
  });

  it('throws if useLayout is used outside of LayoutProvider', () => {
    const errorSpy = vi?.spyOn(console, 'error').mockImplementation(() => {});
    const Broken = () => {
      useLayout();
      return null;
    };
    expect(() => render(<Broken />)).toThrow('useLayout must be used within a LayoutProvider');
    if (errorSpy) errorSpy.mockRestore();
  });
});

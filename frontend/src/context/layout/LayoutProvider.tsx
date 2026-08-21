import { useContext, useEffect, useState } from 'react';

import useBreakpoint from '@/hooks/useBreakpoint';

import { LayoutContext } from './LayoutContext';

/** Owns the layout state. Rendered only when nothing above already provides it. */
const LayoutStateProvider = ({ children }: { children: React.ReactNode }) => {
  const breakpoint = useBreakpoint();
  // Only the mobile/desktop split matters. Keyed on the boolean rather than the breakpoint name so
  // resizing lg → xlg doesn't re-open a nav the user deliberately collapsed; crossing into (or out
  // of) mobile still resets it, which is the one case where the default should win.
  const isMobile = breakpoint === 'sm' || breakpoint === 'md';

  const [isSideNavExpanded, setSideNavExpanded] = useState(!isMobile);
  const [isHeaderPanelOpen, setHeaderPanelOpen] = useState(false);

  useEffect(() => {
    setSideNavExpanded(!isMobile);
  }, [isMobile]);

  return (
    <LayoutContext.Provider
      value={{
        isSideNavExpanded,
        toggleSideNav: () => setSideNavExpanded((prev) => !prev),
        closeSideNav: () => setSideNavExpanded(false),
        isHeaderPanelOpen,
        toggleHeaderPanel: () => setHeaderPanelOpen((prev) => !prev),
        closeHeaderPanel: () => setHeaderPanelOpen(false),
      }}
    >
      {children}
    </LayoutContext.Provider>
  );
};

/**
 * Layout state, held at the outermost provider.
 *
 * Every protected route wraps its page in `<Layout>`, which renders one of these — so a provider
 * that always owned state would be torn down and rebuilt on each navigation, resetting the side nav
 * to expanded and closing the header panel. Nesting is therefore a pass-through: the provider above
 * the router keeps the state, and the per-route ones defer to it. `Layout` stays self-sufficient
 * when rendered on its own (tests, Storybook), where it is the outermost provider and owns the state
 * itself.
 */
export const LayoutProvider = ({ children }: { children: React.ReactNode }) => {
  const existing = useContext(LayoutContext);
  if (existing) return <>{children}</>;
  return <LayoutStateProvider>{children}</LayoutStateProvider>;
};

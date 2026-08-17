import { Email } from '@carbon/icons-react';
import { SideNav, SideNavItems, SideNavLink, SideNavMenu, SideNavMenuItem } from '@carbon/react';
import { type FC } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { getMenuEntries, getOfflineMenuEntries, type MenuItem } from '@/routes/routePaths';

import { useAuth } from '@/context/auth/useAuth';
import { useLayout } from '@/context/layout/useLayout';
import { env } from '@/env';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';

import './index.scss';

export const LayoutSideNav: FC = () => {
  /**
   * Shared mailbox behind "Report an issue". Configuration, not code: set from the SUPPORT_EMAIL
   * GitHub variable via VITE_SUPPORT_EMAIL, so the address can change without a code change — and
   * because there is no FREP support address in the codebase to hard-code. The link is hidden
   * entirely when it is unset, rather than shipping a mailto: that goes nowhere.
   *
   * Read per render, not once at module load: `env` merges values injected into window.config at
   * container start, and reading it here keeps the component testable without module resets.
   */
  const supportEmail = env.VITE_SUPPORT_EMAIL?.trim() ?? '';
  const { isSideNavExpanded } = useLayout();
  const location = useLocation();
  const { user, isLoggedIn } = useAuth();
  const online = useOnlineStatus();

  // Offline (or logged out): the server-backed screens can't load, so the side nav shows only the
  // offline-capable routes.
  const menuEntries =
    online && isLoggedIn ? getMenuEntries(user?.roles || []) : getOfflineMenuEntries();

  const renderIcon = (route: MenuItem) => {
    const Icon = route.icon;
    return (
      <div className="cds--side-nav__icon">
        {Icon ? <Icon /> : null}
        <span className="cds--side-nav__link-text">{route.id}</span>
      </div>
    );
  };

  const renderMenuLink = (route: MenuItem) => (
    <SideNavLink
      data-testid={`side-nav-link-${route.id}`}
      key={route.id}
      as={Link}
      to={route.path}
      isActive={route.path === location.pathname}
      renderIcon={route.icon}
    >
      {route.id}
    </SideNavLink>
  );

  const renderMenuItem = (route: MenuItem) => {
    const childPath = (parentPath: string, route: MenuItem) =>
      `${parentPath}${route.path ? `/${route.path}` : ''}`;
    return (
      <SideNavMenu
        data-testid={`side-nav-menu-${route.id}`}
        key={route.id}
        title={route.id}
        isActive={location.pathname.startsWith(route.path)}
        defaultExpanded={location.pathname.startsWith(route.path)}
        renderIcon={route.icon}
      >
        {route.children?.map((childRoute) => (
          <SideNavMenuItem
            data-testid={`side-nav-menu-item-${childRoute.id}`}
            key={childRoute.id}
            as={Link}
            to={childPath(route.path, childRoute)}
            isActive={childPath(route.path, childRoute) === location.pathname}
          >
            {renderIcon(childRoute)}
          </SideNavMenuItem>
        ))}
      </SideNavMenu>
    );
  };

  return (
    <SideNav
      expanded
      isPersistent={false}
      isChildOfHeader
      className={`side-nav-drawer${isSideNavExpanded ? ' side-nav-drawer--open' : ''}`}
    >
      <SideNavItems>
        {menuEntries.map((route) =>
          route.children ? renderMenuItem(route) : renderMenuLink(route),
        )}
        {/* Support — pinned to the bottom of the nav regardless of how many role-dependent entries
            render above it (see the flex rules in index.scss). A plain mailto: rather than a route:
            it opens the user's own mail client with the shared mailbox pre-addressed. The app tells
            users to "contact the FREP help desk" when something fails; this is the how. */}
        {supportEmail && (
          <>
            <li className="side-nav-support-heading" aria-hidden="true">
              Support
            </li>
            <SideNavLink
              data-testid="side-nav-link-email-support"
              href={`mailto:${supportEmail}`}
              renderIcon={Email}
            >
              Report an issue
            </SideNavLink>
          </>
        )}
      </SideNavItems>
    </SideNav>
  );
};

import {
  CloudOffline,
  Document,
  ListChecked,
  Search,
  SettingsAdjust,
  TableSplit,
} from '@carbon/icons-react';
import { ActionableNotification, ClickableTile, Column, Grid } from '@carbon/react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

import type { FC } from 'react';

import { APP_NAME } from '@/constants/appName';
import { useAuth } from '@/context/auth/useAuth';
import { env } from '@/env';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';

import './dashboard.scss';

/**
 * Remembers that the welcome banner was closed, per browser.
 *
 * Versioned: bump the suffix to put a new message in front of everyone who dismissed the last one.
 * Storage can throw outright (private windows, blocked site data), so both sides are guarded — a
 * browser that cannot remember simply shows the banner again, which is the harmless direction.
 */
const WELCOME_DISMISSED_KEY = 'frep.dashboard.welcome.v1';

const welcomeWasDismissed = (): boolean => {
  try {
    return window.localStorage.getItem(WELCOME_DISMISSED_KEY) === 'true';
  } catch {
    return false;
  }
};

type ScreenTile = {
  title: string;
  description: string;
  to: string;
  Icon: React.ComponentType<{ size?: number }>;
  sysAdminOnly?: boolean;
};

const SCREENS: ScreenTile[] = [
  {
    title: 'District Random List',
    description:
      'View randomly generated sites for a given district during a master list year, and drill into individual site details.',
    to: '/random-list',
    Icon: ListChecked,
  },
  {
    title: 'Accepted Sites',
    description:
      'Browse sites that have been accepted onto the current master list, filtered by district and protocol.',
    to: '/accepted-sites',
    Icon: TableSplit,
  },
  {
    title: 'Checklist Search',
    description:
      'Find any FREP checklist by tenure, opening, client number (with client lookup), protocol, or status.',
    to: '/search/checklists',
    Icon: Search,
  },
  {
    title: 'Exports',
    description:
      'Browse the catalog of legacy FREP Jasper reports. Generation is read-only for now.',
    to: '/exports',
    Icon: Document,
  },
  {
    title: 'Generate Master List',
    description:
      'Sys-admin tool to set eligibility criteria and (re-)generate the provincial random list.',
    to: '/admin/master-list',
    Icon: SettingsAdjust,
    sysAdminOnly: true,
  },
];

// Shown when offline / logged out — the only screen reachable without a network connection.
const OFFLINE_SCREEN: ScreenTile = {
  title: 'Offline Checklist',
  description: 'Open the CHR checklists saved on this device for offline editing.',
  to: '/chr/offline',
  Icon: CloudOffline,
};

const DashboardPage: FC = () => {
  const navigate = useNavigate();
  const { isSysAdmin } = useAuthorization();
  const [welcomeDismissed, setWelcomeDismissed] = useState(welcomeWasDismissed);
  // The same shared mailbox the side nav's "Report an issue" uses, read per render from the values
  // injected at container start. Unset means no mailbox to write to, so the invitation is dropped
  // rather than shipping a mailto: that goes nowhere — see LayoutSideNav.
  const supportEmail = env.VITE_SUPPORT_EMAIL?.trim() ?? '';
  const { isLoggedIn } = useAuth();
  const online = useOnlineStatus();

  // Offline (or logged out): the server-backed screens can't load, so only the device-local
  // offline checklists are usable.
  const offlineOnly = !online || !isLoggedIn;
  const visibleScreens = offlineOnly
    ? [OFFLINE_SCREEN]
    : SCREENS.filter((s) => !s.sysAdminOnly || isSysAdmin);

  const dismissWelcome = () => {
    setWelcomeDismissed(true);
    try {
      window.localStorage.setItem(WELCOME_DISMISSED_KEY, 'true');
    } catch {
      // Not remembered — it will greet them again next visit, which beats failing to close.
    }
  };

  return (
    <Grid fullWidth className="default-grid dashboard-grid">
      <Column sm={4} md={8} lg={16}>
        <h1 className="dashboard__title">{APP_NAME}</h1>
        <p className="dashboard__subtitle">Select a screen to begin.</p>
      </Column>

      {!welcomeDismissed && (
        <Column sm={4} md={8} lg={16}>
          {/* ActionableNotification rather than InlineNotification: the message carries a link, and
              an inline notification refuses interactive children outright (Carbon throws
              "component should have no interactive child nodes"). `inline` keeps the inline look.
              `hasFocus={false}` because it defaults to true — a welcome banner that grabs focus on
              arrival would drop the user past the page heading every time. */}
          <ActionableNotification
            kind="info"
            lowContrast
            inline
            hasFocus={false}
            className="dashboard__welcome"
            data-testid="dashboard-welcome"
            title="Welcome to the new FREP IMS!"
            subtitle={
              <span className="dashboard__welcome-text">
                Your feedback helps us improve the system
                {supportEmail ? (
                  <>
                    , don&apos;t hesitate to reach out to us at{' '}
                    {/* The address is the link text, the way the Landing page's "Request access"
                        mailbox reads — it comes from configuration, so spelling it out here would
                        go stale the moment the mailbox moves. */}
                    <a href={`mailto:${supportEmail}`}>{supportEmail}</a>.
                  </>
                ) : (
                  '.'
                )}
              </span>
            }
            statusIconDescription="Information"
            onCloseButtonClick={dismissWelcome}
          />
        </Column>
      )}

      <Column sm={4} md={8} lg={16}>
        <div className="dashboard__tiles">
          {visibleScreens.map(({ title, description, to, Icon }) => (
            <ClickableTile
              key={to}
              onClick={() => navigate(to)}
              className="dashboard__tile"
              aria-label={title}
            >
              <div className="dashboard__tile-icon" aria-hidden="true">
                <Icon size={32} />
              </div>
              <h2 className="dashboard__tile-title">{title}</h2>
              <p className="dashboard__tile-description">{description}</p>
            </ClickableTile>
          ))}
        </div>
      </Column>
    </Grid>
  );
};

export default DashboardPage;

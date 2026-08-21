import { ArrowRight, Login } from '@carbon/icons-react';
import { Button, Column, Grid, InlineNotification } from '@carbon/react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import logo_rev from '@/assets/img/bc-gov-logo-rev.png';
import logo from '@/assets/img/bc-gov-logo.png';
import LandingImg from '@/assets/img/landing.jpg';
import { Modal } from '@/components/Modal';
import { SESSION_EXPIRED_FLAG } from '@/components/SessionTimeout';
import useBreakpoint from '@/hooks/useBreakpoint';

import type { BreakpointType } from '@/hooks/useBreakpoint/types';
import type { FC } from 'react';

import { APP_FULL_NAME, APP_NAME } from '@/constants/appName';
import { useAuth } from '@/context/auth/useAuth';
import { useTheme } from '@/context/theme/useTheme';
import { env } from '@/env';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';

import './index.scss';

const LandingPage: FC = () => {
  const breakpoint = useBreakpoint();
  const { theme } = useTheme();
  const { login } = useAuth();
  const online = useOnlineStatus();
  const navigate = useNavigate();

  // Show the "session expired" notice once, when we land here via a timeout
  // logout (SessionTimeout stashes the flag before signing out). Read-and-clear
  // so a manual refresh of the login page doesn't keep showing it.
  const [sessionExpired, setSessionExpired] = useState(false);
  const [requestAccessOpen, setRequestAccessOpen] = useState(false);

  /**
   * The mailbox behind "Request access". Configuration, not code, and deliberately a different
   * address from the side nav's "Report an issue" support mailbox — reporting a bug and asking to be
   * provisioned go to different people. Set from the ACCESS_REQUEST_EMAIL GitHub variable; the whole
   * block is hidden when it is unset rather than offering a link that goes nowhere.
   */
  const accessRequestEmail = env.VITE_ACCESS_REQUEST_EMAIL?.trim() ?? '';

  useEffect(() => {
    if (sessionStorage.getItem(SESSION_EXPIRED_FLAG) === '1') {
      setSessionExpired(true);
      sessionStorage.removeItem(SESSION_EXPIRED_FLAG);
    }
  }, []);

  const elementMarginMap: Record<BreakpointType, number> = {
    max: 6,
    xlg: 6,
    lg: 6,
    md: 3,
    sm: 2.5,
  };

  const elementGap = elementMarginMap[breakpoint] || elementMarginMap.sm;

  return (
    <div className="landing-grid-container">
      <Grid fullWidth className="landing-grid">
        <Column className="landing-content-col" sm={4} md={8} lg={8}>
          <div className="landing-content-wrapper" style={{ gap: `${elementGap}rem` }}>
            <div>
              <img
                src={theme === 'g100' ? logo_rev : logo}
                alt="BCGov Logo"
                width={160}
                className="logo"
              />
            </div>

            <h1 data-testid="landing-title" className="landing-title">
              {APP_NAME}
            </h1>

            <h2 data-testid="landing-subtitle" className="landing-subtitle">
              {APP_FULL_NAME}
            </h2>

            {sessionExpired && (
              <InlineNotification
                kind="warning"
                lowContrast
                hideCloseButton
                className="landing-session-expired"
                data-testid="landing-session-expired"
                title="You've been logged out"
                subtitle="Your session expired for security reasons and any unsaved changes were lost. Log in again to continue."
              />
            )}

            <div className="buttons-container single-row">
              {online ? (
                <>
                  <Button
                    type="button"
                    onClick={() => login('idir')}
                    renderIcon={Login}
                    size="md"
                    data-testid="landing-button__idir"
                    className="login-btn"
                  >
                    Log in with IDIR
                  </Button>
                  <Button
                    type="button"
                    kind="tertiary"
                    onClick={() => login('bceid')}
                    renderIcon={Login}
                    size="md"
                    data-testid="landing-button__bceid"
                    className="login-btn"
                  >
                    Log in with Business BCeID
                  </Button>
                </>
              ) : (
                // Offline: IDIR login can't run, so enter the offline FREP IMS.
                <Button
                  type="button"
                  onClick={() => navigate('/dashboard')}
                  renderIcon={ArrowRight}
                  size="md"
                  data-testid="landing-button__offline"
                  className="login-btn"
                >
                  Get started
                </Button>
              )}
            </div>

            {online && accessRequestEmail && (
              <div className="landing-request-access">
                <button
                  type="button"
                  className="landing-request-access__link"
                  data-testid="landing-request-access"
                  onClick={() => setRequestAccessOpen(true)}
                >
                  Request access to {APP_NAME}
                </button>
                <p className="landing-request-access__note">
                  An active IDIR or Business BCeID account is required
                </p>
              </div>
            )}
          </div>
        </Column>
        <Column className="landing-img-col" sm={4} md={8} lg={8}>
          <img src={LandingImg} alt="Landing cover" className="landing-img" />
        </Column>
      </Grid>

      <Modal
        open={requestAccessOpen}
        modalHeading={`Request access to ${APP_NAME}`}
        passiveModal
        size="sm"
        onRequestClose={() => setRequestAccessOpen(false)}
      >
        <div className="landing-request-modal">
          <p>
            To request access, email{' '}
            <a href={`mailto:${accessRequestEmail}`}>{accessRequestEmail}</a> and include:
          </p>

          <ul>
            <li>First and last name, or IDIR / Business BCeID username</li>
            <li>Email address</li>
            <li>Organization</li>
          </ul>

          {/* CHR editing is granted per district (FREP_CHR_EDITOR_DISTRICT_<code>), not globally, so
              a request that omits the districts can't be actioned. */}
          <p className="landing-request-modal__group-title">For Cultural Heritage (CHR) editing</p>
          <ul>
            <li>The district(s) you need to edit CHR checklists for</li>
          </ul>
        </div>
      </Modal>
    </div>
  );
};

export default LandingPage;

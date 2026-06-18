import { ArrowRight, Login } from '@carbon/icons-react';
import { Button, Column, Grid } from '@carbon/react';
import { useNavigate } from 'react-router-dom';

import logo_rev from '@/assets/img/bc-gov-logo-rev.png';
import logo from '@/assets/img/bc-gov-logo.png';
import LandingImg from '@/assets/img/landing.jpg';
import useBreakpoint from '@/hooks/useBreakpoint';

import type { BreakpointType } from '@/hooks/useBreakpoint/types';
import type { FC } from 'react';

import { useAuth } from '@/context/auth/useAuth';
import { useTheme } from '@/context/theme/useTheme';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';

import './index.scss';

const LandingPage: FC = () => {
  const breakpoint = useBreakpoint();
  const { theme } = useTheme();
  const { login } = useAuth();
  const online = useOnlineStatus();
  const navigate = useNavigate();

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
              FREP
            </h1>

            <h2 data-testid="landing-subtitle" className="landing-subtitle">
              Natural Resources Application
            </h2>

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
                // Offline: IDIR login can't run, so enter the offline FREP Dashboard.
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
          </div>
        </Column>
        <Column className="landing-img-col" sm={4} md={8} lg={8}>
          <img src={LandingImg} alt="Landing cover" className="landing-img" />
        </Column>
      </Grid>
    </div>
  );
};

export default LandingPage;

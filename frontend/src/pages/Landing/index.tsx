import { ArrowRight } from '@carbon/icons-react';
import { Button, Column, Grid } from '@carbon/react';
import { Link } from 'react-router-dom';

import logo_rev from '@/assets/img/bc-gov-logo-rev.png';
import logo from '@/assets/img/bc-gov-logo.png';
import LandingImg from '@/assets/img/landing.jpg';
import { useTheme } from '@/context/theme/useTheme';
import useBreakpoint from '@/hooks/useBreakpoint';

import type { BreakpointType } from '@/hooks/useBreakpoint/types';
import type { FC } from 'react';

import './index.scss';

const LandingPage: FC = () => {
  const breakpoint = useBreakpoint();
  const { theme } = useTheme();

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
              <Button
                as={Link}
                to="/dashboard"
                renderIcon={ArrowRight}
                size="md"
                data-testid="landing-button__dashboard"
                className="login-btn"
              >
                Go to dashboard
              </Button>
            </div>

            {/*
              --- IDIR login (re-enable with Cognito auth before deploying) ---
              <Button
                type="button"
                onClick={() => login()}
                renderIcon={Login}
                data-testid="landing-button__idir"
              >
                Log in with IDIR
              </Button>
            */}
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

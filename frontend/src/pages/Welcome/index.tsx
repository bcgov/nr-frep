import { Column, Grid, Link, Tile } from '@carbon/react';
import { Link as RouterLink } from 'react-router-dom';

import type { FC } from 'react';

import { useAuth } from '@/context/auth/useAuth';

import './welcome.scss';

const QUICK_LINKS = [
  { to: '/random-list', label: 'FREP100 — District Random List' },
  { to: '/accepted-sites', label: 'FREP200 — Accepted Sites' },
  { to: '/search/checklists', label: 'FREP400 — Checklist Search' },
  { to: '/search/clients', label: 'FREP410 — Client Search' },
];

const WelcomePage: FC = () => {
  const { user } = useAuth();
  const greetingName = user?.firstName || user?.displayName || 'Evaluator';

  return (
    <Grid fullWidth className="default-grid welcome-grid">
      <Column sm={4} md={8} lg={16}>
        <h1 className="welcome__title">FREP000 — Welcome, {greetingName}</h1>
        <p className="welcome__subtitle">
          The Forest and Range Evaluation Program (FREP) supports field evaluations of forest and
          range practices on BC&apos;s Crown land.
        </p>
      </Column>

      <Column sm={4} md={8} lg={10}>
        <Tile className="welcome__intro">
          <h2>About FREP</h2>
          <p>
            FREP collects checklist data against randomly selected sites each year so that the
            Province can report on whether forest and range practices are meeting the objectives of
            the Forest and Range Practices Act.
          </p>
          <p>
            This new front end mirrors the legacy workflows: district lists, site details, accepted
            sites, protocol checklists (Biodiversity, Riparian, Water Quality), search, and master
            list administration.
          </p>
          <p>
            <Link
              href="https://www2.gov.bc.ca/gov/content/industry/forestry/managing-our-forest-resources/integrated-resource-monitoring/forest-range-evaluation-program"
              target="_blank"
              rel="noopener noreferrer"
            >
              FREP program overview (gov.bc.ca)
            </Link>
          </p>
        </Tile>
      </Column>

      <Column sm={4} md={8} lg={6}>
        <Tile className="welcome__links">
          <h2>Quick links</h2>
          <ul>
            {QUICK_LINKS.map((link) => (
              <li key={link.to}>
                <RouterLink to={link.to}>{link.label}</RouterLink>
              </li>
            ))}
          </ul>
        </Tile>
      </Column>
    </Grid>
  );
};

export default WelcomePage;

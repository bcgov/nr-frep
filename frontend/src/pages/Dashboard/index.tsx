import {
  DocumentTasks,
  Home,
  ListChecked,
  Search,
  SettingsAdjust,
  TableSplit,
  Tree,
  UserMultiple,
} from '@carbon/icons-react';
import { ClickableTile, Column, Grid } from '@carbon/react';
import { useNavigate } from 'react-router-dom';

import type { FC } from 'react';

import { useAuthorization } from '@/hooks/useAuthorization';

import './dashboard.scss';

type ScreenTile = {
  code: string;
  title: string;
  description: string;
  to: string;
  Icon: React.ComponentType<{ size?: number }>;
  sysAdminOnly?: boolean;
};

const SCREENS: ScreenTile[] = [
  {
    code: 'FREP000',
    title: 'Welcome',
    description: 'Program intro and quick links to the screens you use most.',
    to: '/welcome',
    Icon: Home,
  },
  {
    code: 'FREP100',
    title: 'District Random List',
    description:
      'View randomly generated sites for a given district during a master list year, and drill into individual site details.',
    to: '/random-list',
    Icon: ListChecked,
  },
  {
    code: 'FREP110',
    title: 'Site Details',
    description:
      'Inspect a single selected site: tenure header, opening info, and per-protocol accept / reject / target decisions.',
    to: '/site-detail/1001',
    Icon: DocumentTasks,
  },
  {
    code: 'FREP200',
    title: 'Accepted Sites',
    description:
      'Browse sites that have been accepted onto the current master list, filtered by district and protocol.',
    to: '/accepted-sites',
    Icon: TableSplit,
  },
  {
    code: 'FREP210–254',
    title: 'Protocol Checklists',
    description:
      'Biodiversity, Riparian, and Water Quality field evaluations rendered as tabbed checklists.',
    to: '/protocol-checklists/biodiversity/9001',
    Icon: Tree,
  },
  {
    code: 'FREP400',
    title: 'Checklist Search',
    description: 'Find any FREP checklist by tenure, opening, client number, protocol, or status.',
    to: '/search/checklists',
    Icon: Search,
  },
  {
    code: 'FREP410',
    title: 'Client Search',
    description: 'Look up Forest Client records by client number or name.',
    to: '/search/clients',
    Icon: UserMultiple,
  },
  {
    code: 'FREP700',
    title: 'Generate Master List',
    description:
      'Sys-admin tool to set eligibility criteria and (re-)generate the provincial random list.',
    to: '/admin/master-list',
    Icon: SettingsAdjust,
    sysAdminOnly: true,
  },
];

const DashboardPage: FC = () => {
  const navigate = useNavigate();
  const { isSysAdmin } = useAuthorization();

  const visibleScreens = SCREENS.filter((s) => !s.sysAdminOnly || isSysAdmin);

  return (
    <Grid fullWidth className="default-grid dashboard-grid">
      <Column sm={4} md={8} lg={16}>
        <h1 className="dashboard__title">FREP Dashboard</h1>
        <p className="dashboard__subtitle">
          Select a screen to begin. These screens are read-only ports of the legacy FREP web app.
        </p>
      </Column>

      <Column sm={4} md={8} lg={16}>
        <div className="dashboard__tiles">
          {visibleScreens.map(({ code, title, description, to, Icon }) => (
            <ClickableTile
              key={code}
              onClick={() => navigate(to)}
              className="dashboard__tile"
              aria-label={`${code} — ${title}`}
            >
              <div className="dashboard__tile-icon" aria-hidden="true">
                <Icon size={32} />
              </div>
              <span className="dashboard__tile-code">{code}</span>
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

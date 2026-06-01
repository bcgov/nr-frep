import {
  Document,
  ListChecked,
  Search,
  SettingsAdjust,
  TableSplit,
  UserMultiple,
} from '@carbon/icons-react';
import { ClickableTile, Column, Grid } from '@carbon/react';
import { useNavigate } from 'react-router-dom';

import type { FC } from 'react';

import { useAuthorization } from '@/hooks/useAuthorization';

import './dashboard.scss';

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
    description: 'Find any FREP checklist by tenure, opening, client number, protocol, or status.',
    to: '/search/checklists',
    Icon: Search,
  },
  {
    title: 'Client Search',
    description: 'Look up Forest Client records by client number or name.',
    to: '/search/clients',
    Icon: UserMultiple,
  },
  {
    title: 'Reports',
    description:
      'Browse the catalog of legacy FREP Jasper reports. Generation is read-only for now.',
    to: '/reports',
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

const DashboardPage: FC = () => {
  const navigate = useNavigate();
  const { isSysAdmin } = useAuthorization();

  const visibleScreens = SCREENS.filter((s) => !s.sysAdminOnly || isSysAdmin);

  return (
    <Grid fullWidth className="default-grid dashboard-grid">
      <Column sm={4} md={8} lg={16}>
        <h1 className="dashboard__title">FREP Dashboard</h1>
        <p className="dashboard__subtitle">
          Select a screen to begin.
        </p>
      </Column>

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

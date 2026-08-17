import { Header, HeaderMenuButton, HeaderName, SkipToContent } from '@carbon/react';
import { type FC } from 'react';
import { Link } from 'react-router-dom';

import { LayoutHeaderPanel } from '@/components/Layout/LayoutHeaderPanel';
import { LayoutSideNav } from '@/components/Layout/LayoutSideNav';

import LayoutHeaderGlobalBar from './LayoutHeaderGlobalBar';

import { APP_NAME } from '@/constants/appName';
import { useLayout } from '@/context/layout/useLayout';

import './index.scss';

export const LayoutHeader: FC = () => {
  const { isSideNavExpanded, toggleSideNav, closeSideNav } = useLayout();

  const handleMenuButtonClick = () => {
    if (isSideNavExpanded) {
      closeSideNav();
    } else {
      toggleSideNav();
    }
  };

  const appName = APP_NAME;

  return (
    <Header aria-label={appName} className="bc-header" data-testid="bc-header__header">
      <SkipToContent />
      <HeaderMenuButton
        aria-label={isSideNavExpanded ? 'Close menu' : 'Open menu'}
        isActive={isSideNavExpanded}
        onClick={handleMenuButtonClick}
      />
      <HeaderName as={Link} to={'/dashboard'} prefix="">
        {appName}
      </HeaderName>

      <LayoutHeaderGlobalBar />
      <LayoutHeaderPanel />
      <LayoutSideNav />
    </Header>
  );
};

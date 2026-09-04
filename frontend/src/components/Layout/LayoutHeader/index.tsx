import { Header, HeaderMenuButton, HeaderName, SkipToContent } from '@carbon/react';
import { type FC } from 'react';
import { Link } from 'react-router-dom';

import { LayoutHeaderPanel } from '@/components/Layout/LayoutHeaderPanel';
import { LayoutSideNav } from '@/components/Layout/LayoutSideNav';

import LayoutHeaderGlobalBar from './LayoutHeaderGlobalBar';

import { APP_FULL_NAME, APP_NAME } from '@/constants/appName';
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

  return (
    <Header aria-label={APP_NAME} className="bc-header" data-testid="bc-header__header">
      <SkipToContent />
      <HeaderMenuButton
        aria-label={isSideNavExpanded ? 'Close menu' : 'Open menu'}
        isActive={isSideNavExpanded}
        onClick={handleMenuButtonClick}
      />
      {/* "FREP IMS" renders in Carbon's prefix style (regular weight) ahead of the bold full name,
          matching the FSPTS header treatment. */}
      <HeaderName as={Link} to="/dashboard" prefix={APP_NAME}>
        {APP_FULL_NAME}
      </HeaderName>

      <LayoutHeaderGlobalBar />
      <LayoutHeaderPanel />
      <LayoutSideNav />
    </Header>
  );
};

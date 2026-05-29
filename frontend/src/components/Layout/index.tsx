import { Content, HeaderContainer } from '@carbon/react';

import { LayoutProvider } from '@/context/layout/LayoutProvider';
import { useLayout } from '@/context/layout/useLayout';

import { LayoutHeader } from './LayoutHeader';

import type { FC, ReactNode } from 'react';

import './index.scss';

const LayoutContent: FC<{ children: ReactNode }> = ({ children }) => {
  const { isSideNavExpanded } = useLayout();

  return (
    <Content
      className={isSideNavExpanded ? 'layout-content--side-nav-expanded' : undefined}
    >
      {children}
    </Content>
  );
};

const Layout: FC<{ children: ReactNode }> = ({ children }) => {
  return (
    <LayoutProvider>
      <HeaderContainer render={LayoutHeader} />
      <LayoutContent>{children}</LayoutContent>
    </LayoutProvider>
  );
};

export default Layout;

import { useState, useEffect, type ReactNode, useCallback } from 'react';

import { PageTitleContext } from './PageTitleContext';

import { APP_NAME } from '@/constants/appName';

export const PageTitleProvider = ({ children }: { children: ReactNode }) => {
  const [pageTitle, setPageTitleState] = useState(document.title);
  const [currentHierarchy, setCurrentHierarchy] = useState(0);

  const resolveName = (title: string) => [APP_NAME, title].join(' - ');

  const setPageTitle = useCallback(
    (title: string, hierarchy?: 1 | 2 | 3) => {
      const actualHierarchy = hierarchy ?? 3;
      setPageTitleState((prev) => {
        if (actualHierarchy >= currentHierarchy) {
          return resolveName(title);
        }
        return prev;
      });
      setCurrentHierarchy((prevHierarchy) =>
        actualHierarchy >= prevHierarchy ? actualHierarchy : prevHierarchy,
      );
    },
    [currentHierarchy],
  );

  useEffect(() => {
    document.title = pageTitle;
  }, [pageTitle]);

  return (
    <PageTitleContext.Provider value={{ pageTitle, setPageTitle, currentHierarchy }}>
      {children}
    </PageTitleContext.Provider>
  );
};

export default PageTitleProvider;

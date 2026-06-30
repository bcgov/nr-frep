import { test } from '@playwright/test';

import { expectNoGlobalError, gotoProtected } from './utils';

/**
 * The detail pages load their record by id from the backend. Hitting them with a bogus id exercises
 * their not-found / error handling — they must render a graceful in-page state within the Layout, not
 * fall through to the global error boundary. Read-only: no record with these ids exists.
 */
const BOGUS_DETAIL_ROUTES = [
  '/site-detail/0',
  '/protocol-checklists/biodiversity/0',
  '/chr/checklists/0',
];

test.describe('Detail page resilience (unknown id)', () => {
  for (const route of BOGUS_DETAIL_ROUTES) {
    test(`${route} loads gracefully without the error boundary`, async ({ page }) => {
      await gotoProtected(page, route);
      await expectNoGlobalError(page);
    });
  }
});

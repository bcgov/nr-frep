import { expect, test } from '@playwright/test';

import { expectNoGlobalError, gotoProtected } from './utils';

/**
 * Read-only smoke checks that each top-level protected screen boots past the auth/loading overlay
 * and renders its own page (its <h1>), without tripping the global error boundary. These hit a real
 * deployed FREP backend, so they don't write anything.
 */
const PAGES = [
  { path: '/dashboard', heading: 'FREP IMS' },
  { path: '/accepted-sites', heading: 'Accepted Sites' },
  { path: '/search/checklists', heading: 'Checklist Search' },
  { path: '/random-list', heading: 'District Random List' },
  { path: '/reports', heading: 'Reports' },
];

test.describe('Protected page navigation', () => {
  for (const { path, heading } of PAGES) {
    test(`${path} renders the "${heading}" page`, async ({ page }) => {
      await gotoProtected(page, path);
      await expect(page.getByRole('heading', { name: heading, level: 1 })).toBeVisible();
      await expectNoGlobalError(page);
    });
  }
});

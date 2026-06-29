import { expect, test } from '@playwright/test';

import { expectNoGlobalError, gotoProtected } from './utils';

test.describe('CHR offline checklists', () => {
  test('renders the offline checklists page', async ({ page }) => {
    await gotoProtected(page, '/chr/offline');

    await expect(page.getByRole('heading', { name: 'Offline checklists', level: 1 })).toBeVisible();
    await expectNoGlobalError(page);
  });
});

test.describe('Generate Master List (admin)', () => {
  // Role-gated: a non-admin lands on /unauthorized. Either way the route must boot cleanly without
  // tripping the error boundary; the Generate action is destructive so it is never clicked here.
  test('the admin route loads without the error boundary', async ({ page }) => {
    await gotoProtected(page, '/admin/master-list');
    await expectNoGlobalError(page);

    const heading = page.getByRole('heading', { name: 'Generate Master List', level: 1 });
    if (await heading.isVisible().catch(() => false)) {
      await expect(page.getByLabel('Master list year')).toBeVisible();
      // Present but intentionally not clicked (it mutates data).
      await expect(page.getByRole('button', { name: /Generate/ }).first()).toBeVisible();
    }
  });
});

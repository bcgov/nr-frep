import { expect, test, type Page } from '@playwright/test';

import { gotoProtected } from './utils';

/**
 * Smoke check that protected routes render without throwing.
 */

const assertNoGlobalError = async (page: Page) => {
  await expect(page.getByRole('heading', { name: /something went wrong/i })).toHaveCount(0);
};

test.describe('page coverage', () => {
  test('dashboard renders the landing menu', async ({ page }) => {
    await gotoProtected(page, '/dashboard');
    await expect(page.getByTestId('side-nav-link-Dashboard')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'FREP Dashboard', level: 1 })).toBeVisible();
    await assertNoGlobalError(page);
  });

  test('accepted sites page renders', async ({ page }) => {
    await gotoProtected(page, '/accepted-sites');
    await expect(page.getByRole('heading', { name: 'Accepted Sites', level: 1 })).toBeVisible();
    // The list loaded without erroring: either the results table (data) or the empty state is
    // shown. The table renders via Carbon's headless DataTable, which doesn't forward data-testid
    // to the DOM, so match it by role.
    await expect(
      page.getByRole('table').or(page.getByTestId('accepted-sites-empty')),
    ).toBeVisible();
    await assertNoGlobalError(page);
  });

  test('404 renders for unknown route', async ({ page }) => {
    await gotoProtected(page, '/this-route-does-not-exist');
    await expect(page.getByText(/not found|404/i).first()).toBeVisible();
    await assertNoGlobalError(page);
  });
});

import { expect, test, type Page } from '@playwright/test';

import { gotoProtected } from './utils';

/**
 * Smoke check that protected routes render without throwing.
 */

const assertNoGlobalError = async (page: Page) => {
  await expect(page.getByRole('heading', { name: /something went wrong/i })).toHaveCount(0);
};

test.describe('page coverage', () => {
  test('dashboard renders accepted sites table', async ({ page }) => {
    await gotoProtected(page, '/dashboard');
    await expect(page.getByTestId('side-nav-link-Dashboard')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Accepted Sites' })).toBeVisible();
    await expect(page.getByTestId('accepted-sites-table')).toBeVisible();
    await assertNoGlobalError(page);
  });

  test('404 renders for unknown route', async ({ page }) => {
    await gotoProtected(page, '/this-route-does-not-exist');
    await expect(page.getByText(/not found|404/i).first()).toBeVisible();
    await assertNoGlobalError(page);
  });
});

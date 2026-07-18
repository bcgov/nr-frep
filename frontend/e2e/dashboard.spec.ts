import { expect, test } from '@playwright/test';

import { expectNoGlobalError, gotoProtected } from './utils';

test.describe('Home', () => {
  test('renders the home page with screen tiles', async ({ page }) => {
    await gotoProtected(page, '/home');

    await expect(page.getByRole('heading', { name: 'FREP IMS', level: 1 })).toBeVisible();
    // Each screen tile shows its name as an <h2>.
    await expect(page.getByRole('heading', { name: 'Accepted Sites', level: 2 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Checklist Search', level: 2 })).toBeVisible();
    await expectNoGlobalError(page);
  });

  test('a tile navigates to its screen', async ({ page }) => {
    await gotoProtected(page, '/home');

    // ClickableTile carries an aria-label of the screen name; clicking it routes there.
    await page.locator('.dashboard__tile[aria-label="Accepted Sites"]').click();

    await expect(page).toHaveURL(/\/accepted-sites$/);
    await expect(page.getByRole('heading', { name: 'Accepted Sites', level: 1 })).toBeVisible();
    await expectNoGlobalError(page);
  });
});

import { expect, test } from '@playwright/test';

import { expectNoGlobalError, gotoProtected, waitForSettled } from './utils';

test.describe('Accepted Sites', () => {
  test('renders filters, the Refresh action, and a settled results state', async ({ page }) => {
    await gotoProtected(page, '/accepted-sites');

    await expect(page.getByRole('heading', { name: 'Accepted Sites', level: 1 })).toBeVisible();
    await expect(page.getByLabel('Master list year')).toBeVisible();
    await expect(page.getByLabel('Org unit')).toBeVisible();
    await expect(page.getByLabel('Protocol')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Refresh' })).toBeVisible();

    await waitForSettled(page, 'accepted-sites');
    await expectNoGlobalError(page);
  });

  test('changing the Protocol filter re-loads without error', async ({ page }) => {
    await gotoProtected(page, '/accepted-sites');
    await waitForSettled(page, 'accepted-sites');

    // Switch to the "All protocols" / first protocol option (read-only filter change).
    await page.getByLabel('Protocol').selectOption({ index: 1 });
    await waitForSettled(page, 'accepted-sites');
    await expectNoGlobalError(page);
  });

  test('Refresh re-runs the load', async ({ page }) => {
    await gotoProtected(page, '/accepted-sites');
    await waitForSettled(page, 'accepted-sites');

    const refresh = page.getByRole('button', { name: 'Refresh' });
    if (await refresh.isEnabled()) {
      await refresh.click();
      await waitForSettled(page, 'accepted-sites');
    }
    await expectNoGlobalError(page);
  });
});

import { expect, test } from '@playwright/test';

import { expectNoGlobalError, gotoProtected, waitForSettled } from './utils';

test.describe('District Random List', () => {
  test('renders filters and settles into a results state', async ({ page }) => {
    await gotoProtected(page, '/random-list');

    await expect(
      page.getByRole('heading', { name: 'District Random List', level: 1 }),
    ).toBeVisible();
    await expect(page.getByLabel('Master list year')).toBeVisible();
    await expect(page.getByLabel('Org unit')).toBeVisible();

    await waitForSettled(page, 'random-list');
    await expectNoGlobalError(page);
  });

  test('changing the Org unit filter re-loads without error', async ({ page }) => {
    await gotoProtected(page, '/random-list');
    await waitForSettled(page, 'random-list');

    const orgUnit = page.getByLabel('Org unit');
    const optionCount = await orgUnit.locator('option').count();
    if (optionCount > 1) {
      await orgUnit.selectOption({ index: 1 });
      await waitForSettled(page, 'random-list');
    }
    await expectNoGlobalError(page);
  });
});

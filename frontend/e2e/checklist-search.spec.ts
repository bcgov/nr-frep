import { expect, test } from '@playwright/test';

import { expectNoGlobalError, gotoProtected, waitForSettled } from './utils';

test.describe('Checklist Search', () => {
  test('renders every filter plus the Search and Clear controls', async ({ page }) => {
    await gotoProtected(page, '/search/checklists');

    await expect(page.getByRole('heading', { name: 'Checklist Search', level: 1 })).toBeVisible();
    for (const label of [
      'Master list year',
      'Org unit',
      'Protocol',
      'Status',
      'Opening ID',
      'Client name',
    ]) {
      await expect(page.getByLabel(label, { exact: true })).toBeVisible();
    }
    await expect(page.getByRole('button', { name: 'Search', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Clear', exact: true })).toBeVisible();
    await expectNoGlobalError(page);
  });

  test('Search settles into a results state', async ({ page }) => {
    await gotoProtected(page, '/search/checklists');

    await page.getByRole('button', { name: 'Search', exact: true }).click();
    await waitForSettled(page, 'checklist-search');
    await expectNoGlobalError(page);
  });

  test('Clear empties a typed filter', async ({ page }) => {
    await gotoProtected(page, '/search/checklists');

    const opening = page.getByLabel('Opening ID', { exact: true });
    await opening.fill('1234567');
    await expect(opening).toHaveValue('1234567');

    await page.getByRole('button', { name: 'Clear', exact: true }).click();
    await expect(opening).toHaveValue('');
    await expectNoGlobalError(page);
  });

  test('the client lookup modal opens and closes', async ({ page }) => {
    await gotoProtected(page, '/search/checklists');

    await page.getByRole('button', { name: 'Look up client' }).click();
    const dialog = page.getByRole('dialog', { name: 'Client Search' });
    await expect(dialog).toBeVisible();

    await dialog.getByRole('button', { name: 'Close' }).click();
    await expect(dialog).toBeHidden();
    await expectNoGlobalError(page);
  });
});

import { expect, test } from '@playwright/test';

import { expectNoGlobalError, gotoProtected } from './utils';

// The opening-search page reads its district context from query params, so it renders standalone.
const URL = '/add-target-site?orgUnit=1&orgUnitName=Test+District&year=2024';

const FILTER_LABELS = [
  'Licence',
  'Cutting permit',
  'Timber mark',
  'Cut block',
  'Opening ID',
  'Licensee opening ID',
  'Client',
  'Block status',
  'Open category',
  'Opening status',
  'Date type',
  'Sort by',
];

test.describe('Add Target Site (opening search)', () => {
  test('renders the heading, district, every filter, and Search/Clear', async ({ page }) => {
    await gotoProtected(page, URL);

    await expect(page.getByRole('heading', { name: 'Add target site', level: 1 })).toBeVisible();
    await expect(page.getByText('District 1 - Test District')).toBeVisible();
    for (const label of FILTER_LABELS) {
      await expect(page.getByLabel(label, { exact: true })).toBeVisible();
    }
    await expect(page.getByRole('button', { name: 'Search' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Clear' })).toBeVisible();
    await expectNoGlobalError(page);
  });

  test('Clear resets a typed filter', async ({ page }) => {
    await gotoProtected(page, URL);

    const licence = page.getByLabel('Licence', { exact: true });
    await licence.fill('A20018');
    await expect(licence).toHaveValue('A20018');

    await page.getByRole('button', { name: 'Clear' }).click();
    await expect(licence).toHaveValue('');
    await expectNoGlobalError(page);
  });

  test('the client lookup modal opens and closes', async ({ page }) => {
    await gotoProtected(page, URL);

    await page.getByRole('button', { name: 'Look up client' }).click();
    const dialog = page.getByRole('dialog', { name: 'Client Search' });
    await expect(dialog).toBeVisible();

    await dialog.getByRole('button', { name: 'Close' }).click();
    await expect(dialog).toBeHidden();
    await expectNoGlobalError(page);
  });

  test('Back returns to where the user came from', async ({ page }) => {
    await gotoProtected(page, '/accepted-sites');
    await gotoProtected(page, URL);

    await page.getByRole('button', { name: 'Back' }).click();
    await expect(page).toHaveURL(/\/accepted-sites/);
  });
});

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
    // Located by role, not by label: Carbon's ComboBox labels both the input and its listbox with
    // the same element, so getByLabel matches two nodes and trips strict mode.
    await expect(page.getByRole('combobox', { name: 'Client' })).toBeVisible();
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

  test('the client picker is a type-ahead, not a lookup dialog', async ({ page }) => {
    // Same combo box as Checklist Search — see that spec for the reasoning.
    await gotoProtected(page, URL);

    await expect(page.getByRole('combobox', { name: 'Client' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Look up client' })).toHaveCount(0);
    await expectNoGlobalError(page);
  });

  test('Back returns to where the user came from', async ({ page }) => {
    await gotoProtected(page, '/accepted-sites');
    await gotoProtected(page, URL);

    await page.getByRole('button', { name: 'Back' }).click();
    await expect(page).toHaveURL(/\/accepted-sites/);
  });
});

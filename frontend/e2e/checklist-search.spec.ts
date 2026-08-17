import { expect, test } from '@playwright/test';

import { expectNoGlobalError, gotoProtected, waitForSettled } from './utils';

test.describe('Checklist Search', () => {
  test('renders every filter plus the Search and Clear controls', async ({ page }) => {
    await gotoProtected(page, '/search/checklists');

    await expect(page.getByRole('heading', { name: 'Checklist Search', level: 1 })).toBeVisible();
    for (const label of ['Master list year', 'Org unit', 'Protocol', 'Status', 'Opening ID']) {
      await expect(page.getByLabel(label, { exact: true })).toBeVisible();
    }
    // Located by role, not by label: Carbon's ComboBox labels both the input and its listbox with
    // the same element, so getByLabel matches two nodes and trips strict mode.
    await expect(page.getByRole('combobox', { name: 'Client name' })).toBeVisible();
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

  test('the client picker is a type-ahead, not a lookup dialog', async ({ page }) => {
    // Replaced the search-form-in-a-modal: the field is now a combo box that searches name, acronym
    // and client number together as the user types. Asserting the role and the helper text keeps
    // this independent of what client data the environment happens to hold.
    await gotoProtected(page, '/search/checklists');

    const client = page.getByRole('combobox', { name: 'Client name' });
    await expect(client).toBeVisible();
    await expect(
      page.getByText('Enter name, acronym, or client number (min. 3 characters)'),
    ).toBeVisible();

    // No dialog, and nothing left of the old lookup/clear icon buttons.
    await expect(page.getByRole('button', { name: 'Look up client' })).toHaveCount(0);
    await expectNoGlobalError(page);
  });
});

import { expect, test } from '@playwright/test';

import { expectNoGlobalError, gotoProtected } from './utils';

test.describe('Exports', () => {
  test('renders the Exports page', async ({ page }) => {
    await gotoProtected(page, '/exports');

    await expect(page.getByRole('heading', { name: 'Exports', level: 1 })).toBeVisible();
    await expect(page.getByText('Generate FREP exports below.')).toBeVisible();
    await expectNoGlobalError(page);
  });
});

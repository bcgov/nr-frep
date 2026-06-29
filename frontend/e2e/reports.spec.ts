import { expect, test } from '@playwright/test';

import { expectNoGlobalError, gotoProtected } from './utils';

test.describe('Reports', () => {
  test('renders the Reports page', async ({ page }) => {
    await gotoProtected(page, '/reports');

    await expect(page.getByRole('heading', { name: 'Reports', level: 1 })).toBeVisible();
    await expect(page.getByText('Generate FREP reports below.')).toBeVisible();
    await expectNoGlobalError(page);
  });
});

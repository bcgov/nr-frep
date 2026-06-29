import { expect, test } from '@playwright/test';

import { gotoProtected } from './utils';

test('an unknown route renders the Not Found page, not the error boundary', async ({ page }) => {
  await gotoProtected(page, '/this-route-does-not-exist');

  await expect(page.getByRole('heading', { name: 'Not Found', level: 1 })).toBeVisible();
  await expect(page.getByText('The page you are looking for does not exist.')).toBeVisible();
  // The catch-all should be a graceful 404, not the global error boundary.
  await expect(page.getByRole('heading', { name: 'Global Error' })).toHaveCount(0);
});

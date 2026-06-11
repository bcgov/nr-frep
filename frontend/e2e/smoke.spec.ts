import { expect, test, type Page } from '@playwright/test';

test('root responds with 200', async ({ page }) => {
  const response = await page.goto('/');
  expect(response?.status(), 'root should return 200').toBe(200);
});

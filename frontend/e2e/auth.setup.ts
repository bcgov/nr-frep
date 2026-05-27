import { existsSync } from 'node:fs';

import { test as setup, expect } from '@playwright/test';

import { STORAGE_STATE } from './utils';

/**
 * One-time IDIR sign-in bootstrap. Parks the browser at the FAM/IDIR login
 * page and waits for the operator to finish authentication; on success the
 * Amplify cookies + localStorage are persisted to {@link STORAGE_STATE} so
 * subsequent test runs start already logged in.
 *
 * Triggered by {@code npm run e2e:login}. Should be re-run whenever the saved
 * session expires (tests start failing with 401 or redirect loops).
 */
setup('authenticate via IDIR', async ({ page }) => {
  if (existsSync(STORAGE_STATE) && !process.env.E2E_FORCE_LOGIN) {
    console.log(`Reusing existing auth state at ${STORAGE_STATE}`);
    return;
  }

  await page.goto('/');

  // The landing page exposes a Log in button that kicks off Cognito/IDIR.
  const loginButton = page.getByRole('button', { name: /log in with idir/i });
  await loginButton.click();

  // The operator now completes IDIR auth (username, password, MFA) by hand.
  // The flow eventually redirects back to /dashboard with Amplify cookies set.
  await expect(page).toHaveURL(/\/dashboard$/i, { timeout: 5 * 60_000 });
  await page.getByTestId('bc-header__header').waitFor({ timeout: 60_000 });

  await page.context().storageState({ path: STORAGE_STATE });
});

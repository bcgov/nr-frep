import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';

import { test as setup, expect } from '@playwright/test';

import { SESSION_STORAGE_STATE, STORAGE_STATE } from './utils';

/**
 * Auth setup. Runs once per `playwright test` invocation, as a dependency of
 * every browser project. Three behaviours, in priority order:
 *
 *   1. e2e/.auth/user.json already exists (and E2E_FORCE_LOGIN is unset) → do
 *      nothing (cached state).
 *   2. E2E_IDIR_USER + E2E_IDIR_PASSWORD env vars are set → drive IDIR login
 *      programmatically. Used in CI (reusable-tests.yml passes these from
 *      GitHub Actions secrets) so the suite runs unattended.
 *   3. Neither of the above → fall back to the interactive flow: open the
 *      headed browser and wait up to 5 minutes for a human to complete the
 *      IDIR sign-in. Used locally via `npm run e2e:login`.
 *
 * <p><b>Two files are saved, not one.</b> Playwright's `storageState` covers cookies and
 * localStorage; `oidc-client-ts` keeps its tokens in **sessionStorage**, which `storageState` does
 * not touch at all. Saving only the former produces a state file that restores cleanly and leaves
 * every spec signed out. `e2e/fixtures.ts` restores the second file.
 *
 * <p><b>On programmatic login and MFA.</b> FREP's CSS integration selects IDIR - MFA. A second
 * factor is by construction something a script cannot supply, so the credentials path here works
 * only for an account exempt from MFA — a service account obtained from the IDIR team. Without one,
 * CI cannot log in at all and the honest options are an MFA-exempt account or an API-level test
 * strategy that never goes through the browser login. Find out which you have early; it decides the
 * shape of the whole e2e suite.
 */
setup('authenticate via IDIR', async ({ page }) => {
  if (
    existsSync(STORAGE_STATE) &&
    existsSync(SESSION_STORAGE_STATE) &&
    !process.env.E2E_FORCE_LOGIN
  ) {
    console.log(`Reusing existing auth state at ${STORAGE_STATE}`);
    return;
  }

  const idirUser = process.env.E2E_IDIR_USER;
  const idirPassword = process.env.E2E_IDIR_PASSWORD;
  const programmatic = Boolean(idirUser && idirPassword);

  await page.goto('/');

  // The landing page exposes a Log in button that starts the Keycloak flow.
  await page.getByTestId('landing-button__idir').click();

  if (programmatic) {
    // The BC Gov SSO login page is on a different origin than the SPA. `kc_idp_hint=azureidir`
    // sends the browser straight to the IDIR broker, so there is no provider chooser to click
    // through the way there was via the Cognito hosted UI.
    await page.waitForURL(/logon|loginproxy|microsoftonline/i, { timeout: 60_000 });

    // Selectors match the Logon7 / IDIR login form fields. If the upstream form ever changes its
    // `name` attributes, this is the place to update them.
    await page.locator('input[name="user"]').fill(idirUser!);
    await page.locator('input[name="password"]').fill(idirPassword!);
    await page.locator('input[type="submit"], button[type="submit"]').first().click();
  }

  // Whether interactive or programmatic, we wait for the redirect back to
  // /dashboard. Interactive flow gets 5 min for a human; programmatic gets 2.
  await page.waitForURL((url) => url.pathname.startsWith('/dashboard'), {
    timeout: programmatic ? 2 * 60_000 : 5 * 60_000,
  });

  // Sanity check: the Layout header renders once auth + roles resolve.
  await expect(page.getByTestId('bc-header__header')).toBeVisible({ timeout: 60_000 });

  await page.context().storageState({ path: STORAGE_STATE });

  // ...and the half storageState cannot see. Without this the tokens are simply not saved.
  const sessionEntries = await page.evaluate(() => ({ ...window.sessionStorage }));
  mkdirSync(dirname(SESSION_STORAGE_STATE), { recursive: true });
  writeFileSync(SESSION_STORAGE_STATE, JSON.stringify(sessionEntries, null, 2));
});

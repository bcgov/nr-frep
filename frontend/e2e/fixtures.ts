import { readFileSync, writeFileSync } from 'node:fs';

import { test as base } from '@playwright/test';

import { SESSION_STORAGE_STATE } from './utils';

/**
 * Playwright fixtures that carry the auth session across specs.
 *
 * <h3>Why this file exists</h3>
 * Playwright's `storageState` covers cookies and **localStorage only** — and `oidc-client-ts` keeps
 * its tokens in **sessionStorage**. So the saved `user.json` restores a browser that has everything
 * except the thing that proves who you are, and every spec lands on the logged-out landing page. The
 * snapshot has to be taken and restored by hand; that is what this is.
 *
 * <p>Import `test` from here rather than from `@playwright/test` in every spec.
 */

type SessionSnapshot = Record<string, string>;

const readSnapshot = (): SessionSnapshot => {
  try {
    return JSON.parse(readFileSync(SESSION_STORAGE_STATE, 'utf8')) as SessionSnapshot;
  } catch {
    return {};
  }
};

const writeSnapshot = (entries: SessionSnapshot): void => {
  // Never overwrite a good snapshot with an empty one: a spec that ends on a page which never
  // booted the app (a 404 check, a hard navigation failure) has an empty sessionStorage, and
  // persisting that would sign the rest of the run out.
  if (Object.keys(entries).length === 0) return;
  writeFileSync(SESSION_STORAGE_STATE, JSON.stringify(entries, null, 2));
};

export const test = base.extend<{ persistSession: void }>({
  /**
   * `page` is OVERRIDDEN, not wrapped in an auto fixture, because the init script has to be
   * registered before the page navigates anywhere — an auto fixture would run too late and the
   * first `goto` would already have booted the app with an empty sessionStorage.
   */
  page: async ({ page }, use) => {
    const snapshot = readSnapshot();
    await page.addInitScript((entries: SessionSnapshot) => {
      for (const [key, value] of Object.entries(entries)) {
        window.sessionStorage.setItem(key, value);
      }
    }, snapshot);
    await use(page);
  },

  /**
   * Persists the session back to disk after every test.
   *
   * <p>Not an optimisation — a correctness requirement. **Keycloak rotates the refresh token on
   * every renewal**, so the token spec 1 was given is dead the moment spec 1 renews. Without writing
   * the new one back, spec 2 restores a rotated-away token and fails on a 401 that has nothing to do
   * with what spec 2 is testing.
   */
  persistSession: [
    async ({ page }, use) => {
      await use();
      try {
        const entries = await page.evaluate(() => ({ ...window.sessionStorage }));
        writeSnapshot(entries as SessionSnapshot);
      } catch {
        // Page already closed, or navigated somewhere script evaluation is refused. The previous
        // snapshot stands.
      }
    },
    { auto: true },
  ],
});

export { expect } from '@playwright/test';

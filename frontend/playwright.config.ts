import { defineConfig, devices } from '@playwright/test';

import { baseURL, STORAGE_STATE } from './e2e/utils';

/**
 * Playwright E2E config — runs against deployed DEV by default.
 *
 * Auth flow:
 *   1. `npm run e2e:login` runs the `setup` project headed, parks at the IDIR
 *      login page, and once you sign in saves BOTH halves of the session:
 *      cookies + localStorage to e2e/.auth/user.json, and sessionStorage — where
 *      oidc-client-ts actually keeps the tokens, and which Playwright's own
 *      storageState does not capture — to e2e/.auth/session-storage.json.
 *   2. All other projects start from that storageState, with the sessionStorage
 *      half restored by the fixtures in e2e/fixtures.ts, so each test boots
 *      already-authenticated.
 *
 * Override the target with E2E_BASE_URL (e.g. http://localhost:3000 for local).
 */
export default defineConfig({
  timeout: 180_000,
  testDir: './e2e',
  // Serial execution. We share ONE refresh token across specs, and Keycloak rotates it on every
  // renewal — parallel workers race that rotation, and the loser is left holding a token that was
  // already spent, which surfaces as a context stuck on the white `<Loading>` overlay rather than as
  // anything that names the cause. Bump back up only alongside a way to mint per-worker auth (or a
  // mock strategy that never touches the realm).
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: [['line'], ['list', { printSteps: true }], ['html', { open: 'never' }]],
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        storageState: STORAGE_STATE,
      },
      dependencies: ['setup'],
    },
  ],
});

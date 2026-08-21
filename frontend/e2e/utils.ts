import path from 'node:path';

import { expect, type Page } from '@playwright/test';

// E2E_BASE_URL is set by `.github/workflows/reusable-tests.yml` at step
// level (resolved from the PR slot or test/prod target). For local hand-runs,
// export it explicitly:
//   E2E_BASE_URL=http://localhost:3000 npm run e2e
// Failing fast here beats silently targeting a stale dev URL.
if (!process.env.E2E_BASE_URL) {
  throw new Error(
    'E2E_BASE_URL is not set. Export it before running Playwright ' +
      '(e.g. `E2E_BASE_URL=http://localhost:3000 npm run e2e`).',
  );
}

export const baseURL = process.env.E2E_BASE_URL;

/**
 * Assert the SPA rendered a real page rather than falling through to the React
 * Router error boundary, which renders a "Global Error" heading
 * ({@code GlobalErrorPage}).
 */
export const expectNoGlobalError = async (page: Page): Promise<void> => {
  await expect(
    page.getByRole('heading', { name: 'Global Error' }),
    'global error boundary should not have rendered',
  ).toHaveCount(0);
};

/**
 * Wait until a data-loading page has settled — i.e. its `${prefix}-loading` skeleton is gone, leaving
 * the results table / empty message / error banner rendered. (Keying off the loading indicator avoids
 * relying on the table's data-testid, which Carbon's DataTable doesn't forward to the DOM.)
 */
export const waitForSettled = async (page: Page, prefix: string): Promise<void> => {
  await expect(
    page.getByTestId(`${prefix}-loading`),
    `${prefix} did not finish loading`,
  ).toBeHidden({ timeout: 60_000 });
};

/** Path to the saved auth state produced by auth.setup.ts. */
export const STORAGE_STATE = path.join(import.meta.dirname, '.auth', 'user.json');

/**
 * Unique-ish identifier suffix for test artifacts so concurrent runs and
 * leftover rows don't collide. Format: e2e-<base36 timestamp>-<rand>.
 */
export const uniqueSuffix = (): string => {
  const t = Date.now().toString(36);
  const r = Math.floor(Math.random() * 36 ** 4)
    .toString(36)
    .padStart(4, '0');
  return `e2e-${t}-${r}`;
};

/**
 * Navigate to a route on the protected side of the SPA and wait until the
 * Layout header has rendered — that means AuthProvider has finished
 * bootstrapping and we're past the white `<Loading withOverlay>` screen.
 *
 * On timeout, dumps current URL + a body excerpt so we can tell whether the
 * SPA stayed on the loading overlay, redirected to /unauthorized, crashed
 * into the global error boundary, etc.
 */
/**
 * Navigation failures worth another attempt: the PR slot momentarily has no ready pod, so the router
 * has nothing to send the request to. The frontend Deployment runs a single replica with a
 * `Recreate` strategy (the deployer action patches both), so any pod swap — a redeploy, a
 * reschedule — empties the Service's endpoint list and every request is refused until the
 * replacement is ready, then recovers fully.
 *
 * Deliberately narrow: only connection-level errors match. An assertion, a missing element or a
 * rendered error boundary is a real failure and still fails on the first attempt.
 */
const TRANSIENT_NAVIGATION =
  /net::ERR_CONNECTION_REFUSED|net::ERR_CONNECTION_RESET|net::ERR_CONNECTION_CLOSED|net::ERR_EMPTY_RESPONSE|net::ERR_TIMED_OUT|net::ERR_NAME_NOT_RESOLVED/;

/** How long to keep re-trying a refused connection before giving up on the slot. */
const TRANSIENT_RETRY_BUDGET_MS = 90_000;
const TRANSIENT_RETRY_GAP_MS = 10_000;

export const gotoProtected = async (page: Page, path: string): Promise<void> => {
  // Capture browser console messages and page errors during this navigation.
  const consoleMessages: string[] = [];
  const pageErrors: string[] = [];
  const onConsole = (msg: { type(): string; text(): string }) => {
    consoleMessages.push(`[${msg.type()}] ${msg.text()}`);
  };
  const onPageError = (err: Error) => {
    pageErrors.push(err.message);
  };
  page.on('console', onConsole);
  page.on('pageerror', onPageError);

  try {
    const deadline = Date.now() + TRANSIENT_RETRY_BUDGET_MS;
    for (let attempt = 1; ; attempt++) {
      try {
        await page.goto(path);
        await page.getByTestId('bc-header__header').waitFor({ timeout: 60_000 });
        break;
      } catch (navErr) {
        const message = navErr instanceof Error ? navErr.message : String(navErr);
        if (!TRANSIENT_NAVIGATION.test(message) || Date.now() >= deadline) throw navErr;
        // Logged so a report that eventually passes still shows the slot went away.
        console.warn(
          `gotoProtected("${path}") attempt ${attempt}: slot not accepting connections, retrying in ` +
            `${TRANSIENT_RETRY_GAP_MS / 1000}s — ${message.split('\n')[0]}`,
        );
        await page.waitForTimeout(TRANSIENT_RETRY_GAP_MS);
      }
    }
  } catch (err) {
    const url = page.url();
    const title = await page.title().catch(() => '(unavailable)');
    const readyState = await page.evaluate(() => document.readyState).catch(() => '(unavailable)');
    const bodyHTML = await page
      .evaluate(() => document.body?.innerHTML?.slice(0, 1500) ?? '(no body)')
      .catch(() => '(unavailable)');
    const rootContent = await page
      .evaluate(() => document.getElementById('root')?.innerHTML?.slice(0, 800) ?? '(no #root)')
      .catch(() => '(unavailable)');
    const loadingVisible = await page
      .getByTestId('loading')
      .isVisible()
      .catch(() => false);
    throw new Error(
      `gotoProtected("${path}") failed to find the Layout header.\n` +
        `  Current URL  : ${url}\n` +
        `  Page title   : ${title}\n` +
        `  readyState   : ${readyState}\n` +
        `  Loading?     : ${loadingVisible}\n` +
        `  #root excerpt: ${rootContent}\n` +
        `  body excerpt : ${bodyHTML}\n` +
        `  Console (${consoleMessages.length}): ${consoleMessages.slice(-15).join(' | ') || '(none)'}\n` +
        `  Page errors (${pageErrors.length}): ${pageErrors.join(' | ') || '(none)'}\n` +
        `  Original     : ${err instanceof Error ? err.message : String(err)}`,
    );
  } finally {
    page.off('console', onConsole);
    page.off('pageerror', onPageError);
  }
};

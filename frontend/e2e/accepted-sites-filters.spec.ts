import { expect, test } from '@playwright/test';

import { expectNoGlobalError, gotoProtected, waitForSettled } from './utils';

/**
 * Regression for the Accepted Sites filter-persistence bug: a tester working in the 25/26 master list
 * clicked into a checklist and, on Back, the page had reset to the current (26/27) master list. The
 * filters are now mirrored into the URL query string, so Back restores the year/district/protocol.
 *
 * Runs against deployed DEV. Picks whatever non-default year the environment offers (there are
 * normally several); with a single year there's nothing to switch to, so the tests just confirm the
 * page stayed healthy — mirroring the other specs' `if`-guard style rather than skipping.
 */
const yearSelect = (page: import('@playwright/test').Page) =>
  page.getByLabel('Master list year', { exact: true });

/** The year option value that isn't currently selected (or null if there's only one). */
const otherYearValue = async (page: import('@playwright/test').Page): Promise<string | null> => {
  const select = yearSelect(page);
  const current = await select.inputValue();
  const values = await select
    .locator('option')
    .evaluateAll((opts) => opts.map((o) => (o as HTMLOptionElement).value).filter(Boolean));
  return values.find((v) => v !== current) ?? null;
};

test.describe('Accepted Sites — filters persist across Back', () => {
  test('changing the master list year is reflected in the URL', async ({ page }) => {
    await gotoProtected(page, '/accepted-sites');
    await waitForSettled(page, 'accepted-sites');

    const other = await otherYearValue(page);
    if (other !== null) {
      await yearSelect(page).selectOption(other);
      await waitForSettled(page, 'accepted-sites');

      // The active filter is written to the query string — this is what a Back navigation restores.
      await expect(page).toHaveURL(new RegExp(`year=${other}`));
    }
    await expectNoGlobalError(page);
  });

  test('the selected year survives navigating into a checklist and pressing Back', async ({
    page,
  }) => {
    await gotoProtected(page, '/accepted-sites');
    await waitForSettled(page, 'accepted-sites');

    const other = await otherYearValue(page);
    if (other !== null) {
      await yearSelect(page).selectOption(other);
      await waitForSettled(page, 'accepted-sites');
      await expect(yearSelect(page)).toHaveValue(other);

      // Reproduce the reported flow: open a checklist, then Back. If the current filters return no
      // rows to click, fall back to any other protected page — the Back mechanism (URL params) is
      // the same.
      const checklistLink = page.locator('a[href^="/protocol-checklists/"]').first();
      if ((await checklistLink.count()) > 0) {
        await checklistLink.click();
        await page.waitForURL(/\/protocol-checklists\//);
      } else {
        await gotoProtected(page, '/random-list');
      }

      await page.goBack();
      await page.waitForURL(/\/accepted-sites/);
      await waitForSettled(page, 'accepted-sites');

      // Back restored the year the user was working in — not the current-master-list default.
      await expect(yearSelect(page)).toHaveValue(other);
    }
    await expectNoGlobalError(page);
  });
});

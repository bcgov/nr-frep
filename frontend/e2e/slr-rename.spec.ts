import { expect, test } from '@playwright/test';

import { expectNoGlobalError, gotoProtected, waitForSettled } from './utils';

/**
 * E2E contract for the Biodiversity → Stand Level Retention (SLB → SLR) rename.
 *
 * Premise: the `SLR` code exists in `FREP_RESOURCE_VALUE_TYPE_CODE` (added by the
 * nr-mof-db SLR migration). The protocol dropdowns read their options from
 * `getProtocols()`, which is backed by that table, so the "SLR - Stand Level
 * Retention" option does not exist until the DB change is deployed to the target
 * slot — these tests fail until then.
 *
 * NOTE: historical `SLB` records deliberately keep the label "Biodiversity" — the
 * relabel migration was dropped (decision 2026-08). "Biodiversity" is therefore a
 * legitimate string on result rows; the assertions below check the *dropdown
 * options*, which exclude SLB via each page's IN_SCOPE filter, and must not be
 * widened into page-wide text assertions.
 */

const SLR_LABEL = 'Stand Level Retention';
const OLD_LABEL = 'Biodiversity';

test.describe('SLB → SLR rename', () => {
  test('Checklist Search offers Stand Level Retention (SLR), not Biodiversity', async ({
    page,
  }) => {
    await gotoProtected(page, '/search/checklists');

    const protocol = page.locator('#checklist-search-protocol');
    await expect(protocol).toBeVisible();

    // The go-forward SLR option is present; the retired "Biodiversity" label is gone.
    await expect(protocol.locator('option', { hasText: SLR_LABEL })).toHaveCount(1);
    await expect(protocol.locator('option', { hasText: OLD_LABEL })).toHaveCount(0);

    // Selecting SLR and searching exercises the backend family-match (SLR filter →
    // historical SLB folded in). We assert it settles without hitting the error boundary
    // rather than asserting on data, since result rows depend on the slot's dataset.
    await protocol.selectOption('SLR');
    await page.getByRole('button', { name: 'Search', exact: true }).click();
    await waitForSettled(page, 'checklist-search');
    await expectNoGlobalError(page);
  });

  test('Accepted Sites offers Stand Level Retention (SLR), not Biodiversity', async ({ page }) => {
    await gotoProtected(page, '/accepted-sites');
    await waitForSettled(page, 'accepted-sites');

    const protocol = page.getByLabel('Protocol');
    await expect(protocol.locator('option', { hasText: SLR_LABEL })).toHaveCount(1);
    await expect(protocol.locator('option', { hasText: OLD_LABEL })).toHaveCount(0);

    // The SLR filter collapses the whole biodiversity family (SLB + SLR) server-side, so
    // historical SLB accepted sites still surface under SLR. Confirm the reload is clean.
    await protocol.selectOption('SLR');
    await waitForSettled(page, 'accepted-sites');
    await expectNoGlobalError(page);
  });

  test('Exports page shows the Stand Level Retention program name', async ({ page }) => {
    // The route is /exports (routePaths.tsx) and its H1 reads "Exports" — the page
    // component is named ReportsPage, which is why this test used to say /reports.
    await gotoProtected(page, '/exports');

    await expect(page.getByRole('heading', { name: 'Exports', level: 1 })).toBeVisible();
    // The five extract definitions were rebranded; the program name should be present.
    await expect(page.getByText(SLR_LABEL).first()).toBeVisible();
    await expectNoGlobalError(page);
  });

  /**
   * Record-level flows below need seeded, addressable data (a known historical SLB
   * checklist id and the ability to create a fresh SLR site), which the smoke slots
   * don't guarantee. They stay as `fixme` so they're tracked in the suite but not run
   * in CI — verify them manually on DEV/TEST as part of the Phase 6 cutover check.
   */
  test.fixme(
    'a historical SLB checklist opens read-only (view-only banner, no submit/unsubmit)',
    () => {
      // Manual: open a known SLB `/protocol-checklists/slr/:id`; expect the read-only banner,
      // no Submit/Unsubmit, and that a save attempt returns 403.
    },
  );

  test.fixme('a new biodiversity target site is created as SLR and is editable', () => {
    // Manual: create a biodiversity target site; expect the persisted record to be SLR and
    // fully editable (saves succeed).
  });
});

import { expect, test } from '@playwright/test';

import { expectNoGlobalError, gotoProtected, waitForSettled } from './utils';

/**
 * E2E contract for the Biodiversity → Stand Level Retention (SLB → SLR) rename.
 *
 * These assertions encode the POST-migration state: they pass only once the
 * `nr-mof-db` migrations `V202607031000.1__FREP_ADD_SLR_EXPIRE_SLB.sql` (adds the
 * `SLR` code) and `V202607031430.1__FREP_RELABEL_SLB_DESCRIPTION.sql` (relabels the
 * program to "Stand Level Retention") have been deployed to the target slot. The
 * protocol dropdowns read their options from `getProtocols()`, which is backed by
 * `FREP_RESOURCE_VALUE_TYPE_CODE` — so the "SLR - Stand Level Retention" option
 * does not exist until the DB change lands.
 *
 * ⇒ The frontend PR carrying this spec stays in DRAFT until the DB PR merges and
 *   deploys; flipping it to ready is the signal the coordinated release is green.
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

  test('Reports page shows the Stand Level Retention program name, not Biodiversity', async ({
    page,
  }) => {
    await gotoProtected(page, '/reports');

    await expect(page.getByRole('heading', { name: 'Reports', level: 1 })).toBeVisible();
    await expect(page.getByText(SLR_LABEL).first()).toBeVisible();
    await expect(page.getByText(OLD_LABEL)).toHaveCount(0);
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

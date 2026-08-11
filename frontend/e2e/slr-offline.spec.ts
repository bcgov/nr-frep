import { expect, test, type Page } from '@playwright/test';

import { expectNoGlobalError, gotoProtected, waitForSettled } from './utils';

/**
 * E2E round trip for Stand Level Retention offline mode: take offline → edit with no connectivity →
 * check in.
 *
 * **Premise: the nr-mof-db offline migrations must be deployed to the target slot.** Take-offline
 * claims a checkout through `FREP_TOMBSTONE.take_offline`, which needs both
 * `BIODIVERSITY_CHECKLIST.DEVICE_CHECKOUT_GUID` and the revved package. Until those land, the
 * download succeeds and the final checkout step fails — these tests fail with it. That is deliberate:
 * a green run here is the signal the whole feature is wired end to end.
 *
 * **Why this exists when the unit suite is large.** Everything below runs in a real browser against a
 * real backend, which is the only place three things are true at once: the five Bio views actually
 * route through the offline facade (the unit tests call the facade directly), IndexedDB survives a
 * genuine page reload, and "offline" means a network that is really down rather than a mocked
 * `navigator.onLine`. Those are exactly the seams that produced every escaped defect in this project.
 *
 * **Data safety.** The test picks an ACT SLR checklist from search, edits only the Opening tab's
 * location description, and always checks the copy back in — so the slot is left as it was found,
 * with the checklist active and its checkout released. The `finally` block is the important part: a
 * failed run must not strand a checkout on a slot everyone shares.
 */

const OFFLINE_LIST = '/protocol-checklists/offline';

/** Cut the browser off from the network, the way a device in the field is. */
const goOffline = async (page: Page): Promise<void> => {
  await page.context().setOffline(true);
};

const goOnline = async (page: Page): Promise<void> => {
  await page.context().setOffline(false);
};

/**
 * Find an ACT Stand Level Retention checklist on this slot and open it.
 *
 * Discovered rather than hardcoded: slot datasets differ, and a hardcoded id rots into a confusing
 * failure the first time someone reseeds. Returns null when the slot has none, so the caller can skip
 * rather than fail on a data gap that isn't a code defect.
 */
const openAnActiveSlrChecklist = async (page: Page): Promise<string | null> => {
  await gotoProtected(page, '/search/checklists');
  const protocol = page.locator('#checklist-search-protocol');
  await expect(protocol).toBeVisible();
  await protocol.selectOption('SLR');
  await page.getByRole('button', { name: 'Search', exact: true }).click();
  await waitForSettled(page, 'checklist-search');

  // The first row whose status is Active. A submitted or checked-out one cannot be taken offline.
  const activeRow = page.getByRole('row').filter({ hasText: 'Active' }).first();
  if (!(await activeRow.isVisible().catch(() => false))) return null;

  const link = activeRow.getByRole('link').first();
  const href = await link.getAttribute('href');
  await link.click();
  await expect(page.getByRole('button', { name: /Take offline|Check in/ })).toBeVisible({
    timeout: 30_000,
  });
  return href?.split('/').pop() ?? null;
};

/**
 * Match a value whether the field renders it as read-only text or back inside an input. Playwright
 * has no `getByDisplayValue`, and which one appears depends on the tab's edit mode after a reload.
 */
const shownAnywhere = (page: Page, value: string) =>
  page.getByText(value, { exact: false }).or(page.locator(`input[value="${value}"]`)).first();

/** Everything this device is holding, straight out of IndexedDB. */
const readOfflineRecord = (page: Page, checklistId: string) =>
  page.evaluate(async (id) => {
    const open = indexedDB.open('frep-bio');
    const db: IDBDatabase = await new Promise((resolve, reject) => {
      open.onsuccess = () => resolve(open.result);
      open.onerror = () => reject(open.error);
    });
    const row = await new Promise<unknown>((resolve, reject) => {
      const request = db.transaction('bioChecklists').objectStore('bioChecklists').get(id);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
    db.close();
    return row as { syncState?: string; deviceCheckoutGuid?: string } | undefined;
  }, checklistId);

test.describe('SLR offline round trip', () => {
  // A full round trip over a real network, including a download of every attachment.
  test.slow();

  test('take offline, edit with no connectivity, then check in', async ({ page }) => {
    const checklistId = await openAnActiveSlrChecklist(page);
    test.skip(!checklistId, 'This slot has no ACT Stand Level Retention checklist to take offline.');

    let checkedOut = false;
    try {
      // ── 1. Take offline ────────────────────────────────────────────
      await page.getByRole('button', { name: 'Take offline' }).click();
      await expect(page.getByRole('button', { name: 'Check in' })).toBeVisible({ timeout: 120_000 });
      checkedOut = true;

      // Stored, checked out, and marked clean — not merely rendered.
      const stored = await readOfflineRecord(page, checklistId!);
      expect(stored?.syncState).toBe('CLEAN');
      expect(stored?.deviceCheckoutGuid).toBeTruthy();

      // ── 2. Survive a reload, then edit with the network down ───────
      await goOffline(page);
      await page.reload();
      // The page must come back from IndexedDB with no network at all. This is the assertion that
      // the offline copy is real storage rather than React state.
      await expect(page.getByText('Saved on this device')).toBeVisible({ timeout: 60_000 });
      await expectNoGlobalError(page);

      const edited = `e2e offline edit ${Date.now()}`;
      await page.getByRole('button', { name: 'Edit' }).first().click();
      const location = page.getByLabel(/Location description/i).first();
      await location.fill(edited);
      await page.getByRole('button', { name: 'Save' }).first().click();

      // Saved locally, with no connectivity — and the record says so.
      await expect
        .poll(async () => (await readOfflineRecord(page, checklistId!))?.syncState, {
          timeout: 30_000,
        })
        .toBe('DIRTY');

      // The edit survives a second reload while still offline.
      await page.reload();
      await expect(page.getByText('Saved on this device')).toBeVisible({ timeout: 60_000 });
      // Either rendering is fine: the tab may show the saved value as read-only text or back in an
      // input, depending on which mode the reload lands in.
      await expect(shownAnywhere(page, edited)).toBeVisible();

      // ── 3. Check in ────────────────────────────────────────────────
      await goOnline(page);
      await page.reload();
      await page.getByRole('button', { name: 'Check in' }).click();

      // Take offline returning means the copy is gone and the page is back on the server record.
      await expect(page.getByRole('button', { name: 'Take offline' })).toBeVisible({
        timeout: 120_000,
      });
      checkedOut = false;

      // The local copy is gone from the device, not merely hidden.
      expect(await readOfflineRecord(page, checklistId!)).toBeUndefined();
      await expectNoGlobalError(page);

      // ── 4. The edit reached the server ─────────────────────────────
      await page.reload();
      await expect(shownAnywhere(page, edited)).toBeVisible({ timeout: 60_000 });
    } finally {
      // Never leave a checkout stranded on a shared slot: a failed run would otherwise make the
      // checklist read-only for everyone until an admin reactivated it.
      await goOnline(page).catch(() => undefined);
      if (checkedOut) {
        await gotoProtected(page, OFFLINE_LIST).catch(() => undefined);
        await page
          .getByRole('button', { name: 'Remove from device' })
          .first()
          .click({ timeout: 15_000 })
          .catch(() => undefined);
      }
    }
  });
});

test.describe('SLR offline list', () => {
  test('renders and is reachable while offline', async ({ page }) => {
    // The offline routes are meant to serve without a network — that is service-worker behaviour, so
    // it can only be checked in a real browser.
    await gotoProtected(page, OFFLINE_LIST);
    await expect(
      page.getByRole('heading', { name: 'Offline checklists', level: 1 }),
    ).toBeVisible();
    await expectNoGlobalError(page);

    await goOffline(page);
    await page.reload();
    await expect(
      page.getByRole('heading', { name: 'Offline checklists', level: 1 }),
    ).toBeVisible({ timeout: 60_000 });
    await expectNoGlobalError(page);
    await goOnline(page);
  });
});

test.describe('SLR checked-out checklist', () => {
  test('an interrupted take-offline leaves no checkout behind', async ({ page }) => {
    // The reads-first/checkout-last guarantee, which is the reason the snapshot GET does not claim
    // the checkout. Killing the network mid-download must cost nothing on either side.
    const checklistId = await openAnActiveSlrChecklist(page);
    test.skip(!checklistId, 'This slot has no ACT Stand Level Retention checklist to take offline.');

    await page.getByRole('button', { name: 'Take offline' }).click();
    // Cut it off while the download is still running, before the checkout step.
    await goOffline(page);

    // The button must come back — no local copy was stored.
    await expect(page.getByRole('button', { name: /Take offline/ })).toBeVisible({
      timeout: 60_000,
    });
    expect(await readOfflineRecord(page, checklistId!)).toBeUndefined();

    await goOnline(page);
    await page.reload();
    // And the server still considers it editable: no read-only banner, so no checkout was claimed.
    await expect(page.getByText('checked out to a field device')).toHaveCount(0);
    await expectNoGlobalError(page);
  });
});

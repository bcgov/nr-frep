import { expect, test } from '@playwright/test';

import { expectNoGlobalError, gotoProtected, waitForSettled } from './utils';

/**
 * Regression for the CHR Features → Age fix (reported bug: two age types could be selected at once).
 * Age is single-select: once one box is checked, the other three are disabled until it's unchecked.
 *
 * Runs against deployed DEV: discovers an Active CHR checklist via Checklist Search (no hardcoded id),
 * opens the Features tab, and adds a throwaway feature so the editor is reachable regardless of the
 * record's existing features. The feature is discarded with Cancel — nothing is saved, so DEV data is
 * left untouched. When no editable CHR checklist is available it still asserts the search flow stayed
 * healthy (mirrors the other specs' `if`-guard style rather than skipping).
 */
const AGE_LABELS = ['Pre-1846', 'Post-1846', 'Age unknown', 'Historical use'] as const;

test.describe('CHR Features — Age is single-select', () => {
  test('selecting one age disables the other three', async ({ page }) => {
    // 1. Find an Active CHR checklist through the search page.
    await gotoProtected(page, '/search/checklists');
    await page.getByLabel('Protocol', { exact: true }).selectOption('CHR');
    await page.getByLabel('Status', { exact: true }).selectOption('ACT');
    await page.getByRole('button', { name: 'Search', exact: true }).click();
    await waitForSettled(page, 'checklist-search');

    const firstChr = page.locator('a[href^="/protocol-checklists/chr/"]').first();
    if ((await firstChr.count()) > 0) {
      await firstChr.click();

      // 2. Open the Features tab. Only exercise the editor when the checklist is editable for this user.
      await page.getByRole('tab', { name: 'Features' }).click();
      const addFeature = page.getByRole('button', { name: 'Add feature' });
      if (await addFeature.isVisible().catch(() => false)) {
        // 3. Add a throwaway feature to reach the editor, then open the Age accordion.
        await addFeature.click();
        await page.getByRole('button', { name: 'Age' }).click();

        const ageBox = (name: string) => page.getByRole('checkbox', { name });

        // All four start enabled (no age selected yet).
        for (const label of AGE_LABELS) {
          await expect(ageBox(label)).toBeEnabled();
        }

        // 4. Select the first age — the other three must become disabled.
        await ageBox('Pre-1846').check();
        await expect(ageBox('Pre-1846')).toBeChecked();
        for (const label of ['Post-1846', 'Age unknown', 'Historical use']) {
          await expect(ageBox(label)).toBeDisabled();
        }

        // 5. Unchecking the active age re-enables the whole group.
        await ageBox('Pre-1846').uncheck();
        for (const label of AGE_LABELS) {
          await expect(ageBox(label)).toBeEnabled();
        }

        // 6. Discard the throwaway feature — no save, so DEV data is untouched.
        await page.getByRole('button', { name: 'Cancel' }).click();
      }
    }

    await expectNoGlobalError(page);
  });
});

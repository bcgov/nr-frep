import { expect, test } from '@playwright/test';

import { expectNoGlobalError, gotoProtected, waitForSettled } from './utils';

/**
 * Regression for the CHR Features → Age fix (reported bug: two age types could be selected at once).
 * A feature has one age, so it is asked as one: a radio group, not four checkboxes.
 *
 * The original fix kept the checkboxes and disabled the other three once one was ticked, which meant
 * switching age required unticking the old one first. #104 replaced them with radios, so single-select
 * is now structural — and this spec asserts the behaviour that replaced it: selecting a different age
 * *moves* the selection, and the unselected options stay enabled rather than becoming disabled.
 *
 * Runs against deployed DEV: discovers an Active CHR checklist via Checklist Search (no hardcoded id),
 * opens the Features tab, and adds a throwaway feature so the editor is reachable regardless of the
 * record's existing features. The feature is discarded with Cancel — nothing is saved, so DEV data is
 * left untouched. When no editable CHR checklist is available it still asserts the search flow stayed
 * healthy (mirrors the other specs' `if`-guard style rather than skipping).
 */
const AGE_LABELS = ['Pre-1846', 'Post-1846', 'Age unknown', 'Historical use'] as const;

test.describe('CHR Features — Age is single-select', () => {
  test('selecting an age moves the selection rather than adding to it', async ({ page }) => {
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
        // 3. Add a throwaway feature to reach the editor. The Age options are in a plain section —
        //    #104 replaced the accordions with flat sections, so there is nothing to expand first.
        await addFeature.click();

        const ageRadio = (name: string) => page.getByRole('radio', { name, exact: true });

        // All four are offered and none is selected on a new feature.
        for (const label of AGE_LABELS) {
          await expect(ageRadio(label)).toBeEnabled();
          await expect(ageRadio(label)).not.toBeChecked();
        }

        // 4. Select the first age. `force` is required: Carbon renders the real <input> visually
        //    hidden/offscreen, so Playwright can't scroll it into the viewport to click — but it's
        //    still the radio and the change handler still fires.
        await ageRadio('Pre-1846').check({ force: true });
        await expect(ageRadio('Pre-1846')).toBeChecked();

        // The other three are unselected — and, unlike the checkbox version this replaced, still
        // enabled. Asserting that is the point: it is what makes step 5 possible in one click.
        for (const label of ['Post-1846', 'Age unknown', 'Historical use']) {
          await expect(ageRadio(label)).not.toBeChecked();
          await expect(ageRadio(label)).toBeEnabled();
        }

        // 5. Switching age moves the selection. Two ages must never be selected at once — the
        //    original bug — and no unticking step is needed to get here.
        await ageRadio('Age unknown').check({ force: true });
        await expect(ageRadio('Age unknown')).toBeChecked();
        for (const label of ['Pre-1846', 'Post-1846', 'Historical use']) {
          await expect(ageRadio(label)).not.toBeChecked();
        }

        // 6. Discard the throwaway feature — no save, so DEV data is untouched.
        await page.getByRole('button', { name: 'Cancel', exact: true }).click();
      }
    }

    await expectNoGlobalError(page);
  });
});

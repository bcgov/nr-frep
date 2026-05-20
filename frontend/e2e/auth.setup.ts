import { test } from '@playwright/test';

/**
 * LOCAL DEV: Cognito / IDIR auth bootstrap disabled.
 * Re-enable the implementation below before running e2e against a deployed environment.
 */
test('authenticate via IDIR', async () => {
  // No-op while local auth is disabled.
});

/*
import { existsSync } from 'node:fs';
import { test as setup, expect } from '@playwright/test';
import { STORAGE_STATE } from './utils';

setup('authenticate via IDIR', async ({ page }) => {
  ... original IDIR login flow ...
});
*/

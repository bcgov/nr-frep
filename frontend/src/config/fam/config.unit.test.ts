import { describe, expect, it } from 'vitest';

import { redirectSignOut } from './config';

describe('redirectSignOut', () => {
  it('has no trailing slash', () => {
    // Cognito matches "Allowed sign-out URLs" by exact string, and the registered values are bare
    // origins. Appending a slash made the fallback signOut() send a URL Cognito had never seen, and
    // it answered "Required parameters missing" — an error that names neither the parameter nor the
    // real cause. Local dev hid it: the federated chain is configured there, so the fallback that
    // uses this value never runs.
    expect(redirectSignOut.endsWith('/')).toBe(false);
  });

  it('is the bare origin, matching what the federated chain sends', () => {
    // Both logout paths have to return to the same registered URL: the chain passes
    // window.location.origin, so this must resolve to the same string when no base path is set.
    expect(redirectSignOut).toBe(window.location.origin);
  });
});

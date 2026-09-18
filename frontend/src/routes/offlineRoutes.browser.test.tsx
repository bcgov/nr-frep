import { describe, expect, it } from 'vitest';

import { getOfflineRoutes, OFFLINE_PATHS, PROTECTED_ROUTES } from './routePaths';

/**
 * Guards the offline route set against the failure it has already had once.
 *
 * `getOfflineRoutes` picks routes by exact-matching their `path` against a hardcoded string set. A
 * route whose path is renamed keeps working online and silently disappears offline — it falls to the
 * catch-all, which redirects to the landing page. That is what happened to the SLR checklist when
 * the generic `/protocol-checklists/:protocolType/:checklistId` became `/protocol-checklists/slr/:id`
 * and the set was not updated: offline, opening an SLR copy from the offline list bounced to the
 * landing page, while CHR — whose path still matched — was fine.
 *
 * Nothing else catches this. The page renders, the link is right, and both are online-only checks.
 */
describe('offline route set', () => {
  const offlinePaths = getOfflineRoutes().map((route) => route.path);
  const realPaths = new Set(PROTECTED_ROUTES.map((route) => route.path));

  it.each([
    ['the unified offline list', '/offline'],
    ['the CHR checklist', '/protocol-checklists/chr/:id'],
    ['the SLR checklist', '/protocol-checklists/slr/:id'],
  ])('serves %s while offline', (_label, path) => {
    expect(offlinePaths).toContain(path);
  });

  it('names only routes that exist, so a renamed path cannot drop out silently', () => {
    // Asserted against OFFLINE_PATHS itself, not against what the filter returned. A set entry
    // naming a route that no longer exists produces *no* route, so it is invisible downstream —
    // which is precisely how the SLR path rotted unnoticed. Checking the output here would pass
    // while the bug was present; this fails, naming the dead entry.
    const dead = [...OFFLINE_PATHS].filter((path) => !realPaths.has(path));
    expect(dead).toEqual([]);
  });

  it('ends in a catch-all, which is what makes a missing route look like a redirect loop', () => {
    expect(offlinePaths[offlinePaths.length - 1]).toBe('*');
  });
});

import { describe, it, expect } from 'vitest';

import * as routePaths from './routePaths';

describe('routePaths', () => {
  it('getMenuEntries returns the dashboard menu item', () => {
    const entries = routePaths.getMenuEntries([]);
    expect(Array.isArray(entries)).toBe(true);
    expect(entries.some((e) => e.id === 'Dashboard')).toBe(true);
    expect(entries).toHaveLength(1);
  });

  it('getPublicRoutes returns the unauthenticated route set', () => {
    const result = routePaths.getPublicRoutes();
    expect(result.some((r) => r.id === 'Landing')).toBe(true);
    expect(result.some((r) => r.id === 'Unauthorized')).toBe(true);
    expect(result.some((r) => r.id === 'Not Found')).toBe(true);
  });

  it('getProtectedRoutes returns protected and system routes', () => {
    const result = routePaths.getProtectedRoutes();
    expect(Array.isArray(result)).toBe(true);
    expect(result.some((r) => r.id === 'Dashboard')).toBe(true);
  });

  it('getNoRoleRoutes returns the unauthorized route plus a catch-all redirect', () => {
    const result = routePaths.getNoRoleRoutes();
    expect(result.some((r) => r.id === 'Unauthorized')).toBe(true);
    expect(result.some((r) => r.path === '*')).toBe(true);
    expect(result.some((r) => r.id === 'Dashboard')).toBe(false);
  });
});

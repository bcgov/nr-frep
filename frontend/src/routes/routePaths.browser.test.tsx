import { describe, it, expect } from 'vitest';

import * as routePaths from './routePaths';

describe('routePaths', () => {
  it('getMenuEntries returns the dashboard and ported FREP screens for non-admin', () => {
    const entries = routePaths.getMenuEntries(['FREP_VIEW_ONLY']);
    expect(Array.isArray(entries)).toBe(true);
    expect(entries.some((e) => e.id === 'Welcome')).toBe(true);
    expect(entries.some((e) => e.id === 'Dashboard')).toBe(true);
    expect(entries.some((e) => e.id === 'District Random List')).toBe(true);
    expect(entries.some((e) => e.id === 'Accepted Sites')).toBe(true);
    expect(entries.some((e) => e.id === 'Checklist Search')).toBe(true);
    expect(entries.some((e) => e.id === 'Client Search')).toBe(true);
    expect(entries.some((e) => e.id === 'Site Details')).toBe(false);
    expect(entries.some((e) => e.id === 'Protocol Checklist')).toBe(false);
    expect(entries.some((e) => e.id === 'Generate Master List')).toBe(false);
  });

  it('getMenuEntries exposes Generate Master List to sys-admins', () => {
    const entries = routePaths.getMenuEntries(['FREP_ADMIN']);
    expect(entries.some((e) => e.id === 'Generate Master List')).toBe(true);
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

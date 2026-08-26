import { renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { useAuthorization } from './index';

import type { FamLoginUser, ROLE_TYPE } from '@/context/auth/types';

vi.mock('@/context/auth/useAuth', () => ({
  useAuth: vi.fn(),
}));

import { useAuth } from '@/context/auth/useAuth';

const mockUseAuth = vi.mocked(useAuth);

function withUser(user: Partial<FamLoginUser>) {
  mockUseAuth.mockReturnValue({
    user: { roles: [], privileges: {}, ...user } as FamLoginUser,
    isLoggedIn: true,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
    ensureFreshToken: vi.fn(),
    forceRefreshSession: vi.fn(),
  });
}

function withRoles(roles: FamLoginUser['roles']) {
  withUser({ roles });
}

describe('useAuthorization (legacy WebADE role parity)', () => {
  it('grants full write and admin actions to FREP_ADMIN', () => {
    withRoles(['FREP_ADMIN']);

    const { result } = renderHook(() => useAuthorization());

    expect(result.current).toMatchObject({
      isSysAdmin: true,
      isUpdate: false,
      hasAnyRole: true,
      canEdit: true,
      canCreate: true,
      canDelete: true,
      canPerformSysAdminActions: true,
    });
  });

  it('grants write but not admin-only actions to FREP_EDITOR', () => {
    withRoles(['FREP_EDITOR']);

    const { result } = renderHook(() => useAuthorization());

    expect(result.current).toMatchObject({
      isSysAdmin: false,
      isUpdate: true,
      hasAnyRole: true,
      canEdit: true,
      canPerformSysAdminActions: false,
    });
  });

  it('does not admit a user holding only the retired read-only group', () => {
    // FREP_VIEW_ONLY has been retired from FREP. It used to count toward hasAnyRole, so a stale
    // account carrying only that group would still have been let into an app it can do nothing in.
    withRoles(['FREP_VIEW_ONLY' as ROLE_TYPE]);

    const { result } = renderHook(() => useAuthorization());

    expect(result.current).toMatchObject({
      isSysAdmin: false,
      isUpdate: false,
      hasAnyRole: false,
      canEdit: false,
      canCreate: false,
      canDelete: false,
      canPerformSysAdminActions: false,
    });
  });

  it('admits a CHR-district editor, who holds no global role', () => {
    // The only caller with no global FREP role who should still get in.
    withRoles([]);
    (useAuth as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
      user: { roles: [], privileges: { FREP_CHR_EDITOR: ['DCK'] } },
    });

    const { result } = renderHook(() => useAuthorization());

    expect(result.current.hasAnyRole).toBe(true);
    expect(result.current.canEdit).toBe(false);
    expect(result.current.canChr('DCK')).toBe(true);
  });

  /**
   * Legacy WebADE action_lnk parity (scripts/5.0.0/00/webade/webade_inserts.sql):
   * - SUBMITCHECKLIST, TAKECHECKLISTOFFLINE, UNSUBMITCHECKLIST: SYS_ADMIN + UPDATE
   * - ACTIVATECHECKLIST: SYS_ADMIN only
   * - CHECKLIST, ACCEPTEDSITES: both remaining roles (the legacy read-only role is retired)
   */
  it('maps legacy write actions to sys-admin and update roles', () => {
    const writeActionsRoles: Array<FamLoginUser['roles']> = [['FREP_ADMIN'], ['FREP_EDITOR']];

    for (const roles of writeActionsRoles) {
      withRoles(roles);
      const { result } = renderHook(() => useAuthorization());
      expect(result.current.canEdit).toBe(true);
    }
  });

  it('maps legacy ACTIVATECHECKLIST to sys-admin only', () => {
    withRoles(['FREP_EDITOR']);
    expect(renderHook(() => useAuthorization()).result.current.canPerformSysAdminActions).toBe(
      false,
    );

    withRoles(['FREP_ADMIN']);
    expect(renderHook(() => useAuthorization()).result.current.canPerformSysAdminActions).toBe(
      true,
    );
  });
});

describe('useAuthorization — protocol + district scope', () => {
  it('a CHR-district-only user sees CHR (their districts) but not Bio', () => {
    withUser({ roles: ['FREP_CHR_EDITOR'], privileges: { FREP_CHR_EDITOR: ['DCK', 'DCC'] } });

    const { result } = renderHook(() => useAuthorization());

    expect(result.current.hasAnyRole).toBe(true); // not treated as "no role"
    expect(result.current.canEdit).toBe(false); // no Bio access
    expect(result.current.canAnyChr).toBe(true);
    expect(result.current.chrDistricts).toEqual(['DCK', 'DCC']);
    expect(result.current.canChr('DCK')).toBe(true);
    expect(result.current.canChr('dcc')).toBe(true); // case-insensitive
    expect(result.current.canChr('DSE')).toBe(false); // other district
  });

  it('a FREP_EDITOR (Bio) user sees Bio but no CHR', () => {
    withRoles(['FREP_EDITOR']);

    const { result } = renderHook(() => useAuthorization());

    expect(result.current.canEdit).toBe(true);
    expect(result.current.canAnyChr).toBe(false);
    expect(result.current.canChr('DCK')).toBe(false);
  });

  it('sys-admin sees Bio and every CHR district', () => {
    withRoles(['FREP_ADMIN']);

    const { result } = renderHook(() => useAuthorization());

    expect(result.current.canEdit).toBe(true);
    expect(result.current.canAnyChr).toBe(true);
    expect(result.current.canChr('DCK')).toBe(true);
    expect(result.current.canChr('ANYTHING')).toBe(true);
  });

  // Site records are shared across protocols, so site editing is deliberately wider than canEdit:
  // a CHR district editor maintains the checklists hanging off a site and must be able to edit it.
  it('a CHR-district-only user may edit site details without gaining Bio write', () => {
    withUser({ roles: ['FREP_CHR_EDITOR'], privileges: { FREP_CHR_EDITOR: ['DCK'] } });

    const { result } = renderHook(() => useAuthorization());

    expect(result.current.canEditSite).toBe(true);
    expect(result.current.canEdit).toBe(false);
    expect(result.current.canCreate).toBe(false); // Add Target Site stays editor-only
  });

  it.each([
    ['FREP_EDITOR', true],
    ['FREP_ADMIN', true],
    // A group FREP no longer issues: it must grant nothing rather than fall through to a default.
    ['FREP_VIEW_ONLY', false],
  ])('canEditSite for %s is %s', (role, expected) => {
    withRoles([role as never]);

    const { result } = renderHook(() => useAuthorization());

    expect(result.current.canEditSite).toBe(expected);
  });
});

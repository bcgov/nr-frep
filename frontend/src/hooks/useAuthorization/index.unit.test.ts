import { renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { useAuthorization } from './index';

import type { FamLoginUser } from '@/context/auth/types';

vi.mock('@/context/auth/useAuth', () => ({
  useAuth: vi.fn(),
}));

import { useAuth } from '@/context/auth/useAuth';

const mockUseAuth = vi.mocked(useAuth);

function withRoles(roles: FamLoginUser['roles']) {
  mockUseAuth.mockReturnValue({
    user: { roles, privileges: {} },
    isLoggedIn: true,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
    userToken: vi.fn(),
    ensureFreshToken: vi.fn(),
  });
}

describe('useAuthorization (legacy WebADE role parity)', () => {
  it('grants full write and admin actions to FREP_ADMIN', () => {
    withRoles(['FREP_ADMIN']);

    const { result } = renderHook(() => useAuthorization());

    expect(result.current).toMatchObject({
      isSysAdmin: true,
      isUpdate: false,
      isViewOnly: false,
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
      isViewOnly: false,
      hasAnyRole: true,
      canEdit: true,
      canPerformSysAdminActions: false,
    });
  });

  it('treats FREP_VIEW_ONLY as read-only', () => {
    withRoles(['FREP_VIEW_ONLY']);

    const { result } = renderHook(() => useAuthorization());

    expect(result.current).toMatchObject({
      isSysAdmin: false,
      isUpdate: false,
      isViewOnly: true,
      hasAnyRole: true,
      canEdit: false,
      canCreate: false,
      canDelete: false,
      canPerformSysAdminActions: false,
    });
  });

  it('does not treat FREP_VIEW_ONLY as view-only when FREP_EDITOR is also present', () => {
    withRoles(['FREP_VIEW_ONLY', 'FREP_EDITOR']);

    const { result } = renderHook(() => useAuthorization());

    expect(result.current.isViewOnly).toBe(false);
    expect(result.current.canEdit).toBe(true);
  });

  it('does not treat FREP_VIEW_ONLY as view-only when FREP_ADMIN is also present', () => {
    withRoles(['FREP_VIEW_ONLY', 'FREP_ADMIN']);

    const { result } = renderHook(() => useAuthorization());

    expect(result.current.isViewOnly).toBe(false);
    expect(result.current.canPerformSysAdminActions).toBe(true);
  });

  /**
   * Legacy WebADE action_lnk parity (scripts/5.0.0/00/webade/webade_inserts.sql):
   * - SUBMITCHECKLIST, TAKECHECKLISTOFFLINE, UNSUBMITCHECKLIST: SYS_ADMIN + UPDATE
   * - ACTIVATECHECKLIST: SYS_ADMIN only
   * - CHECKLIST, ACCEPTEDSITES: all three roles (view-only may read, not POST-save)
   */
  it('maps legacy write actions to sys-admin and update roles', () => {
    const writeActionsRoles: Array<FamLoginUser['roles']> = [['FREP_ADMIN'], ['FREP_EDITOR']];

    for (const roles of writeActionsRoles) {
      withRoles(roles);
      const { result } = renderHook(() => useAuthorization());
      expect(result.current.canEdit).toBe(true);
    }

    withRoles(['FREP_VIEW_ONLY']);
    expect(renderHook(() => useAuthorization()).result.current.canEdit).toBe(false);
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

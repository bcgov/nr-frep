import { useMemo } from 'react';

import { useAuth } from '@/context/auth/useAuth';

import type { FamLoginUser, ROLE_TYPE } from '@/context/auth/types';

/**
 * Authorization result returned by {@link useAuthorization}.
 *
 * Role semantics mirror legacy WebADE ({@code FrepUser} / {@code RestAction}).
 */
export type AuthorizationInfo = {
  /** `true` when the user holds the `FREP_ADMIN` Cognito group. */
  isSysAdmin: boolean;
  /** `true` when the user holds the `FREP_EDITOR` Cognito group. */
  isUpdate: boolean;
  /**
   * `true` when the user is view-only: has `FREP_VIEW_ONLY` without
   * `FREP_ADMIN` or `FREP_EDITOR` (legacy {@code isViewOnlyUser}).
   */
  isViewOnly: boolean;
  /** `true` when the user has at least one recognized FREP role. */
  hasAnyRole: boolean;
  /** `true` when the user can perform write operations (sys-admin or update). */
  canEdit: boolean;
  /** `true` when the user can create new resources. Alias for {@link canEdit}. */
  canCreate: boolean;
  /** `true` when the user can delete resources. Alias for {@link canEdit}. */
  canDelete: boolean;
  /** `true` for admin-only actions (legacy {@code ACTIVATECHECKLIST} parity). */
  canPerformSysAdminActions: boolean;
  /** Checks if the user holds a specific role. */
  hasRole: (role: ROLE_TYPE) => boolean;
  /** The full user object for advanced checks (may be `undefined` before login). */
  user: FamLoginUser | undefined;
};

/**
 * Hook that provides role-based authorization helpers derived from the
 * authenticated user's Cognito groups.
 *
 * @example
 * ```tsx
 * const { isSysAdmin, canEdit } = useAuthorization();
 *
 * return (
 *   <>
 *     {canEdit && <Button>Edit</Button>}
 *     {isSysAdmin && <Link to="/admin">Admin</Link>}
 *   </>
 * );
 * ```
 */
export const useAuthorization = (): AuthorizationInfo => {
  const { user } = useAuth();

  return useMemo<AuthorizationInfo>(() => {
    const roles = user?.roles ?? [];
    const isSysAdmin = roles.includes('FREP_ADMIN');
    const isUpdate = roles.includes('FREP_EDITOR');
    const hasViewOnlyRole = roles.includes('FREP_VIEW_ONLY');
    const isViewOnly = hasViewOnlyRole && !isSysAdmin && !isUpdate;
    const hasAnyRole = isSysAdmin || isUpdate || hasViewOnlyRole;

    const canEdit = isSysAdmin || isUpdate;
    const canCreate = canEdit;
    const canDelete = canEdit;
    const canPerformSysAdminActions = isSysAdmin;

    const hasRole = (role: ROLE_TYPE) => roles.includes(role);

    return {
      isSysAdmin,
      isUpdate,
      isViewOnly,
      hasAnyRole,
      canEdit,
      canCreate,
      canDelete,
      canPerformSysAdminActions,
      hasRole,
      user,
    };
  }, [user]);
};

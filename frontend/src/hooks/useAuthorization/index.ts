import { useMemo } from 'react';

import type { FamLoginUser, ROLE_TYPE } from '@/context/auth/types';

import { useAuth } from '@/context/auth/useAuth';

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
  /** The 3-letter district codes the user may access CHR for (from `FREP_CHR_EDITOR_DISTRICT_*`). */
  chrDistricts: string[];
  /** `true` when the user may access CHR for any district (sys-admin, or holds ≥1 district role). */
  canAnyChr: boolean;
  /** `true` when the user may access CHR for the given 3-letter district `org_unit_code`. */
  canChr: (orgUnitCode: string | undefined | null) => boolean;
  /**
   * `true` when the user may edit a site's resources (FREP110 Site Details) — editors *or* any
   * per-district CHR editor. Broader than {@link canEdit}: site records are shared across protocols,
   * so a CHR district editor maintaining their districts' checklists can edit the sites those
   * checklists hang off. Mirrors `LoggedUserHelper.canEditSite()`. Creating a targeted site
   * (FREP200) still requires {@link canCreate}.
   */
  canEditSite: boolean;
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
    // Per-district CHR access: the FREP_CHR_EDITOR privilege value is the list of district codes.
    const chrDistricts = user?.privileges?.FREP_CHR_EDITOR ?? [];
    const canAnyChr = isSysAdmin || chrDistricts.length > 0;
    const canChr = (orgUnitCode: string | undefined | null) =>
      isSysAdmin || (!!orgUnitCode && chrDistricts.includes(orgUnitCode.toUpperCase()));

    // A CHR-district-only user holds no base role, so include CHR access here — otherwise they'd be
    // treated as having no role and routed away from the app. FREP_VIEW_ONLY used to count here
    // too; it has been retired, so holding only that group no longer admits anyone.
    const hasAnyRole = isSysAdmin || isUpdate || canAnyChr;

    const canEdit = isSysAdmin || isUpdate;
    const canCreate = canEdit;
    const canDelete = canEdit;
    const canEditSite = canEdit || canAnyChr;
    const canPerformSysAdminActions = isSysAdmin;

    const hasRole = (role: ROLE_TYPE) => roles.includes(role);

    return {
      isSysAdmin,
      isUpdate,
      hasAnyRole,
      canEdit,
      canCreate,
      canDelete,
      canPerformSysAdminActions,
      chrDistricts,
      canAnyChr,
      canChr,
      canEditSite,
      hasRole,
      user,
    };
  }, [user]);
};

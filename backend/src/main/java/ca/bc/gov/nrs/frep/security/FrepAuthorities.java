package ca.bc.gov.nrs.frep.security;

/**
 * SpEL authorization expressions for {@code @PreAuthorize} on the API endpoints. Modelled on the
 * nr-fspts {@code FspAuthorities} pattern (one constant per access level, referenced from each
 * endpoint), adapted to nr-frep's authority naming: the Cognito-groups converter exposes authorities
 * <em>without</em> the {@code ROLE_} prefix (see {@code Oauth2SecurityCustomizer}), so these use
 * {@code hasAnyAuthority(...)} rather than {@code hasAnyRole(...)}.
 *
 * @see RoleConstants for the underlying authority strings and legacy WebADE parity.
 */
public final class FrepAuthorities {

  private FrepAuthorities() {}

  /**
   * Roles permitted to create or modify FREP content (checklists, site evaluations, etc.):
   * {@code FREP_ADMIN} and {@code FREP_EDITOR}. Read-only ({@code FREP_VIEW_ONLY}) is excluded.
   */
  public static final String CONTENT_EDIT = "hasAnyAuthority('FREP_ADMIN','FREP_EDITOR')";

  /**
   * Sys-admin-only actions (FREP700 master-list administration, checklist activation): {@code FREP_ADMIN}.
   */
  public static final String ADMIN = "hasAuthority('FREP_ADMIN')";

  /**
   * CHR checklist edit/submit: {@code FREP_ADMIN} or any per-district CHR editor role
   * ({@code FREP_CHR_EDITOR_DISTRICT_*}). A global {@code FREP_EDITOR} (Biodiversity) is intentionally
   * excluded — CHR access is district-scoped. This is the coarse gate; the specific district is
   * enforced in the service layer against the checklist's org unit. Evaluated via the {@code @auth}
   * bean ({@link LoggedUserHelper}).
   */
  public static final String CHR_EDIT = "@auth.canAnyChr()";

  /**
   * FREP editor access: {@code FREP_ADMIN} or {@code FREP_EDITOR}. Gates the protocol-checklist
   * (Biodiversity) reads (writes use {@link #CONTENT_EDIT}, which is equivalent).
   */
  public static final String FREP_EDIT = "hasAnyAuthority('FREP_ADMIN','FREP_EDITOR')";
}

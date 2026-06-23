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
}

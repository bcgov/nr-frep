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
   * {@code FREP_ADMIN} and {@code FREP_EDITOR} — the only two global roles FREP has.
   */
  public static final String CONTENT_EDIT = "hasAnyAuthority('FREP_ADMIN','FREP_EDITOR')";

  /**
   * Sys-admin-only actions (FREP700 master-list administration, checklist activation): {@code FREP_ADMIN}.
   */
  public static final String ADMIN = "hasAuthority('FREP_ADMIN')";

  /**
   * Coarse "may this caller touch CHR at all" gate: {@code FREP_ADMIN} or any per-district CHR editor
   * role ({@code FREP_CHR_EDITOR_DISTRICT_*}). A global {@code FREP_EDITOR} (Biodiversity) is
   * intentionally excluded — CHR access is district-scoped. Evaluated via the {@code @auth} bean
   * ({@link LoggedUserHelper}).
   *
   * <p><b>Not sufficient on its own for any endpoint that resolves a specific checklist.</b> CHR is
   * strictly district-scoped, and there is no service-layer district check to fall back on — the
   * per-district rule lives entirely on the annotation. Endpoints that take a checklist id must use
   * {@code @PreAuthorize("@chrAuth.canEditChecklist(#id)")} ({@link ChrChecklistAuthorizer}), which
   * resolves the checklist's org unit and checks it against the caller's districts. This constant is
   * for id-less surfaces only.
   */
  public static final String CHR_EDIT = "@auth.canAnyChr()";

  /**
   * FREP editor access: {@code FREP_ADMIN} or {@code FREP_EDITOR}. Gates the protocol-checklist
   * (Biodiversity) reads (writes use {@link #CONTENT_EDIT}, which is equivalent).
   */
  public static final String FREP_EDIT = "hasAnyAuthority('FREP_ADMIN','FREP_EDITOR')";

  /**
   * Site Details resource editing (FREP110): {@code FREP_ADMIN}, {@code FREP_EDITOR}, <em>or</em> any
   * per-district CHR editor. Broader than {@link #CONTENT_EDIT} because site records are shared
   * across protocols — see {@link LoggedUserHelper#canEditSite()}. Creating a targeted site (FREP200)
   * remains {@link #CONTENT_EDIT}.
   */
  public static final String SITE_EDIT = "@auth.canEditSite()";
}

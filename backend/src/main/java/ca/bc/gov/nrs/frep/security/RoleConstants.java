package ca.bc.gov.nrs.frep.security;

import org.springframework.stereotype.Component;

/**
 * Exposes role constants as Spring beans for use in SpEL expressions and security configuration.
 *
 * <p>BC Gov SSO (CSS) role names match legacy WebADE roles ({@code FrepUser} in nr-frep-legacy).
 * URL-level rules use {@code hasAuthority()} / {@code hasAnyAuthority()} in
 * {@link ApiAuthorizationCustomizer}.
 *
 * <h3>Legacy WebADE action parity</h3>
 * <p>Source: {@code scripts/5.0.0/00/webade/webade_inserts.sql}
 * <ul>
 *   <li>{@link #SYS_ADMIN_AUTHORITY} — all actions including {@code ACTIVATECHECKLIST} (admin-only)</li>
 *   <li>{@link #UPDATE_AUTHORITY} — write actions except {@code ACTIVATECHECKLIST}</li>
 * </ul>
 */
@Component("roles")
public class RoleConstants {

  /**
   * CSS role for full administrative access.
   *
   * <p>Renamed twice: legacy WebADE called it {@code FREP_SYS_ADMIN}, FAM/Cognito
   * {@code FREP_ADMIN}, and the CSS integration {@code FREP_ADMINISTRATOR}. Only the last one is
   * ever seen on a token — the earlier names survive only in the legacy FAM extracts.
   */
  public static final String SYS_ADMIN_AUTHORITY = "FREP_ADMINISTRATOR";

  /** CSS role for create, edit, and submit workflows (legacy WebADE: FREP_UPDATE). */
  public static final String UPDATE_AUTHORITY = "FREP_EDITOR";

  /**
   * Prefix for the per-district CHR editor roles: {@code FREP_CHR_EDITOR_DISTRICT_<code>},
   * where {@code <code>} is the 3-letter Natural Resource District org-unit code (e.g. DCK). A user
   * holding one may edit/submit CHR checklists for that district only. Distinct from the global roles
   * above: a plain {@code FREP_EDITOR} is Biodiversity-only and grants no CHR access.
   */
  public static final String CHR_DISTRICT_EDITOR_PREFIX = "FREP_CHR_EDITOR_DISTRICT_";

  /** Roles that may perform HTTP write operations (POST, PUT, PATCH, DELETE). */
  public static final String[] WRITE_AUTHORITIES = {
      SYS_ADMIN_AUTHORITY,
      UPDATE_AUTHORITY,
  };

  /**
   * Roles that may perform HTTP read operations (GET).
   *
   * <p>Identical to {@link #WRITE_AUTHORITIES} since {@code FREP_VIEW_ONLY} was retired: FREP no
   * longer has a read-only global role. Per-district CHR editors hold no global role at all and are
   * authorized through {@code @auth} instead — see {@code LoggedUserHelper}.
   */
  public static final String[] READ_AUTHORITIES = {
      SYS_ADMIN_AUTHORITY,
      UPDATE_AUTHORITY,
  };
}

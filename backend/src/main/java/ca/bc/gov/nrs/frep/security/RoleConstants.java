package ca.bc.gov.nrs.frep.security;

import org.springframework.stereotype.Component;

/**
 * Exposes role constants as Spring beans for use in SpEL expressions and security configuration.
 *
 * <p>Cognito group names match legacy WebADE roles ({@code FrepUser} in nr-frep-legacy).
 * URL-level rules use {@code hasAuthority()} / {@code hasAnyAuthority()} in
 * {@link ApiAuthorizationCustomizer}.
 *
 * <h3>Legacy WebADE action parity</h3>
 * <p>Source: {@code scripts/5.0.0/00/webade/webade_inserts.sql}
 * <ul>
 *   <li>{@link #SYS_ADMIN_AUTHORITY} — all actions including {@code ACTIVATECHECKLIST} (admin-only)</li>
 *   <li>{@link #UPDATE_AUTHORITY} — write actions except {@code ACTIVATECHECKLIST}</li>
 *   <li>{@link #VIEW_ONLY_AUTHORITY} — read actions ({@code CHECKLIST} GET, {@code ACCEPTEDSITES}, etc.)</li>
 * </ul>
 */
@Component("roles")
public class RoleConstants {

  /** Legacy WebADE / Cognito group for full administrative access. */
  public static final String SYS_ADMIN_AUTHORITY = "FREP_SYS_ADMIN";

  /** Legacy WebADE / Cognito group for create, edit, and submit workflows. */
  public static final String UPDATE_AUTHORITY = "FREP_UPDATE";

  /** Legacy WebADE / Cognito group for read-only access. */
  public static final String VIEW_ONLY_AUTHORITY = "FREP_VIEW_ONLY";

  /** Roles that may perform HTTP write operations (POST, PUT, PATCH, DELETE). */
  public static final String[] WRITE_AUTHORITIES = {
      SYS_ADMIN_AUTHORITY,
      UPDATE_AUTHORITY,
  };

  /** Roles that may perform HTTP read operations (GET). */
  public static final String[] READ_AUTHORITIES = {
      SYS_ADMIN_AUTHORITY,
      UPDATE_AUTHORITY,
      VIEW_ONLY_AUTHORITY,
  };
}

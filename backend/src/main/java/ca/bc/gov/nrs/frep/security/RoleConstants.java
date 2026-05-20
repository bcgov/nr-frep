package ca.bc.gov.nrs.frep.security;

import org.springframework.stereotype.Component;

/**
 * Exposes role constants as Spring beans for use in SpEL expressions and security configuration.
 *
 * <p>The string constants {@link #ADMIN_AUTHORITY} and {@link #VIEWER_AUTHORITY} match
 * the Cognito group names and are used in {@code ApiAuthorizationCustomizer} for
 * URL-level access control via {@code hasAuthority()} / {@code hasAnyAuthority()}.
 */
@Component("roles")
public class RoleConstants {

  /** Cognito group / Spring authority for full read-write access. */
  public static final String ADMIN_AUTHORITY = "FREP_ADMIN";

  /** Cognito group / Spring authority for read-only access. */
  public static final String VIEWER_AUTHORITY = "FREP_VIEWER";
}

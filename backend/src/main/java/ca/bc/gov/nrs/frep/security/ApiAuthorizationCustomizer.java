package ca.bc.gov.nrs.frep.security;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

/**
 * URL-level authorization rules.
 *
 * LOCAL DEV: all requests are permitted. Re-enable the Cognito role matrix
 * in the commented block before deploying.
 */
@Component
public class ApiAuthorizationCustomizer implements
    Customizer<
        AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
        > {

  @Override
  public void customize(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>
          .AuthorizationManagerRequestMatcherRegistry authorize
  ) {
    authorize.anyRequest().permitAll();
  }

  /*
   * --- Cognito role-based authorization (re-enable before deploying) ---
   *
   * Legacy WebADE roles map 1:1 to Cognito groups (see RoleConstants).
   *
   * authorize.requestMatchers(HttpMethod.POST, "/api/**")
   *     .hasAnyAuthority(RoleConstants.WRITE_AUTHORITIES);
   * authorize.requestMatchers(HttpMethod.PUT, "/api/**")
   *     .hasAnyAuthority(RoleConstants.WRITE_AUTHORITIES);
   * authorize.requestMatchers(HttpMethod.PATCH, "/api/**")
   *     .hasAnyAuthority(RoleConstants.WRITE_AUTHORITIES);
   * authorize.requestMatchers(HttpMethod.DELETE, "/api/**")
   *     .hasAnyAuthority(RoleConstants.WRITE_AUTHORITIES);
   * authorize.requestMatchers(HttpMethod.GET, "/api/**")
   *     .hasAnyAuthority(RoleConstants.READ_AUTHORITIES);
   *
   * Admin-only endpoints (legacy ACTIVATECHECKLIST parity), when added:
   * authorize.requestMatchers("/api/v1/admin/**")
   *     .hasAuthority(RoleConstants.SYS_ADMIN_AUTHORITY);
   *
   * authorize.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
   * ... static assets, actuator, SPA fallback ...
   * authorize.anyRequest().denyAll();
   */
}

package ca.bc.gov.nrs.frep.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

/**
 * URL-level authorization rules.
 *
 * <p>Legacy WebADE role semantics are preserved (see {@link RoleConstants}):
 * <ul>
 *   <li>{@code FREP_SYS_ADMIN} and {@code FREP_UPDATE} may write.</li>
 *   <li>{@code FREP_VIEW_ONLY} may read but not write.</li>
 *   <li>{@code /api/v1/admin/**} (FREP700, future admin endpoints) is sys-admin only.</li>
 * </ul>
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
    // CORS preflight — must not require auth or the browser never sends the real request.
    authorize.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

    // Health / actuator endpoints — open for OpenShift probes and Prometheus scraping.
    authorize.requestMatchers("/actuator/**").permitAll();
    authorize.requestMatchers("/api/hello").permitAll();

    // Admin endpoints (FREP700 etc.) — sys-admin only, regardless of HTTP method.
    authorize.requestMatchers("/api/v1/admin/**")
        .hasAuthority(RoleConstants.SYS_ADMIN_AUTHORITY);

    // State-changing requests on /api/** require write authorities.
    authorize.requestMatchers(HttpMethod.POST, "/api/**")
        .hasAnyAuthority(RoleConstants.WRITE_AUTHORITIES);
    authorize.requestMatchers(HttpMethod.PUT, "/api/**")
        .hasAnyAuthority(RoleConstants.WRITE_AUTHORITIES);
    authorize.requestMatchers(HttpMethod.PATCH, "/api/**")
        .hasAnyAuthority(RoleConstants.WRITE_AUTHORITIES);
    authorize.requestMatchers(HttpMethod.DELETE, "/api/**")
        .hasAnyAuthority(RoleConstants.WRITE_AUTHORITIES);

    // Read access on /api/** is granted to any of the three FREP roles.
    authorize.requestMatchers(HttpMethod.GET, "/api/**")
        .hasAnyAuthority(RoleConstants.READ_AUTHORITIES);

    // Anything not matched above must be authenticated.
    authorize.anyRequest().authenticated();
  }
}

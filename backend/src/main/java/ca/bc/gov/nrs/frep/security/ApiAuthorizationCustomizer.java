package ca.bc.gov.nrs.frep.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

/**
 * URL-level authorization: only authentication (every request needs a valid token), plus the small
 * set of open endpoints. Role-based authorization now lives per-endpoint via {@code @PreAuthorize}
 * (see {@link FrepAuthorities}), mirroring nr-fspts.
 *
 * <p>The previous URL-level role rules (PUT/POST/… → write authorities, {@code /admin/**} → sys-admin)
 * were dropped in favour of the per-endpoint model. {@link RoleConstants} still backs the
 * {@code @PreAuthorize} expressions and can be reinstated here as a coarse backstop if desired.
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

    // Only the two actuator endpoints we actually expose
    // (management.endpoints.web.exposure.include) are anonymous — for the OpenShift probes and
    // Prometheus scraping. Named individually rather than a broad /actuator/** wildcard, which
    // would silently publish any management endpoint added to that list later. Matches nr-fspts.
    authorize.requestMatchers("/actuator/health", "/actuator/prometheus").permitAll();

    // Everything else requires a valid token; role checks are enforced per-endpoint via @PreAuthorize.
    authorize.anyRequest().authenticated();
  }
}

package ca.bc.gov.nrs.frep.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Pins which actuator paths are anonymous.
 *
 * <p>These were opened with a single {@code /actuator/**} wildcard, which publishes whatever is in
 * {@code management.endpoints.web.exposure.include} — so widening that list would have silently
 * widened the anonymous surface too. Naming the two endpoints makes that impossible, and matches
 * nr-fspts.
 */
class ActuatorExposureTest {

  @SuppressWarnings("unchecked")
  private static AuthorizeHttpRequestsConfigurer<HttpSecurity>
      .AuthorizationManagerRequestMatcherRegistry newRegistry() {
    return mock(
        AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry.class,
        withSettings().defaultAnswer(org.mockito.Answers.RETURNS_DEEP_STUBS));
  }

  @Test
  @DisplayName("opens the health and metrics endpoints, and nothing else under /actuator")
  void onlyHealthAndPrometheusAreAnonymous() {
    var registry = newRegistry();

    new ApiAuthorizationCustomizer().customize(registry);

    verify(registry).requestMatchers("/actuator/health", "/actuator/prometheus");
    // The wildcard must not come back: it would re-publish every management endpoint.
    verify(registry, never()).requestMatchers("/actuator/**");
  }

  @Test
  @DisplayName("still lets CORS preflight through and authenticates everything else")
  void preflightIsOpenAndTheRestIsAuthenticated() {
    var registry = newRegistry();

    new ApiAuthorizationCustomizer().customize(registry);

    // Preflight carries no credentials; requiring auth would stop the browser sending the real
    // request at all.
    verify(registry).requestMatchers(HttpMethod.OPTIONS, "/**");
    verify(registry).anyRequest();
  }
}

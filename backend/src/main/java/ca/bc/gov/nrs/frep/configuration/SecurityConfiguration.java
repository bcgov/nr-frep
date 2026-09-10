package ca.bc.gov.nrs.frep.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import ca.bc.gov.nrs.frep.security.ApiAuthorizationCustomizer;
import ca.bc.gov.nrs.frep.security.Oauth2SecurityCustomizer;

/**
 * Main security configuration. The API runs as an OAuth 2.0 resource server
 * validating BC Gov SSO (Keycloak) access tokens. It is stateless: no session, no login form, and
 * no cookie is ever used as a credential.
 *
 * <p><b>No CORS configuration.</b> The SPA and this API share an origin — Caddy serves the app and
 * proxies {@code /api*} on the same host, and local dev goes through the Vite proxy — so the browser
 * never issues a cross-origin request and no preflight has to be satisfied. Confirmed on TEST, where
 * {@code config.js} carries an empty {@code VITE_BACKEND_URL} and the client falls back to a
 * same-origin {@code /api}. Matches nr-fspts, which has no CORS config at all. If an environment
 * ever points the SPA at an absolute cross-origin API URL, this has to come back.
 *
 * <p><b>CSRF is disabled, and that is not an oversight.</b> CSRF attacks work because the browser
 * attaches an <em>ambient</em> credential — a cookie or session — to a cross-site request without
 * the page asking. This API has none: every request is authenticated solely by a Bearer JWT in the
 * {@code Authorization} header, which an attacker's page cannot make the browser send. A forged
 * cross-site request therefore arrives unauthenticated and is rejected with a 401 on its own merits.
 * {@link SessionCreationPolicy#STATELESS} above enforces the premise rather than leaving it to
 * happen to be true. Static analysis flags a disabled CSRF filter generically; for token auth it is
 * a false positive.
 *
 * <p><b>Security response headers are not set here.</b> They are owned by the Caddy edge
 * (frontend/Caddyfile), which is the only path a browser reaches this API by — the backend has no
 * OpenShift Route, and Caddy proxies {@code /api*} to it. Caddy <em>replaces</em> the headers on
 * every proxied response, so anything set here would be overwritten before it reached a client;
 * a CSP on a JSON response constrains nothing in any case. Keep the policy in one place.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {


  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      ApiAuthorizationCustomizer apiCustomizer,
      Oauth2SecurityCustomizer oauth2Customizer
  ) throws Exception {

    http
        // Stateless, and CSRF disabled — see the class javadoc. The order matters: the session
        // policy is what makes "no ambient credential" an enforced property rather than an
        // incidental one, and CSRF is only safe to drop because of it.
        .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(apiCustomizer)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .oauth2ResourceServer(oauth2Customizer);

    return http.build();
  }


}

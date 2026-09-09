package ca.bc.gov.nrs.frep.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * Configures the OAuth 2.0 Resource Server to validate BC Gov SSO (Keycloak standard realm)
 * <strong>access tokens</strong>.
 *
 * <h3>Audience validation: why {@code azp}, and why there is no {@code token_use} check</h3>
 * The Cognito-era configuration rejected any token whose {@code token_use} claim was not
 * {@code "access"}. <b>Keycloak does not emit {@code token_use} at all</b>, so keeping that check
 * would fail every request — including the actuator health probes, which makes it read as a broken
 * deploy rather than broken auth.
 *
 * <p>Dropping it leaves a real gap, though. The standard realm is shared across many BC Gov
 * applications: every one of their tokens is signed by the same issuer and verifies against the
 * same JWKS, so signature + issuer validation alone does <em>not</em> establish that a token was
 * minted for FREP. The {@code azp} (authorized party) check below closes that — a token issued to
 * another integration is refused here rather than being treated as one of ours.
 *
 * <p>The expected client id comes from configuration, not a constant: it differs per environment.
 *
 * <h3>Roles</h3>
 * See {@link #authoritiesConverter()} — CSS and stock Keycloak put them in different places, so
 * both are read.
 */
@Component
public class Oauth2SecurityCustomizer implements
    Customizer<OAuth2ResourceServerConfigurer<HttpSecurity>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(Oauth2SecurityCustomizer.class);

  /** Claim CSS's realm mappers populate with the client's roles. */
  private static final String CLAIM_CLIENT_ROLES = "client_roles";

  /** Stock-Keycloak location for the same thing: {@code resource_access.<azp>.roles}. */
  private static final String CLAIM_RESOURCE_ACCESS = "resource_access";

  /**
   * FAM records per-grant expiry as a role assigned to the person, shaped
   * {@code FAM:EXPIRES:2026-09-30:FREP_EDITOR}. Harmless to the exact-match authorisation FREP
   * does, but it rides the token alongside the real roles and would otherwise show up anywhere the
   * granted authorities are enumerated (the {@code @auth} district parsing, logs, error bodies).
   */
  private static final String FAM_BOOKKEEPING_PREFIX = "FAM:";

  private final String jwkSetUri;
  private final String clientId;
  private final NimbusJwtDecoder jwtDecoder;

  public Oauth2SecurityCustomizer(
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
      @Value("${ca.bc.gov.nrs.keycloak.client-id}") String clientId
  ) {
    this.jwkSetUri = jwkSetUri;
    this.clientId = clientId;
    this.jwtDecoder = buildJwtDecoder(jwkSetUri);

    // ── Validate issuer + that the token was minted for THIS client ──
    this.jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuerUri),
        token -> {
          String authorizedParty = token.getClaimAsString("azp");
          if (!clientId.equals(authorizedParty)) {
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(
                    "invalid_token",
                    "Token was not issued to this application (azp=" + authorizedParty + ")",
                    null
                )
            );
          }
          return OAuth2TokenValidatorResult.success();
        }
    ));
  }

  @Override
  public void customize(
      OAuth2ResourceServerConfigurer<HttpSecurity> customize) {
    LOGGER.info("Configuring OAuth2 resource server with JWK set URI: {} (azp={})",
        jwkSetUri, clientId);
    customize.jwt(jwt -> jwt.jwtAuthenticationConverter(converter()).decoder(jwtDecoder));
  }

  /**
   * Builds a JWT decoder backed by Nimbus's {@link JWKSourceBuilder}, which provides:
   * <ul>
   *   <li><b>Cached JWKS</b> — fetched once and reused for ~5 min, eliminating per-request
   *       round-trips to the realm.</li>
   *   <li><b>Refresh-ahead caching</b> — re-fetches the JWKS in the background BEFORE it
   *       expires, so user-facing requests never block on a refresh.</li>
   *   <li><b>Retry on transient failures</b> — automatically retries the JWKS fetch when
   *       the realm returns an error or times out.</li>
   *   <li><b>Explicit HTTP timeouts</b> — connect and read timeouts on the JWKS fetch.</li>
   * </ul>
   * This replaces the default Spring decoder builder, which uses a no-op Spring cache and
   * causes intermittent {@code Connect timed out} 401s whenever the identity provider has a brief
   * hiccup.
   */
  private static NimbusJwtDecoder buildJwtDecoder(String jwkSetUri) {
    URL jwkSetUrl;
    try {
      jwkSetUrl = new URI(jwkSetUri).toURL();
    } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
      throw new IllegalStateException("Invalid jwk-set-uri: " + jwkSetUri, e);
    }

    DefaultResourceRetriever retriever = new DefaultResourceRetriever(
        (int) Duration.ofSeconds(10).toMillis(),
        (int) Duration.ofSeconds(15).toMillis(),
        50 * 1024
    );

    JWKSource<SecurityContext> jwkSource = JWKSourceBuilder
        .create(jwkSetUrl, retriever)
        .retrying(true)
        .refreshAheadCache(true)
        .build();

    ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    processor.setJWSKeySelector(
        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
    return new NimbusJwtDecoder(processor);
  }

  private Converter<Jwt, AbstractAuthenticationToken> converter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter());
    return converter;
  }

  /**
   * Reads the caller's roles from the token.
   *
   * <p><b>Both locations are read, deliberately.</b> CSS's realm mappers emit a flat
   * {@code client_roles} array; stock Keycloak nests the same values under
   * {@code resource_access.<azp>.roles}. Which one is populated depends on how the integration was
   * configured, and an integration that switches gets no error — just a user with no roles, which
   * lands them on /unauthorized as though their access had been revoked.
   *
   * <p>Authorities are exposed with no {@code ROLE_} prefix, matching the role codes FREP checks
   * ({@code FREP_ADMINISTRATOR}, {@code FREP_EDITOR}, {@code FREP_CHR_EDITOR_DISTRICT-<code>}) — see
   * {@link FrepAuthorities}. {@link #FAM_BOOKKEEPING_PREFIX} entries are dropped.
   */
  private Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter() {
    return jwt -> {
      List<String> roles = new ArrayList<>(claimAsStringList(jwt.getClaim(CLAIM_CLIENT_ROLES)));
      roles.addAll(resourceAccessRoles(jwt));

      return roles.stream()
          .filter(role -> role != null && !role.isBlank())
          .map(String::trim)
          .filter(role -> !role.toUpperCase(Locale.ROOT).startsWith(FAM_BOOKKEEPING_PREFIX))
          .distinct()
          .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
          .toList();
    };
  }

  /** Pulls {@code resource_access.<azp>.roles}, tolerating every level of it being absent. */
  private List<String> resourceAccessRoles(Jwt jwt) {
    Map<String, Object> resourceAccess = jwt.getClaimAsMap(CLAIM_RESOURCE_ACCESS);
    if (resourceAccess == null) {
      return List.of();
    }
    Object forThisClient = resourceAccess.get(clientId);
    if (!(forThisClient instanceof Map<?, ?> clientEntry)) {
      return List.of();
    }
    return claimAsStringList(clientEntry.get("roles"));
  }

  private static List<String> claimAsStringList(Object claim) {
    if (!(claim instanceof Collection<?> values)) {
      return List.of();
    }
    return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
  }

}

package ca.bc.gov.nrs.frep.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Guards the constructor against whitespace in the configured URIs.
 *
 * <p>A trailing space on the TEST {@code KEYCLOAK_ISSUER_URI} GitHub variable took the whole API
 * down on 2026-09-09: {@code application.yml} composes {@code jwk-set-uri} as
 * {@code ${KEYCLOAK_ISSUER_URI}/protocol/openid-connect/certs}, so the space landed mid-URI, the
 * bean failed to construct, and no pod started. The SPA was unaffected — {@code keycloak.ts}
 * already trimmed the same value — which made it look like a backend-only fault rather than one
 * bad character in shared configuration.
 */
class Oauth2SecurityCustomizerTest {

  private static final String ISSUER = "https://test.loginproxy.gov.bc.ca/auth/realms/standard";
  private static final String CERTS = "/protocol/openid-connect/certs";
  private static final String CLIENT = "forest-and-range-evaluation-program-6538";

  /** No network here: JWKSourceBuilder resolves the URL lazily, on first token validation. */
  private static Oauth2SecurityCustomizer build(String jwkSetUri, String issuer, String client) {
    return new Oauth2SecurityCustomizer(jwkSetUri, issuer, client);
  }

  @Test
  void constructsWithCleanConfiguration() {
    assertDoesNotThrow(() -> build(ISSUER + CERTS, ISSUER, CLIENT));
  }

  /** The exact production failure: the space is INTERIOR, so trim() would not have helped. */
  @Test
  void survivesATrailingSpaceOnTheIssuerVariable() {
    assertDoesNotThrow(() -> build(ISSUER + " " + CERTS, ISSUER + " ", CLIENT));
  }

  @Test
  void survivesLeadingWhitespaceAndNewlines() {
    assertDoesNotThrow(() -> build("  " + ISSUER + CERTS + "\n", "\t" + ISSUER, "  " + CLIENT + " "));
  }

  /** Whitespace is forgiven; a genuinely malformed URI is still a hard, immediate failure. */
  @Test
  void stillRefusesAUriThatIsActuallyInvalid() {
    assertThrows(IllegalStateException.class, () -> build("not a url", ISSUER, CLIENT));
  }
}

package ca.bc.gov.nrs.frep.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Pins the two claim-mapping rules that fail silently against BC Gov SSO (Keycloak).
 *
 * <p>Neither has a runtime symptom: the app authenticates, the pages render, and the audit columns
 * fill in with plausible-looking strings that no longer join to the rows the legacy application
 * wrote. These assertions are the only thing standing between a working deployment and a quietly
 * corrupted audit trail, so they are deliberately literal about the exact expected string.
 *
 * @see JwtPrincipalUtil
 */
class JwtPrincipalUtilTest {

  private static Map<String, Object> claims(String... keyValuePairs) {
    Map<String, Object> claims = new HashMap<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      if (keyValuePairs[i + 1] != null) {
        claims.put(keyValuePairs[i], keyValuePairs[i + 1]);
      }
    }
    return claims;
  }

  // ── Rule 1: every IDIR alias folds to the single token "IDIR" ──────────────

  /**
   * The one that matters in production. FREP's CSS integration selects IDIR - MFA, so the realm
   * reports {@code azureidir} on every login — confirmed by probing the DEV realm, where every
   * {@code kc_idp_hint} resolves to {@code /broker/azureidir/login}. Mapping it verbatim writes
   * {@code AZUREIDIR\jsmith} for the same person the legacy rows call {@code IDIR\jsmith}.
   */
  @Test
  void azureidirIsNormalizedToIdir() {
    assertEquals(
        "IDIR\\jsmith",
        JwtPrincipalUtil.getUserId(
            claims("identity_provider", "azureidir", "idir_username", "jsmith")));
    assertNotEquals(
        "AZUREIDIR\\jsmith",
        JwtPrincipalUtil.getUserId(
            claims("identity_provider", "azureidir", "idir_username", "jsmith")));
  }

  /** The non-MFA broker maps identically, so switching the integration changes no audit string. */
  @Test
  void plainIdirAliasNormalizesTheSameWay() {
    assertEquals(
        "IDIR\\jsmith",
        JwtPrincipalUtil.getUserId(claims("identity_provider", "idir", "idir_username", "jsmith")));
  }

  @Test
  void providerAliasIsCaseInsensitive() {
    assertEquals(
        "IDIR\\jsmith",
        JwtPrincipalUtil.getUserId(
            claims("identity_provider", "AzureIDIR", "idir_username", "jsmith")));
  }

  @Test
  void bceidBusinessIsNormalizedToLegacyBceidToken() {
    assertEquals(
        "BCEID\\contractor1",
        JwtPrincipalUtil.getUserId(
            claims("identity_provider", "bceidbusiness", "bceid_username", "contractor1")));
    assertEquals(
        "BCEID\\contractor1",
        JwtPrincipalUtil.getUserId(
            claims("identity_provider", "BCEIDBUSINESS", "bceid_username", "contractor1")));
  }

  @Test
  void bcscAliasesMapToBcsc() {
    assertEquals(
        "BCSC\\user",
        JwtPrincipalUtil.getUserId(claims("identity_provider", "bcsc", "idir_username", "user")));
    // Legacy FAM/Cognito spelling, kept so tokens minted during the cutover still map.
    assertEquals(
        "BCSC\\user",
        JwtPrincipalUtil.getUserId(
            claims("identity_provider", "ca.bc.gov.flnr.fam.bcsc", "idir_username", "user")));
  }

  @Test
  void unrecognizedProviderIsUppercasedRatherThanGuessedAt() {
    assertEquals(
        "SOMETHINGELSE\\user",
        JwtPrincipalUtil.getUserId(
            claims("identity_provider", "somethingelse", "idir_username", "user")));
  }

  // ── Rule 2: GUIDs are case-folded; usernames are not ───────────────────────

  /**
   * {@code idir_user_guid} and the GUID inside {@code preferred_username} can arrive in different
   * cases in the same token. Whichever one is reached, the result has to be one string — otherwise
   * one person becomes two rows.
   */
  @Test
  void lowercaseGuidFallbackIsUpperCased() {
    Map<String, Object> lowerGuid =
        claims(
            "identity_provider", "azureidir",
            "idir_user_guid", "0a1b2c3d4e5f60718293a4b5c6d7e8f9");
    assertEquals("IDIR\\0A1B2C3D4E5F60718293A4B5C6D7E8F9", JwtPrincipalUtil.getUserId(lowerGuid));
  }

  @Test
  void guidFromEitherClaimYieldsTheSameIdentity() {
    // The same person, one token spelling the GUID upper-case and the other lower-case.
    String fromPreferred =
        JwtPrincipalUtil.getUserId(
            claims(
                "identity_provider", "azureidir",
                "preferred_username", "0a1b2c3d4e5f60718293a4b5c6d7e8f9@azureidir"));
    String fromGuidClaim =
        JwtPrincipalUtil.getUserId(
            claims(
                "identity_provider", "azureidir",
                "idir_user_guid", "0A1B2C3D4E5F60718293A4B5C6D7E8F9"));
    assertEquals(fromGuidClaim, fromPreferred);
  }

  /** A username is a name, not a number — it must survive exactly as issued. */
  @Test
  void usernameCaseIsPreserved() {
    assertEquals(
        "IDIR\\JSmith",
        JwtPrincipalUtil.getUserId(
            claims("identity_provider", "azureidir", "idir_username", "JSmith")));
  }

  // ── Resolution order and empty cases ───────────────────────────────────────

  @Test
  void usernameWinsOverGuidWhenBothArePresent() {
    Map<String, Object> both =
        claims(
            "identity_provider", "azureidir",
            "idir_username", "jsmith",
            "idir_user_guid", "0A1B2C3D",
            "preferred_username", "0a1b2c3d@azureidir");
    assertEquals("IDIR\\jsmith", JwtPrincipalUtil.getUserId(both));
  }

  @Test
  void preferredUsernameWithoutAnAtSignIsNotTreatedAsAGuid() {
    assertEquals(
        "",
        JwtPrincipalUtil.getUserId(
            claims("identity_provider", "azureidir", "preferred_username", "service-account-frep")));
  }

  @Test
  void noIdentityClaimsYieldsAnEmptyString() {
    assertEquals("", JwtPrincipalUtil.getUserId(claims("identity_provider", "azureidir")));
    assertEquals("", JwtPrincipalUtil.getUserId(new HashMap<>()));
  }

  @Test
  void displayNameIsReadFromTheKeycloakClaim() {
    assertEquals(
        "Smith, John", JwtPrincipalUtil.getDisplayName(claims("display_name", "Smith, John")));
    assertEquals("", JwtPrincipalUtil.getDisplayName(new HashMap<>()));
  }
}

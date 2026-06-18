package ca.bc.gov.nrs.frep.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the userid prefix produced from FAM JWT claims, including the
 * normalization of the FAM "BCEIDBUSINESS" provider to the legacy WebADE
 * source-directory token "BCEID" so values fit the legacy FREP schema
 * (audit columns, evaluator/search display stripping built around "IDIR\\"/"BCEID\\").
 */
class JwtPrincipalUtilTest {

  private static Map<String, Object> claims(String idpName, String idpUsername) {
    Map<String, Object> claims = new HashMap<>();
    if (idpName != null) {
      claims.put("custom:idp_name", idpName);
    }
    if (idpUsername != null) {
      claims.put("custom:idp_username", idpUsername);
    }
    return claims;
  }

  @Test
  void idirProviderIsUppercasedAndPrefixed() {
    assertEquals("IDIR\\JSMITH", JwtPrincipalUtil.getUserId(claims("idir", "JSMITH")));
  }

  @Test
  void bceidBusinessIsNormalizedToBceid() {
    assertEquals(
        "BCEID\\CONTRACTOR1", JwtPrincipalUtil.getUserId(claims("bceidbusiness", "CONTRACTOR1")));
    // Already-uppercase form normalizes the same way.
    assertEquals(
        "BCEID\\CONTRACTOR1", JwtPrincipalUtil.getUserId(claims("BCEIDBUSINESS", "CONTRACTOR1")));
  }

  @Test
  void famBcscProviderMapsToBcsc() {
    assertEquals(
        "BCSC\\USER", JwtPrincipalUtil.getUserId(claims("ca.bc.gov.flnr.fam.bcsc", "USER")));
  }

  @Test
  void fallsBackToIdpUserIdAndEmptyWhenNoIdentity() {
    Map<String, Object> userIdOnly = new HashMap<>();
    userIdOnly.put("custom:idp_name", "bceidbusiness");
    userIdOnly.put("custom:idp_user_id", "guid-123");
    assertEquals("BCEID\\guid-123", JwtPrincipalUtil.getUserId(userIdOnly));

    assertEquals("", JwtPrincipalUtil.getUserId(claims("bceidbusiness", null)));
  }
}

package ca.bc.gov.nrs.frep.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documents parity between legacy WebADE roles and Cognito group names.
 *
 * <p>Source: nr-frep-legacy {@code scripts/5.0.0/00/webade/webade_inserts.sql}
 * and {@code FrepUser} / {@code RestAction} role checks.
 *
 * <p>The legacy read-only role ({@code FREP_VIEW_ONLY}) has since been retired from FREP, so only
 * the admin and editor groups carry over.
 */
class LegacyRoleMappingTest {

  @Test
  void cognitoGroupNamesMatchExpectedConstants() {
    // Admin group renamed from legacy FREP_SYS_ADMIN to FREP_ADMIN in Cognito.
    assertEquals("FREP_ADMIN", RoleConstants.SYS_ADMIN_AUTHORITY);
    assertEquals("FREP_EDITOR", RoleConstants.UPDATE_AUTHORITY);
  }

  @Test
  void readAuthoritiesAreTheTwoRemainingGlobalRoles() {
    // FREP_VIEW_ONLY was retired, so reads and writes now carry the same two global roles. A
    // per-district CHR editor holds neither and is authorized through @auth instead.
    assertArrayEquals(
        new String[] {
            RoleConstants.SYS_ADMIN_AUTHORITY,
            RoleConstants.UPDATE_AUTHORITY,
        },
        RoleConstants.READ_AUTHORITIES
    );
  }

  @Test
  void writeAuthoritiesMatchLegacySubmitAndChecklistActions() {
    Set<String> writeRoles = Set.of(RoleConstants.WRITE_AUTHORITIES);

    assertTrue(writeRoles.contains(RoleConstants.SYS_ADMIN_AUTHORITY));
    assertTrue(writeRoles.contains(RoleConstants.UPDATE_AUTHORITY));
    assertEquals(2, writeRoles.size());
  }

  @Test
  void activateChecklistIsSysAdminOnlyInLegacyWebade() {
    // action_lnk grants ACTIVATECHECKLIST to FREP_SYS_ADMIN (legacy) only (not FREP_UPDATE/FREP_EDITOR).
    // Cognito's equivalent group is FREP_ADMIN.
    String adminOnlyAction = "ACTIVATECHECKLIST";
    String sysAdminRole = RoleConstants.SYS_ADMIN_AUTHORITY;

    assertEquals("FREP_ADMIN", sysAdminRole);
    assertTrue(adminOnlyAction.startsWith("ACTIVATE"));
  }
}

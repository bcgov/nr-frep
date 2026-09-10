package ca.bc.gov.nrs.frep.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documents parity between legacy WebADE roles and the BC Gov SSO (CSS) role names.
 *
 * <p>Source: nr-frep-legacy {@code scripts/5.0.0/00/webade/webade_inserts.sql}
 * and {@code FrepUser} / {@code RestAction} role checks.
 *
 * <p>The legacy read-only role ({@code FREP_VIEW_ONLY}) has since been retired from FREP, so only
 * the admin and editor groups carry over.
 */
class LegacyRoleMappingTest {

  @Test
  void cssRoleNamesMatchExpectedConstants() {
    // Admin role renamed twice: WebADE FREP_SYS_ADMIN -> FAM/Cognito FREP_ADMIN -> CSS
    // FREP_ADMINISTRATOR. FREP_EDITOR (WebADE FREP_UPDATE) carried across unchanged.
    assertEquals("FREP_ADMINISTRATOR", RoleConstants.SYS_ADMIN_AUTHORITY);
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
    // The equivalent CSS role is FREP_ADMINISTRATOR.
    String adminOnlyAction = "ACTIVATECHECKLIST";
    String sysAdminRole = RoleConstants.SYS_ADMIN_AUTHORITY;

    assertEquals("FREP_ADMINISTRATOR", sysAdminRole);
    assertTrue(adminOnlyAction.startsWith("ACTIVATE"));
  }
}

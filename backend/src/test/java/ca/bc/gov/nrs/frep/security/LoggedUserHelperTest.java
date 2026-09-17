package ca.bc.gov.nrs.frep.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for the protocol/district capability helpers. Drives {@link LoggedUserHelper} through a
 * real {@link SecurityContextHolder} populated with the raw role strings the token carries.
 */
class LoggedUserHelperTest {

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private LoggedUserHelper withAuthorities(String... authorities) {
    TestingAuthenticationToken auth = new TestingAuthenticationToken("user", "creds", authorities);
    auth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(auth);
    return new LoggedUserHelper();
  }

  @Test
  void parsesDistrictCodesFromChrRoles() {
    LoggedUserHelper helper =
        withAuthorities("FREP_CHR_EDITOR_DISTRICT-DCK", "FREP_CHR_EDITOR_DISTRICT-DCC");

    assertThat(helper.chrDistrictCodes()).containsExactlyInAnyOrder("DCK", "DCC");
    assertThat(helper.canAnyChr()).isTrue();
    assertThat(helper.canChr("DCK")).isTrue();
    assertThat(helper.canChr("dcc")).isTrue(); // case-insensitive
    assertThat(helper.canChr("DSE")).isFalse();
    assertThat(helper.canEdit()).isFalse(); // CHR-only user has no Bio access
  }

  @Test
  void editorHasBioButNoChr() {
    LoggedUserHelper helper = withAuthorities("FREP_EDITOR");

    assertThat(helper.canEdit()).isTrue();
    assertThat(helper.chrDistrictCodes()).isEmpty();
    assertThat(helper.canAnyChr()).isFalse();
    assertThat(helper.canChr("DCK")).isFalse();
  }

  @Test
  void adminSeesBioAndEveryChrDistrict() {
    LoggedUserHelper helper = withAuthorities("FREP_ADMINISTRATOR");

    assertThat(helper.canEdit()).isTrue();
    assertThat(helper.canAnyChr()).isTrue();
    assertThat(helper.canChr("DCK")).isTrue();
    assertThat(helper.canChr("ANYTHING")).isTrue();
  }

  @Test
  void canChrRefusesAnAbsentDistrictEvenForAnAdmin() {
    // Changed 2026-08-10. This used to assert `canChr(null)` was TRUE for an admin ("admin passes
    // regardless"). An absent district does not mean "every district" — it means the caller could
    // not resolve one, most often because the checklist id it started from has no matching row.
    // ChrChecklistAuthorizer.canEditChecklist passes exactly that null, so an admin was authorized
    // to edit a checklist that does not exist and the request then died on the status read as a
    // 500 rather than an honest 403.
    //
    // Merge note: the role NAMES here are main's (FREP_ADMINISTRATOR, and the DISTRICT- hyphen);
    // the BEHAVIOUR is this branch's, and it is what the merged LoggedUserHelper.canChr does — the
    // null/blank guard sits above the isSysAdmin() bypass.
    assertThat(withAuthorities("FREP_CHR_EDITOR_DISTRICT-DCK").canChr(null)).isFalse();
    assertThat(withAuthorities("FREP_ADMINISTRATOR").canChr(null)).isFalse();
    assertThat(withAuthorities("FREP_ADMINISTRATOR").canChr("   ")).isFalse();
    // A real district is of course still open to an admin — the bypass itself is unchanged.
    assertThat(withAuthorities("FREP_ADMINISTRATOR").canChr("DCK")).isTrue();
  }

  @Test
  void siteEditingIsOpenToEditorsAndChrDistrictEditorsAlike() {
    // Site records are shared across protocols, so canEditSite is deliberately wider than canEdit.
    assertThat(withAuthorities("FREP_EDITOR").canEditSite()).isTrue();
    assertThat(withAuthorities("FREP_ADMINISTRATOR").canEditSite()).isTrue();
    assertThat(withAuthorities("FREP_CHR_EDITOR_DISTRICT-DCK").canEditSite()).isTrue();
    // ...but it is still a role check: view-only and no-role users cannot edit.
    // No global FREP role and no CHR district — the roleless case, now that FREP_VIEW_ONLY is gone.
    assertThat(withAuthorities("SOME_OTHER_APP_ROLE").canEditSite()).isFalse();
    assertThat(withAuthorities().canEditSite()).isFalse();
  }

  @Test
  void chrDistrictEditorGainsSiteEditingWithoutGainingBiodiversityWrite() {
    LoggedUserHelper helper = withAuthorities("FREP_CHR_EDITOR_DISTRICT-DCK");

    assertThat(helper.canEditSite()).isTrue();
    assertThat(helper.canEdit()).isFalse();
  }
}

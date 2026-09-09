package ca.bc.gov.nrs.frep.security;

import ca.bc.gov.nrs.frep.util.JwtPrincipalUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring bean exposing authorization helpers for the currently authenticated user.
 *
 * <p>Registered as {@code @auth} for programmatic use in services and security configuration.
 *
 * <h3>Everything is read straight off the access token</h3>
 * BC Gov SSO puts the profile claims FREP needs ({@code idir_username}, {@code identity_provider},
 * {@code display_name}) on the <em>access</em> token, alongside the roles. There is no per-request
 * userinfo call and no claim-merging: the Cognito-era {@code /oauth2/userInfo} round trip — which
 * existed only because Cognito withheld those claims from access tokens — is gone.
 *
 * <p>If {@link #getLoggedUserId()} starts returning GUIDs rather than usernames, the claims were
 * mapped onto the ID token only. That is a CSS console setting, not a code change — see
 * {@link JwtPrincipalUtil}.
 */
@Component("auth")
public class LoggedUserHelper {

  // ─── Identity helpers ──────────────────────────────────────────────
  /**
   * Get the ID from the logged user (e.g. {@code IDIR\jsmith}) — the legacy source-directory
   * string the FREP audit columns hold. Built from the access token's {@code identity_provider} and
   * {@code idir_username} / {@code bceid_username} claims.
   */
  public String getLoggedUserId() {
    return JwtPrincipalUtil.getUserId(getPrincipal().getClaims());
  }

  /** The user's display name from the token's {@code display_name} claim, or an empty string. */
  public String getLoggedUserDisplayName() {
    return JwtPrincipalUtil.getDisplayName(getPrincipal().getClaims());
  }

  // ─── Role / authority helpers (roles ride the access token — see Oauth2SecurityCustomizer) ──

  /**
   * Returns the set of authority strings for the current user (e.g. {@code FREP_ADMINISTRATOR}).
   */
  public Set<String> getAuthorities() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return Set.of();
    }
    return authentication.getAuthorities()
        .stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
  }

  /**
   * Returns {@code true} if the user holds the {@code FREP_ADMINISTRATOR} authority.
   */
  public boolean isSysAdmin() {
    return getAuthorities().contains(RoleConstants.SYS_ADMIN_AUTHORITY);
  }

  /**
   * Returns {@code true} if the user holds the {@code FREP_EDITOR} authority.
   */
  public boolean isUpdate() {
    return getAuthorities().contains(RoleConstants.UPDATE_AUTHORITY);
  }

  /**
   * Returns {@code true} if the user may perform write operations
   * ({@code FREP_ADMINISTRATOR} or {@code FREP_EDITOR}).
   */
  public boolean canWrite() {
    return isSysAdmin() || isUpdate();
  }

  // ─── Protocol / district capability helpers (CHR district-scoped access) ──

  /**
   * The set of Natural Resource District codes (e.g. {@code "DCK"}) the user may access CHR checklists
   * for, parsed from the {@code FREP_CHR_EDITOR_DISTRICT_<code>} authorities. Empty when the user holds
   * none. Codes are upper-cased so comparisons against {@code org_unit_code} are case-insensitive.
   */
  public Set<String> chrDistrictCodes() {
    return getAuthorities().stream()
        .filter(authority -> authority.startsWith(RoleConstants.CHR_DISTRICT_EDITOR_PREFIX))
        .map(authority -> authority.substring(RoleConstants.CHR_DISTRICT_EDITOR_PREFIX.length()))
        .filter(code -> !code.isBlank())
        .map(code -> code.toUpperCase(Locale.ROOT))
        .collect(Collectors.toSet());
  }

  /**
   * FREP editor access — sys-admin or {@code FREP_EDITOR}. Governs the non-CHR (protocol-checklist /
   * Biodiversity) surfaces, whose visibility is not district-scoped. (CHR uses {@link #canChr}.)
   */
  public boolean canEdit() {
    return isSysAdmin() || isUpdate();
  }

  /** True if the user may access CHR for <em>any</em> district (sys-admin, or holds ≥1 district role). */
  public boolean canAnyChr() {
    return isSysAdmin() || !chrDistrictCodes().isEmpty();
  }

  /**
   * True if the user may edit a site's resources (FREP110 Site Details): {@code FREP_ADMINISTRATOR},
   * {@code FREP_EDITOR}, or any per-district CHR editor.
   *
   * <p>Site records are shared across protocols, so the Biodiversity-only {@link #canEdit()} is the
   * wrong gate here: a CHR district editor already sees their districts' sites
   * ({@code AcceptedSiteService} filters on {@link #canChr}) and maintains the CHR checklists hanging
   * off them, yet could not edit the site those checklists belong to.
   *
   * <p>Deliberately the coarse "CHR anywhere" check, not per-district — Site Details is keyed by site
   * id, and the district-scoped variant would need the site's org unit resolved the way
   * {@code ChrChecklistAuthorizer} does for checklists. <em>Creating</em> a targeted site (FREP200)
   * stays editor-only.
   */
  public boolean canEditSite() {
    return canEdit() || canAnyChr();
  }

  /**
   * True if the user may access CHR for the given 3-letter district {@code org_unit_code}. Sys-admins
   * see every district; a district editor sees only the codes they hold a role for.
   */
  public boolean canChr(String orgUnitCode) {
    if (isSysAdmin()) {
      return true;
    }
    return orgUnitCode != null
        && chrDistrictCodes().contains(orgUnitCode.toUpperCase(Locale.ROOT));
  }

  // ─── Internal helpers ─────────────────────────────────────────────

  /**
   * Returns the raw {@link Jwt} principal from the security context.
   */
  private Jwt getPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof Jwt jwtPrincipal) {
      return jwtPrincipal;
    }
    throw new IllegalStateException("No authenticated JWT principal available");
  }
}

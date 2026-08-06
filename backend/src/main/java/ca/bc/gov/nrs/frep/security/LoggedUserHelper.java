package ca.bc.gov.nrs.frep.security;

import ca.bc.gov.nrs.frep.util.JwtPrincipalUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring bean exposing authorization helpers for the currently authenticated user.
 *
 * <p>Registered as {@code @auth} for programmatic use in services and security configuration.
 *
 * <h3>Access-token migration note</h3>
 * The frontend now sends a Cognito <em>access token</em> (not an ID token).
 * Access tokens contain {@code cognito:groups} and {@code sub} but lack the
 * {@code custom:idp_*} profile claims that the identity helpers below depend on.
 * Those claims are fetched on demand from the Cognito {@code /oauth2/userInfo}
 * endpoint (via {@link CognitoUserInfoService}) and merged into a synthetic
 * claims map so that existing {@link JwtPrincipalUtil} methods continue to work
 * without modification.
 */
@Component("auth")
public class LoggedUserHelper {

  private final CognitoUserInfoService userInfoService;

  public LoggedUserHelper(CognitoUserInfoService userInfoService) {
    this.userInfoService = userInfoService;
  }

  // ─── Identity helpers ──────────────────────────────────────────────
  /**
   * Get the ID from the logged user (e.g. {@code IDIR\jsmith}).
   * Requires the {@code custom:idp_username} and {@code custom:idp_name} claims
   * which are obtained from the Cognito userInfo endpoint.
   */
  public String getLoggedUserId() {
    return JwtPrincipalUtil.getUserId(getEnrichedClaims());
  }
  // ─── Role / authority helpers (these use cognito:groups from the access token) ──

  /**
   * Returns the set of authority strings for the current user (e.g. {@code FREP_ADMIN}).
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
   * Returns {@code true} if the user holds the {@code FREP_ADMIN} authority.
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
   * Returns {@code true} when the user is view-only per legacy {@code RestAction#isViewOnlyUser}:
   * has {@code FREP_VIEW_ONLY} and does not also hold sys-admin or update roles.
   */
  public boolean isViewOnly() {
    Set<String> authorities = getAuthorities();
    return authorities.contains(RoleConstants.VIEW_ONLY_AUTHORITY)
        && !authorities.contains(RoleConstants.SYS_ADMIN_AUTHORITY)
        && !authorities.contains(RoleConstants.UPDATE_AUTHORITY);
  }

  /**
   * Returns {@code true} if the user may perform write operations
   * ({@code FREP_ADMIN} or {@code FREP_EDITOR}).
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
   * True if the user may edit a site's resources (FREP110 Site Details): {@code FREP_ADMIN},
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

  /**
   * Builds a merged claims map that contains:
   * <ol>
   *   <li>All claims from the access token (cognito:groups, sub, etc.)</li>
   *   <li>Profile claims from the Cognito userInfo endpoint
   *       (custom:idp_name, custom:idp_username, email, etc.)</li>
   * </ol>
   * UserInfo claims do NOT overwrite access-token claims if there's a collision.
   */
  private Map<String, Object> getEnrichedClaims() {
    Jwt accessToken = getPrincipal();
    Map<String, Object> userInfoClaims = userInfoService.getUserInfo(accessToken);

    // Start with userInfo (lower precedence), overlay with access token claims
    java.util.HashMap<String, Object> merged = new java.util.HashMap<>(userInfoClaims);
    merged.putAll(accessToken.getClaims());
    return merged;
  }

}

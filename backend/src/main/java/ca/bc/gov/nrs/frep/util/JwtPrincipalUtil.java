package ca.bc.gov.nrs.frep.util;

import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Extracts the caller's identity from BC Gov SSO (Keycloak standard realm) access-token claims.
 *
 * <p>The value every caller ultimately wants is {@link #getUserId(Map)} — the legacy WebADE
 * source-directory token the FREP schema was built around, {@code IDIR\jsmith}. It goes into
 * {@code create_user} / {@code update_user} audit columns, is compared against stored evaluator
 * ids, and is stripped for display. Rows written before this application exist carry the same
 * shape, so the string this class produces has to keep matching them exactly.
 *
 * <h3>Two rules, both of which fail silently if broken</h3>
 * <ol>
 *   <li><b>Provider normalisation.</b> FREP's CSS integration selects <em>IDIR - MFA</em>, which
 *       federates via Azure AD, so the realm reports {@code identity_provider} as
 *       {@code azureidir} — <em>not</em> {@code idir}. This was confirmed against the DEV realm:
 *       every {@code kc_idp_hint} resolves to {@code /broker/azureidir/login}. Mapping the value
 *       verbatim would start writing {@code AZUREIDIR\jsmith} for the same human whose earlier rows
 *       say {@code IDIR\jsmith}: no error, no failing test, just an audit trail that stops joining
 *       up at the cutover date. Every IDIR alias, in any case, is folded to the single string
 *       {@code IDIR}; {@code BCEIDBUSINESS} to the legacy {@code BCEID} token.</li>
 *   <li><b>GUID case-folding.</b> {@code idir_user_guid} and the GUID embedded in
 *       {@code preferred_username} ({@code <guid>@azureidir}) can arrive in <em>different cases in
 *       the same token</em>. Wherever a GUID stands in for identity — which happens whenever
 *       {@code idir_username} is absent, i.e. whenever the access-token mappers are misconfigured —
 *       {@code IDIR\0a1b…} and {@code IDIR\0A1B…} would become two rows for one person. A GUID is a
 *       128-bit number and its hex spelling carries no meaning, so it is upper-cased. A
 *       <em>username</em> is a name: it is passed through exactly as issued.</li>
 * </ol>
 *
 * <p>Both are pinned by {@code JwtPrincipalUtilTest}.
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class JwtPrincipalUtil {

  /** Claim carrying the brokered identity provider's realm alias. */
  private static final String CLAIM_IDENTITY_PROVIDER = "identity_provider";

  /**
   * Realm aliases that mean "IDIR". {@code azureidir} is the one FREP actually sees (its CSS
   * integration selects IDIR - MFA); {@code idir} is the non-MFA broker and is included so that
   * switching the integration cannot silently change what lands in the audit columns.
   */
  private static final Set<String> IDIR_ALIASES = Set.of("idir", "azureidir", "azure-idir");

  /** Legacy FAM/Cognito provider names, which prefixed BCSC this way. */
  private static final String LEGACY_FAM_BCSC_PREFIX = "ca.bc.gov.flnr.fam.";

  /**
   * Builds the user id as {@code PROVIDER\<username>} — e.g. {@code IDIR\jsmith}. Returns an empty
   * string when the token carries no usable identity at all.
   *
   * @param claims the access token's claims
   * @return the legacy source-directory user id, or {@code ""}
   */
  public static String getUserId(Map<String, Object> claims) {
    String username = getIdpUsername(claims);
    if (StringUtils.isBlank(username)) {
      return StringUtils.EMPTY;
    }
    return getProvider(claims) + "\\" + username;
  }

  /**
   * The identity provider, normalised to the legacy WebADE source-directory token the FREP schema
   * was built around ({@code IDIR}, {@code BCEID}, {@code BCSC}) — the string the audit columns,
   * evaluator comparisons and search-display stripping all already contain.
   *
   * <p>Unrecognised providers are upper-cased and returned as-is rather than guessed at: a
   * wrong-but-plausible value is worse than an obviously foreign one.
   */
  public static String getProvider(Map<String, Object> claims) {
    String provider = getClaimValue(claims, CLAIM_IDENTITY_PROVIDER);
    if (StringUtils.isBlank(provider)) {
      return StringUtils.EMPTY;
    }
    if (provider.startsWith(LEGACY_FAM_BCSC_PREFIX)) {
      return "BCSC";
    }
    String normalized = provider.trim().toLowerCase(Locale.ROOT);
    if (IDIR_ALIASES.contains(normalized)) {
      return "IDIR";
    }
    String upperProvider = normalized.toUpperCase(Locale.ROOT);
    return "BCEIDBUSINESS".equals(upperProvider) ? "BCEID" : upperProvider;
  }

  /**
   * The username on its own, without the provider prefix.
   *
   * <p>Resolution order — the first non-blank wins:
   * <ol>
   *   <li>{@code idir_username} / {@code bceid_username} — the real username, used verbatim.</li>
   *   <li>the GUID in {@code preferred_username} ({@code <guid>@azureidir}), upper-cased.</li>
   *   <li>{@code idir_user_guid} / {@code bceid_user_guid}, upper-cased.</li>
   * </ol>
   *
   * <p>Everything after the first entry is a fallback for a token whose username mappers were not
   * added to the <em>access</em> token (a CSS console setting — see the migration playbook). It
   * keeps the app working, but the ids it produces are GUIDs and will not match rows written by
   * the legacy application, so a deployment that lands on this path has a configuration problem
   * rather than a working one.
   */
  public static String getIdpUsername(Map<String, Object> claims) {
    return Stream.of(
            getClaimValue(claims, "idir_username"),
            getClaimValue(claims, "bceid_username"),
            upperCase(guidFromPreferredUsername(claims)),
            upperCase(getClaimValue(claims, "idir_user_guid")),
            upperCase(getClaimValue(claims, "bceid_user_guid")))
        .filter(StringUtils::isNotBlank)
        .findFirst()
        .orElse(StringUtils.EMPTY);
  }

  /** The user's display name ({@code display_name}), or an empty string. */
  public static String getDisplayName(Map<String, Object> claims) {
    return getClaimValue(claims, "display_name");
  }

  /**
   * Pulls the GUID out of {@code preferred_username}, which the standard realm shapes as
   * {@code <guid>@<idp-alias>}. Returns an empty string if the claim is absent or has no
   * {@code @}, since a bare value there is not reliably a GUID.
   */
  private static String guidFromPreferredUsername(Map<String, Object> claims) {
    String preferred = getClaimValue(claims, "preferred_username");
    int at = preferred.indexOf('@');
    return at > 0 ? preferred.substring(0, at) : StringUtils.EMPTY;
  }

  private static String upperCase(String value) {
    return value == null ? StringUtils.EMPTY : value.toUpperCase(Locale.ROOT);
  }

  /**
   * Retrieves the value of a specified claim from the claims map. If the claim is not present,
   * returns an empty string.
   *
   * @param claims The map containing the JWT claims.
   * @param claimName The name of the claim to retrieve.
   * @return The value of the specified claim as a String, or an empty string if the claim is not
   *     present.
   */
  private static String getClaimValue(Map<String, Object> claims, String claimName) {
    if (claims == null) {
      return StringUtils.EMPTY;
    }
    return claims.getOrDefault(claimName, StringUtils.EMPTY).toString();
  }
}

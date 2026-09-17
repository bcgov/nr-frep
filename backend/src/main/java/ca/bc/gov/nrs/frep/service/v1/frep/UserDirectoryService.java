package ca.bc.gov.nrs.frep.service.v1.frep;

import ca.bc.gov.nrs.frep.client.UserLookupClient;
import ca.bc.gov.nrs.frep.client.UserLookupClient.IdirUser;
import ca.bc.gov.nrs.frep.struct.v1.frep.CodeOptionResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.EvaluatorSearchResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Resolves IDIR user details — display name, email — from
 * <a href="https://github.com/bcgov/nr-user-lookup-api">nr-user-lookup-api</a>, the shared BC Gov
 * identity service. Backs the Administration "Add evaluator" search modal (FREP301) and the
 * userid → name display on checklists.
 *
 * <p>This <b>replaces the FAM identity-lookup integration</b> FREP used previously. FAM (through
 * BC Gov SSO) is still where authorisation comes from — it administers the FREP roles — just not
 * the directory.
 *
 * <h3>Two consequences of the swap, both deliberate</h3>
 * <ol>
 *   <li><b>No role filtering.</b> The FAM integration queried
 *       {@code /external/v1/users?role=FREP_EDITOR&idpType=IDIR}, so the search only ever returned
 *       people who already held FREP access. nr-user-lookup-api is a plain IDIR directory with no
 *       concept of application roles, so the search now returns <em>any</em> IDIR user. Picking
 *       someone who has no FREP role is therefore possible; they simply cannot sign in until
 *       granted one in FAM.</li>
 *   <li><b>Service account, not the caller's token.</b> The FAM integration forwarded the caller's
 *       JWT downstream. nr-user-lookup-api validates FREP's own {@code client_credentials} service
 *       account instead — see {@link ca.bc.gov.nrs.frep.client.ClientCredentialsTokenSource}.</li>
 * </ol>
 *
 * <p>Every failure degrades to an empty page or an unresolved name rather than an error, so an
 * unconfigured or unavailable lookup never breaks a checklist screen — the raw userid shows
 * instead.
 */
@Service
public class UserDirectoryService {

  private static final Logger LOG = LoggerFactory.getLogger(UserDirectoryService.class);

  private static final int MIN_PAGE_SIZE = 10;
  private static final int MAX_PAGE_SIZE = 100;
  private static final int DEFAULT_PAGE_SIZE = 25;
  private static final int MAX_CACHE_ENTRIES = 5000;

  private final UserLookupClient client;
  private final int defaultPageSize;

  /**
   * userid (upper-cased, {@code IDIR\} stripped) → resolved display name; {@link Optional#empty()}
   * means "looked up, no such user". Shared across requests since a userid's name does not depend
   * on who is asking.
   */
  private final Map<String, Optional<String>> nameCache = new ConcurrentHashMap<>();

  public UserDirectoryService(
      UserLookupClient client,
      @Value("${ca.bc.gov.nrs.user-directory.default-page-size:25}") int defaultPageSize) {
    this.client = client;
    this.defaultPageSize = defaultPageSize;
  }

  /**
   * Partial-match IDIR search behind the "Add evaluator" modal. At least one of the three criteria
   * must be supplied; nr-user-lookup-api has no "list everyone" mode and an unfiltered search would
   * be neither useful nor kind to the directory.
   *
   * <p>Returns an empty page when nothing matches, when no criteria are given, or when the lookup
   * is unconfigured or failing.
   */
  public EvaluatorSearchResponse searchEvaluators(
      String userId, String firstName, String lastName, int page, int size) {
    int reqPage = page > 0 ? page : 1;
    int reqSize = Math.min(Math.max(size > 0 ? size : defaultPageSize, MIN_PAGE_SIZE), MAX_PAGE_SIZE);

    String user = normalize(userId);
    String first = normalize(firstName);
    String last = normalize(lastName);
    if (user == null && first == null && last == null) {
      return new EvaluatorSearchResponse(List.of(), 0, reqPage, reqSize);
    }

    try {
      List<IdirUser> items = client.searchIdir(user, first, last, reqSize);
      List<CodeOptionResponse> users = new ArrayList<>();
      for (IdirUser item : items) {
        CodeOptionResponse option = toOption(item);
        if (option != null) {
          users.add(option);
        }
      }
      users.sort((a, b) -> a.description().compareToIgnoreCase(b.description()));
      return new EvaluatorSearchResponse(users, users.size(), reqPage, reqSize);
    } catch (RuntimeException ex) {
      LOG.warn("user-lookup evaluator search failed ({}) — returning an empty page",
          ex.getMessage());
      return new EvaluatorSearchResponse(List.of(), 0, reqPage, reqSize);
    }
  }

  /**
   * Best-effort exact lookup by IDIR userid, for turning a stored {@code IDIR\JSMITH} into
   * {@code "Jane Smith (JSMITH)"} on screen.
   *
   * <p>Returns {@link Optional#empty()} when the user cannot be resolved — not found, lookup
   * unconfigured, or an upstream error — so callers fall back to showing the raw userid. Results
   * are cached by userid, including negative results; a transient failure is <b>not</b> cached, so
   * it retries next time.
   */
  public Optional<String> resolveName(String userId) {
    if (!StringUtils.hasText(userId)) {
      return Optional.empty();
    }
    String bare = stripDirectory(userId);
    if (!StringUtils.hasText(bare)) {
      return Optional.empty();
    }
    String key = bare.toUpperCase(Locale.ROOT);

    Optional<String> cached = nameCache.get(key);
    if (cached != null) {
      return cached;
    }

    Optional<String> resolved;
    try {
      resolved = client.getIdirDetail(bare)
          .map(UserDirectoryService::displayName)
          .filter(StringUtils::hasText);
    } catch (RuntimeException ex) {
      // Transient failure — fall back to the userid and DON'T cache, so it retries next time.
      LOG.debug("user-lookup name lookup failed for {} — using the userid ({})",
          bare, ex.getMessage());
      return Optional.empty();
    }

    if (nameCache.size() >= MAX_CACHE_ENTRIES) {
      nameCache.clear();
    }
    nameCache.put(key, resolved);
    return resolved;
  }

  // ── Mapping ───────────────────────────────────────────────────────

  private static CodeOptionResponse toOption(IdirUser user) {
    if (user == null) {
      return null;
    }
    String userId = trimmed(user.userId());
    if (!StringUtils.hasText(userId)) {
      return null;
    }
    return new CodeOptionResponse(userId, displayName(user));
  }

  /**
   * Evaluator display value: {@code "First Last (userid)"}. Falls back to whichever name part the
   * directory has, and to the bare userid alone when it has no name — unchanged from the FAM-backed
   * implementation, so the on-screen format did not move when the source did.
   */
  private static String displayName(IdirUser user) {
    String userId = trimmed(user.userId());
    String first = trimmed(user.firstName());
    String last = trimmed(user.lastName());

    String name;
    if (StringUtils.hasText(first) && StringUtils.hasText(last)) {
      name = first + " " + last;
    } else if (StringUtils.hasText(last)) {
      name = last;
    } else if (StringUtils.hasText(first)) {
      name = first;
    } else {
      name = "";
    }

    if (!StringUtils.hasText(name)) {
      return StringUtils.hasText(userId) ? userId : "";
    }
    return StringUtils.hasText(userId) ? name + " (" + userId + ")" : name;
  }

  /** {@code IDIR\JSMITH} → {@code JSMITH}. nr-user-lookup-api keys on the bare IDIR name. */
  private static String stripDirectory(String userId) {
    String trimmed = userId.trim();
    int slash = trimmed.indexOf('\\');
    return slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
  }

  private static String trimmed(String value) {
    return value == null ? null : value.trim();
  }

  private static String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}

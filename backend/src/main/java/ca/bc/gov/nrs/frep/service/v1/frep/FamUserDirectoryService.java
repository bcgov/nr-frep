package ca.bc.gov.nrs.frep.service.v1.frep;

import ca.bc.gov.nrs.frep.exception.FamServiceException;
import ca.bc.gov.nrs.frep.struct.v1.frep.CodeOptionResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.EvaluatorSearchResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Searches IDIR users who hold the FREP editor role via the FAM external user-search API
 * ({@code GET {base}/external/v1/users?role=FREP_EDITOR&idpType=IDIR}), with optional name/userId
 * filters and pagination. Backs the Administration "Add evaluator" search modal (FREP301). Ports
 * the FAM-lookup pattern from nr-fspts' {@code UserDirectoryService}, but hits the role-filtered
 * {@code /external/v1/users} endpoint (paged, max 100/page) instead of the IDIR name-search one.
 *
 * <p>The caller's Cognito access token is passed straight through as the Bearer token — FAM
 * resolves the calling application from that token's client_id (no service account). Mirrors
 * {@link ca.bc.gov.nrs.frep.security.CognitoUserInfoService}.
 *
 * <p><strong>Scope note:</strong> FAM has no district / org-unit scoping (only forest-client), so
 * results are FREP editors province-wide, not the district-scoped list the legacy WebADE lookup
 * produced.
 *
 * <p>Returns an empty page when the lookup base URL is not configured; a real upstream failure is
 * surfaced (HTTP 502) so the cause is visible rather than a silently-empty result.
 */
@Service
public class FamUserDirectoryService {

  private static final Logger LOG = LoggerFactory.getLogger(FamUserDirectoryService.class);

  private static final String USERS_PATH = "/external/v1/users";
  // FAM /external/v1/users page bounds (EXT_MIN_PAGE_SIZE / EXT_MAX_PAGE_SIZE).
  private static final int MIN_PAGE_SIZE = 10;
  private static final int MAX_PAGE_SIZE = 100;
  private static final int DEFAULT_PAGE_SIZE = 25;
  private static final List<String> IDP_TYPES = List.of("IDIR", "BCEID");
  private static final List<String> FREP_ACCESS_ROLES =
      List.of("FREP_EDITOR", "FREP_ADMIN");
  private static final int MAX_CACHE_ENTRIES = 5000;
  private static final String EVALUATOR_SEARCH_UNAVAILABLE =
      "The evaluator directory is unavailable right now. Please try again later.";

  private final String baseUrl;
  private final String evaluatorRole;
  private final RestClient restClient;
  // userid (upper-cased, IDIR\ stripped) → resolved display name (empty = looked up, not a FREP user).
  // Shared across requests since a userid's name is the same regardless of caller.
  private final Map<String, Optional<String>> nameCache = new ConcurrentHashMap<>();

  public FamUserDirectoryService(
      @Value("${ca.bc.gov.nrs.identity-lookup.base-url:}") String baseUrl,
      @Value("${ca.bc.gov.nrs.identity-lookup.evaluator-role:FREP_EDITOR}") String evaluatorRole,
      @Value("${ca.bc.gov.nrs.identity-lookup.connect-timeout:15s}") Duration connectTimeout,
      @Value("${ca.bc.gov.nrs.identity-lookup.read-timeout:30s}") Duration readTimeout) {
    this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    this.evaluatorRole = evaluatorRole;

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) connectTimeout.toMillis());
    factory.setReadTimeout((int) readTimeout.toMillis());

    // RestClient with a fixed base URL (mirrors nr-fspts' UserDirectoryService). The host/path are
    // pinned at construction from trusted config; the user-supplied filters only ever flow into
    // queryParam() on the framework's UriBuilder below, so they can't redirect the request (avoids
    // the SSRF that a hand-built URI passed to exchange() trips).
    this.restClient = RestClient.builder()
        .baseUrl(this.baseUrl)
        .requestFactory(factory)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  /**
   * Searches FREP editors, optionally filtered by IDIR username / first name / last name, returning
   * a single page. {@code userId}, {@code firstName}, {@code lastName} are FAM "starts-with" filters
   * (blank = no filter). {@code page} is 1-indexed; {@code size} is clamped to FAM's 10..100 range.
   *
   * <p>FAM's {@code idpType} is a single enum per call, so IDIR and BCEID are queried separately and
   * merged. Paging is applied per IdP and the totals summed — in practice FREP editors are IDIR (BCEID
   * is effectively empty), so this reads as IDIR paging.
   */
  public EvaluatorSearchResponse searchEvaluators(
      String userId, String firstName, String lastName, int page, int size) {
    int reqPage = Math.max(page, 1);
    int reqSize = Math.min(Math.max(size > 0 ? size : DEFAULT_PAGE_SIZE, MIN_PAGE_SIZE), MAX_PAGE_SIZE);

    if (!StringUtils.hasText(baseUrl)) {
      LOG.warn("identity-lookup.base-url is not configured — returning an empty evaluator page");
      return new EvaluatorSearchResponse(List.of(), 0, reqPage, reqSize);
    }
    try {
      List<CodeOptionResponse> users = new ArrayList<>();
      long total = 0;
      for (String idpType : IDP_TYPES) {
        FamUserSearchResponse body =
            callUsers(evaluatorRole, idpType, userId, firstName, lastName, reqPage, reqSize);
        if (body == null || body.users() == null) {
          continue;
        }
        body.users().stream()
            .filter(Objects::nonNull)
            .map(FamUserDirectoryService::toOption)
            .filter(Objects::nonNull)
            .forEach(users::add);
        total += body.total();
      }
      return new EvaluatorSearchResponse(users, total, reqPage, reqSize);
    } catch (RestClientResponseException ex) {
      // FAM returned a non-2xx — e.g. 401/403 when the FREP client isn't authorized to call FAM's
      // external API, or 422 for a bad request. Log the upstream detail for devs; return a clean
      // message so the raw FAM body never reaches the UI.
      LOG.error("FAM evaluator search failed: {} {} — body: {}",
          ex.getStatusCode().value(), ex.getStatusText(), ex.getResponseBodyAsString());
      throw new FamServiceException(EVALUATOR_SEARCH_UNAVAILABLE);
    } catch (RuntimeException ex) {
      LOG.error("FAM evaluator search failed", ex);
      throw new FamServiceException(EVALUATOR_SEARCH_UNAVAILABLE);
    }
  }

  /**
   * One FAM {@code /external/v1/users} call for a single role + idpType, with optional starts-with
   * filters. The base URL is fixed on {@code restClient}; user input flows only into {@code queryParam}
   * (encoded by the UriBuilder), so it can't redirect the request (avoids SSRF).
   */
  private FamUserSearchResponse callUsers(
      String role, String idpType, String idpUsername, String firstName, String lastName,
      int page, int size) {
    return restClient.get()
        .uri(uriBuilder -> {
          uriBuilder.path(USERS_PATH)
              .queryParam("role", role)
              .queryParam("idpType", idpType)
              .queryParam("page", page)
              .queryParam("size", size);
          if (StringUtils.hasText(idpUsername)) {
            uriBuilder.queryParam("idpUsername", idpUsername.trim());
          }
          if (StringUtils.hasText(firstName)) {
            uriBuilder.queryParam("firstName", firstName.trim());
          }
          if (StringUtils.hasText(lastName)) {
            uriBuilder.queryParam("lastName", lastName.trim());
          }
          return uriBuilder.build();
        })
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + extractBearerToken())
        .retrieve()
        .body(FamUserSearchResponse.class);
  }

  /**
   * Resolves a checklist evaluator's display name from FAM by userid — used to show names (instead of
   * raw userids) for evaluators who currently have access to the FREP app. Queries
   * {@code /external/v1/users?role=FREP_EDITOR,FREP_ADMIN&idpType=IDIR,BCEID&idpUsername=…}
   * and exact-matches the username (FAM's filter is "starts-with"). Returns empty when the lookup base
   * URL is unset, the userid isn't a current FREP user, or FAM errors — the caller falls back to the
   * userid. Results are cached by userid (names rarely change; identical for every caller).
   */
  public Optional<String> resolveName(String userId) {
    if (!StringUtils.hasText(userId) || !StringUtils.hasText(baseUrl)) {
      return Optional.empty();
    }
    String bare = stripDirectory(userId);
    String key = bare.toUpperCase(Locale.ROOT);
    Optional<String> cached = nameCache.get(key);
    if (cached != null) {
      return cached;
    }
    Optional<String> resolved;
    try {
      resolved = lookupName(bare);
    } catch (RuntimeException ex) {
      // Transient FAM failure — fall back to the userid and DON'T cache, so it retries next time.
      LOG.debug("FAM name lookup failed for {} — using the userid ({})", bare, ex.getMessage());
      return Optional.empty();
    }
    if (nameCache.size() >= MAX_CACHE_ENTRIES) {
      nameCache.clear();
    }
    nameCache.put(key, resolved);
    return resolved;
  }

  private Optional<String> lookupName(String bareUserId) {
    // idpType is a single enum and role is one value per call, so probe each IdP × FREP-role combo
    // and return the first exact username match. Most evaluators match on (IDIR, FREP_EDITOR) — the
    // first probe; only userids with no FREP access run the full set (then get negatively cached).
    for (String idpType : IDP_TYPES) {
      for (String role : FREP_ACCESS_ROLES) {
        FamUserSearchResponse body =
            callUsers(role, idpType, bareUserId, null, null, 1, MIN_PAGE_SIZE);
        if (body == null || body.users() == null) {
          continue;
        }
        Optional<String> match = body.users().stream()
            .filter(Objects::nonNull)
            .filter(user -> bareUserId.equalsIgnoreCase(trimmed(user.idpUsername())))
            .map(FamUserDirectoryService::displayName)
            .filter(StringUtils::hasText)
            .findFirst();
        if (match.isPresent()) {
          return match;
        }
      }
    }
    return Optional.empty();
  }

  private static String stripDirectory(String userId) {
    String trimmed = userId.trim();
    int slash = trimmed.indexOf('\\');
    return slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
  }

  private static CodeOptionResponse toOption(FamUser user) {
    String userId = trimmed(user.idpUsername());
    if (!StringUtils.hasText(userId)) {
      return null;
    }
    return new CodeOptionResponse(userId, displayName(user));
  }

  /**
   * Evaluator display value: {@code "First Last (userid)"}. Falls back to whichever name part FAM
   * has, and to the bare userid alone when FAM has no name.
   */
  private static String displayName(FamUser user) {
    String userId = trimmed(user.idpUsername());
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

  private static String trimmed(String value) {
    return value == null ? null : value.trim();
  }

  private String extractBearerToken() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      return jwtAuth.getToken().getTokenValue();
    }
    throw new IllegalStateException("No valid JWT bearer token in the security context");
  }

  // FAM /external/v1/users paged response — only the fields we need; ignore roles, guid, idpType, …
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record FamUserSearchResponse(
      @JsonProperty("total") long total,
      @JsonProperty("page") int page,
      @JsonProperty("size") int size,
      @JsonProperty("users") List<FamUser> users) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record FamUser(
      @JsonProperty("idpUsername") String idpUsername,
      @JsonProperty("firstName") String firstName,
      @JsonProperty("lastName") String lastName) {}
}

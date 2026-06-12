package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.CodeOptionResponse;
import ca.bc.gov.nrs.frep.dto.frep.EvaluatorSearchResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

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
@Profile("oracle")
public class FamUserDirectoryService {

  private static final Logger LOG = LoggerFactory.getLogger(FamUserDirectoryService.class);

  private static final String USERS_PATH = "/external/v1/users";
  // FAM /external/v1/users page bounds (EXT_MIN_PAGE_SIZE / EXT_MAX_PAGE_SIZE).
  private static final int MIN_PAGE_SIZE = 10;
  private static final int MAX_PAGE_SIZE = 100;
  private static final int DEFAULT_PAGE_SIZE = 25;

  private final String baseUrl;
  private final String evaluatorRole;
  private final RestTemplate restTemplate;

  public FamUserDirectoryService(
      @Value("${ca.bc.gov.nrs.identity-lookup.base-url:}") String baseUrl,
      @Value("${ca.bc.gov.nrs.identity-lookup.evaluator-role:FREP_EDITOR}") String evaluatorRole,
      @Value("${ca.bc.gov.nrs.identity-lookup.connect-timeout:15s}") Duration connectTimeout,
      @Value("${ca.bc.gov.nrs.identity-lookup.read-timeout:30s}") Duration readTimeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) connectTimeout.toMillis());
    factory.setReadTimeout((int) readTimeout.toMillis());
    this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    this.evaluatorRole = evaluatorRole;
    this.restTemplate = new RestTemplate(factory);
  }

  /**
   * Searches FREP editors, optionally filtered by IDIR username / first name / last name, returning
   * a single page. {@code userId}, {@code firstName}, {@code lastName} are FAM "starts-with" filters
   * (blank = no filter). {@code page} is 1-indexed; {@code size} is clamped to FAM's 10..100 range.
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
      UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
          .path(USERS_PATH)
          .queryParam("role", evaluatorRole)
          .queryParam("idpType", "IDIR")
          .queryParam("page", reqPage)
          .queryParam("size", reqSize);
      if (StringUtils.hasText(userId)) {
        builder.queryParam("idpUsername", userId.trim());
      }
      if (StringUtils.hasText(firstName)) {
        builder.queryParam("firstName", firstName.trim());
      }
      if (StringUtils.hasText(lastName)) {
        builder.queryParam("lastName", lastName.trim());
      }
      // encode() handles spaces/special chars in the name filters; pass a URI so RestTemplate
      // doesn't re-encode it.
      URI uri = builder.encode().build().toUri();

      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(extractBearerToken());
      headers.setAccept(List.of(MediaType.APPLICATION_JSON));

      ResponseEntity<FamUserSearchResponse> response = restTemplate.exchange(
          uri, HttpMethod.GET, new HttpEntity<>(headers), FamUserSearchResponse.class);

      FamUserSearchResponse body = response.getBody();
      if (body == null) {
        return new EvaluatorSearchResponse(List.of(), 0, reqPage, reqSize);
      }
      List<CodeOptionResponse> users = (body.users() == null ? List.<FamUser>of() : body.users())
          .stream()
          .filter(Objects::nonNull)
          .map(FamUserDirectoryService::toOption)
          .filter(Objects::nonNull)
          .toList();
      return new EvaluatorSearchResponse(
          users,
          body.total(),
          body.page() > 0 ? body.page() : reqPage,
          body.size() > 0 ? body.size() : reqSize);
    } catch (RestClientResponseException ex) {
      // FAM returned a non-2xx — e.g. 401/403 when the FREP client isn't authorized to call FAM's
      // external API, or 422 for a bad request. Surface it so the cause is visible.
      LOG.error("FAM evaluator search failed: {} {} — body: {}",
          ex.getStatusCode().value(), ex.getStatusText(), ex.getResponseBodyAsString());
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "Evaluator search failed (HTTP " + ex.getStatusCode().value() + "): "
              + ex.getResponseBodyAsString());
    } catch (RuntimeException ex) {
      LOG.error("FAM evaluator search failed", ex);
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "Evaluator search failed: " + ex.getMessage());
    }
  }

  private static CodeOptionResponse toOption(FamUser user) {
    String userId = trimmed(user.idpUsername());
    if (!StringUtils.hasText(userId)) {
      return null;
    }
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
      name = userId;
    }
    return new CodeOptionResponse(userId, name);
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

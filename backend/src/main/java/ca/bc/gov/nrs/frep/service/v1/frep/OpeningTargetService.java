package ca.bc.gov.nrs.frep.service.v1.frep;

import ca.bc.gov.nrs.frep.repository.v1.OpeningTargetRepository;
import ca.bc.gov.nrs.frep.struct.v1.frep.OpeningSearchCriteria;
import ca.bc.gov.nrs.frep.struct.v1.frep.OpeningSearchResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.PagedResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.TargetedSiteValidationResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * FREP200 "Add Target Site": search the opening inventory and validate a chosen opening for targeting.
 *
 * <p>Targeting an opening reuses the existing site-detail flow (mark the resources {@code TAR} and
 * save, which spawns the checklists). This service only covers the two new front-doors — the opening
 * picker and the {@code ADD_TARGETED_SITE} pre-check — turning the proc's raw error codes into the
 * friendly reasons shown in the picker.
 */
@Service
public class OpeningTargetService {

  /** Friendly text for each {@code FREP_200_ACCEPTED_SITES.ADD_TARGETED_SITE} error code. */
  private static final Map<String, String> ERROR_MESSAGES = Map.of(
      "frep.web.error.usr.invalidOrg",
      "This opening belongs to a different district. It can only be targeted by the district that owns it.",
      "frep.web.error.usr.invalidBlock",
      "This opening has cut blocks still in an active harvest status, so it can't be targeted yet.");

  /** Hard cap on rows returned per search call (legacy SIL56 was a single unbounded result set). */
  static final int MAX_PAGE_SIZE = 100;

  private final OpeningTargetRepository openingTargetRepository;

  public OpeningTargetService(OpeningTargetRepository openingTargetRepository) {
    this.openingTargetRepository = openingTargetRepository;
  }

  /**
   * One page of openings matching the filters. {@code pageSize} is clamped to {@link #MAX_PAGE_SIZE}
   * (and at least 1) so every call returns at most 100 rows; {@code pageNumber} is zero-based. Counts
   * the full match set so the picker can page through all openings.
   */
  public PagedResponse<OpeningSearchResult> searchOpenings(
      OpeningSearchCriteria criteria, int pageNumber, int pageSize) {
    int safeSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
    int safePage = Math.max(0, pageNumber);
    long total = openingTargetRepository.countOpenings(criteria);
    List<OpeningSearchResult> content =
        total == 0
            ? List.of()
            : openingTargetRepository.searchOpenings(criteria, safePage * safeSize, safeSize);
    int totalPages = (int) Math.ceil((double) total / safeSize);
    return new PagedResponse<>(content, total, totalPages, safePage, safeSize);
  }

  /**
   * Validate an opening for targeting. {@code valid} is true when the proc returns no error codes;
   * otherwise {@code messages} holds a friendly reason per code (unknown codes pass through verbatim
   * so nothing is hidden from the user).
   */
  public TargetedSiteValidationResponse validateTargetedSite(String openingId, String orgUnit) {
    String rawErrors = openingTargetRepository.validateTargetedSite(openingId, orgUnit);
    List<String> messages = Arrays.stream(rawErrors.split(";"))
        .map(String::trim)
        .filter(StringUtils::hasText)
        .map(code -> ERROR_MESSAGES.getOrDefault(code, code))
        .toList();
    return new TargetedSiteValidationResponse(messages.isEmpty(), messages, openingId, orgUnit);
  }
}

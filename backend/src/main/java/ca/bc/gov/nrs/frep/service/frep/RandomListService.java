package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.RandomListSiteResponse;
import ca.bc.gov.nrs.frep.repository.frep.RandomListRepository;
import ca.bc.gov.nrs.frep.repository.frep.RandomListRow;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * District Random List (FREP100) lookup backed by the legacy Oracle schema via
 * {@link RandomListRepository}.
 *
 * <p>Legacy equivalent: {@code FREP_100_DIST_RAND_LIST.GET}.
 */
@Service
@Profile("oracle")
public class RandomListService {

  private final RandomListRepository randomListRepository;

  public RandomListService(RandomListRepository randomListRepository) {
    this.randomListRepository = randomListRepository;
  }

  /**
   * Return the randomly generated site list for a given master list year and district.
   *
   * @param effectiveYear  master list year ({@code FREP_EVALUATION_YEAR.effective_year})
   * @param orgUnit        district org unit number; {@code null} or blank → all districts
   */
  public List<RandomListSiteResponse> findRandomList(String effectiveYear, String orgUnit) {
    return randomListRepository.findRandomList(effectiveYear, orgUnit).stream()
        .map(RandomListService::toResponse)
        .toList();
  }

  static RandomListSiteResponse toResponse(RandomListRow row) {
    return new RandomListSiteResponse(
        row.frepSelectedSiteId(),
        "Y".equalsIgnoreCase(row.isReview()),
        row.orgUnitCode(),
        row.openingNumber(),
        row.openingId(),
        row.licenceId(),
        row.cuttingPermitId(),
        row.cutBlockId(),
        parseDouble(row.grossArea()),
        parseDouble(row.netArea()),
        blankToNull(row.disturbanceStartDate()),
        blankToNull(row.disturbanceEndDate()),
        List.copyOf(row.existingChecklistTypes())
    );
  }

  private static Double parseDouble(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Double.valueOf(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}

package ca.bc.gov.nrs.frep.service.v1.frep;

import ca.bc.gov.nrs.frep.struct.v1.frep.RandomListResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.RandomListSiteResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.RandomListSummaryResponse;
import ca.bc.gov.nrs.frep.repository.v1.RandomListRepository;
import ca.bc.gov.nrs.frep.repository.v1.bean.RandomListResult;
import ca.bc.gov.nrs.frep.repository.v1.bean.RandomListRow;
import ca.bc.gov.nrs.frep.repository.v1.bean.RandomListSummary;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * District Random List (FREP100) lookup backed by the legacy Oracle schema via
 * {@link RandomListRepository}.
 *
 * <p>Legacy equivalent: {@code FREP_100_DIST_RAND_LIST.GET}.
 */
@Service
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
  public RandomListResponse findRandomList(String effectiveYear, String orgUnit) {
    RandomListResult result = randomListRepository.findRandomList(effectiveYear, orgUnit);
    return new RandomListResponse(
        toSummaryResponse(result.summary()),
        result.rows().stream().map(RandomListService::toResponse).toList()
    );
  }

  static RandomListSummaryResponse toSummaryResponse(RandomListSummary summary) {
    return new RandomListSummaryResponse(
        blankToNull(summary.orgUnitDesc()),
        parseCount(summary.biodiversity()),
        parseCount(summary.culturalHeritage()),
        parseCount(summary.riparian()),
        parseCount(summary.water())
    );
  }

  private static int parseCount(String value) {
    if (value == null || value.isBlank()) {
      return 0;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
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
        parseDouble(row.exhibitArea()),
        parseDouble(row.grossArea()),
        parseDouble(row.netArea()),
        blankToNull(row.disturbanceStartDate()),
        blankToNull(row.disturbanceEndDate()),
        blankToNull(row.managementUnit()),
        List.copyOf(row.existingChecklists())
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

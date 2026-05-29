package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.RandomListSiteResponse;
import java.util.List;

/**
 * District Random List (FREP100) read API.
 *
 * <p>Legacy equivalent: {@code FREP_100_DIST_RAND_LIST.get(...)}.
 */
public interface RandomListService {

  /**
   * Return the randomly generated site list for a given master list year and district.
   *
   * @param effectiveYear  master list year ({@code FREP_EVALUATION_YEAR.effective_year})
   * @param orgUnit        district org unit number; {@code null} or blank → all districts
   */
  List<RandomListSiteResponse> findRandomList(String effectiveYear, String orgUnit);
}

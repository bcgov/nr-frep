package ca.bc.gov.nrs.frep.repository.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.OpeningSearchCriteria;
import ca.bc.gov.nrs.frep.struct.v1.frep.OpeningSearchResult;
import java.util.List;

/**
 * Data access for the FREP200 "Add Target Site" flow: searching the corporate opening inventory and
 * validating an opening before it is targeted.
 */
public interface OpeningTargetRepository {

  /**
   * One page of openings matching the criteria, for the opening-search picker. Mirrors the legacy
   * SIL56 Opening Tenure Search ({@code SIL_56_OPEN_TEN_SRCH_V002.get}). {@code offset}/{@code pageSize}
   * drive {@code OFFSET ... FETCH NEXT}.
   */
  List<OpeningSearchResult> searchOpenings(OpeningSearchCriteria criteria, int offset, int pageSize);

  /** Total openings matching the same criteria (for the page count). */
  long countOpenings(OpeningSearchCriteria criteria);

  /**
   * Validate that {@code openingId} can be targeted by {@code orgUnitNo} via
   * {@code FREP_200_ACCEPTED_SITES.ADD_TARGETED_SITE}. Returns the proc's raw {@code p_error_message}
   * (a {@code ;}-separated list of error codes), or empty when the opening is valid to target.
   */
  String validateTargetedSite(String openingId, String orgUnitNo);
}

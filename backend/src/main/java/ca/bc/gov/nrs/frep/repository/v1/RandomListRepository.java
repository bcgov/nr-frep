package ca.bc.gov.nrs.frep.repository.v1;

import ca.bc.gov.nrs.frep.repository.v1.bean.RandomListResult;

/** Contract for FREP100 District Random List ({@code FREP_100_DIST_RAND_LIST}). */
public interface RandomListRepository {
  RandomListResult findRandomList(String effectiveYear, String orgUnitNo);
}

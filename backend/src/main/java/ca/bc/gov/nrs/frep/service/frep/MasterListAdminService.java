package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.GenerateMasterListRequest;
import ca.bc.gov.nrs.frep.dto.frep.MasterListAdminResponse;

/**
 * Sys-admin API for FREP700 Generate Master List.
 *
 * <p>Legacy equivalent: {@code FREP_700_GEN_MASTER.get} / {@code generate} /
 * {@code save_comments}.
 */
public interface MasterListAdminService {

  /**
   * Look up the current criteria + (if already generated) the per-district stats.
   */
  MasterListAdminResponse getMasterListCriteria(String effectiveYear);

  /**
   * Trigger a (stubbed) generation run and return the resulting state.
   */
  MasterListAdminResponse generateMasterList(GenerateMasterListRequest request);
}

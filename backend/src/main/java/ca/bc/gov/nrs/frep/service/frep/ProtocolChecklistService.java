package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.ProtocolChecklistResponse;
import java.util.Optional;

/**
 * Read API for protocol checklists (FREP210/211/212, 230–235, 250–254).
 */
public interface ProtocolChecklistService {

  /**
   * Look up a protocol checklist by protocol type and checklist id.
   *
   * @param protocolType  one of {@code bio}, {@code rip}, {@code wat} (case-insensitive)
   * @param checklistId   PK from {@code FREP_*_CHECKLIST}
   */
  Optional<ProtocolChecklistResponse> findChecklist(String protocolType, String checklistId);
}

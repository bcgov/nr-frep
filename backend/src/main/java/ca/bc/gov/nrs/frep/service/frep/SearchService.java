package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.ChecklistSearchResult;
import ca.bc.gov.nrs.frep.dto.frep.ClientSearchResult;
import java.util.List;

/**
 * Read API backing FREP400 (checklist search) and FREP410 (client search).
 *
 * <p>Legacy equivalents: {@code FREP_CHECKLIST_SEARCH.search}, client lookup via
 * forest-client REST calls.
 */
public interface SearchService {

  /**
   * Run a checklist search. Any blank parameter means "any".
   */
  List<ChecklistSearchResult> searchChecklists(
      String effectiveYear,
      String orgUnit,
      String protocolType,
      String licenceId,
      String cuttingPermitId,
      String cutBlockId,
      String openingId,
      String clientNumber,
      String checklistStatusCode
  );

  /**
   * Run a client search. {@code clientNumber} and {@code clientName} are OR'd, with both
   * accepting case-insensitive substring matches.
   */
  List<ClientSearchResult> searchClients(String clientNumber, String clientName);
}

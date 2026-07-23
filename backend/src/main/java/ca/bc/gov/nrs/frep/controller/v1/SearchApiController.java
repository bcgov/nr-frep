package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.ChecklistSearchResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.ClientSearchResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.PagedResponse;
import ca.bc.gov.nrs.frep.endpoint.v1.SearchApiEndpoint;
import ca.bc.gov.nrs.frep.service.v1.frep.SearchService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API for FREP400 (checklist search) and FREP410 (client search). Mappings declared on
 * {@link SearchApiEndpoint}.
 */
@RestController
public class SearchApiController implements SearchApiEndpoint {

  private final SearchService searchService;

  public SearchApiController(SearchService searchService) {
    this.searchService = searchService;
  }

  @Override
  public ResponseEntity<PagedResponse<ChecklistSearchResult>> searchChecklistsPaginated(
      String effectiveYear,
      String orgUnit,
      String protocolType,
      String licenceId,
      String cuttingPermitId,
      String cutBlockId,
      String openingId,
      String clientNumber,
      String checklistStatusCode,
      String checklistId,
      String evaluationDateFrom,
      String evaluationDateTo,
      int pageNumber,
      int pageSize,
      String sort
  ) {
    return ResponseEntity.ok(searchService.searchChecklistsPaged(
        effectiveYear, orgUnit, protocolType, licenceId,
        cuttingPermitId, cutBlockId, openingId, clientNumber, checklistStatusCode,
        checklistId, evaluationDateFrom, evaluationDateTo, pageNumber, pageSize, sort));
  }

  @Override
  public ResponseEntity<List<ClientSearchResult>> searchClients(
      String clientNumber,
      String clientAcronym,
      String clientName,
      String legalFirstName,
      String legalMiddleName
  ) {
    return ResponseEntity.ok(searchService.searchClients(
        clientNumber, clientAcronym, clientName, legalFirstName, legalMiddleName));
  }
}

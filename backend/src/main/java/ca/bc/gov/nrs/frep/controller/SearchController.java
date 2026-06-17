package ca.bc.gov.nrs.frep.controller;

import ca.bc.gov.nrs.frep.dto.frep.ChecklistSearchResult;
import ca.bc.gov.nrs.frep.dto.frep.ClientSearchResult;
import ca.bc.gov.nrs.frep.service.frep.SearchService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API for FREP400 (checklist search) and FREP410 (client search).
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

  private final SearchService searchService;

  public SearchController(SearchService searchService) {
    this.searchService = searchService;
  }

  @GetMapping("/checklists")
  public ResponseEntity<List<ChecklistSearchResult>> searchChecklists(
      @RequestParam(required = false) String effectiveYear,
      @RequestParam(required = false) String orgUnit,
      @RequestParam(required = false) String protocolType,
      @RequestParam(required = false) String licenceId,
      @RequestParam(required = false) String cuttingPermitId,
      @RequestParam(required = false) String cutBlockId,
      @RequestParam(required = false) String openingId,
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String checklistStatusCode,
      @RequestParam(required = false) String checklistId,
      @RequestParam(required = false) String evaluationDateFrom,
      @RequestParam(required = false) String evaluationDateTo
  ) {
    return ResponseEntity.ok(searchService.searchChecklists(
        effectiveYear, orgUnit, protocolType, licenceId,
        cuttingPermitId, cutBlockId, openingId, clientNumber, checklistStatusCode,
        checklistId, evaluationDateFrom, evaluationDateTo));
  }

  @GetMapping("/clients")
  public ResponseEntity<List<ClientSearchResult>> searchClients(
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientAcronym,
      @RequestParam(required = false) String clientName,
      @RequestParam(required = false) String legalFirstName,
      @RequestParam(required = false) String legalMiddleName
  ) {
    return ResponseEntity.ok(searchService.searchClients(
        clientNumber, clientAcronym, clientName, legalFirstName, legalMiddleName));
  }

  // CSV export moved to ReportController (GET /api/v1/reports/checklist-search/csv).
}

package ca.bc.gov.nrs.frep.endpoint.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.ChecklistSearchResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.ClientSearchResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.PagedResponse;
import java.util.List;
import ca.bc.gov.nrs.frep.security.FrepAuthorities;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for FREP400 (checklist search) and FREP410 (client search). Implemented by
 * {@link ca.bc.gov.nrs.frep.controller.v1.SearchApiController}.
 */
@RequestMapping("/api/v1/search")
public interface SearchApiEndpoint {

  /**
   * Server-side paginated checklist search — returns a page + true total (no 5000 VARRAY cap).
   * {@code sort} is {@code "field"} or {@code "field,(asc|desc)"} over a whitelisted set of fields.
   */
  /**
   * Which rows come back is decided in SQL, from the caller — see
   * {@code SearchService.buildCriteria}. This annotation is the admission check in front of it, not
   * a replacement for it: without one, a caller holding no FREP role still ran a COUNT and a paged
   * SELECT over the four-table union and received an empty 200. Now they are refused before any
   * query. {@link FrepAuthorities#SITE_EDIT} is the widest gate FREP has, so no role that can use
   * the screen loses access.
   */
  @PreAuthorize(FrepAuthorities.SITE_EDIT)
  @GetMapping("/checklists/paginated")
  ResponseEntity<PagedResponse<ChecklistSearchResult>> searchChecklistsPaginated(
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
      @RequestParam(required = false) String evaluationDateTo,
      @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
      @RequestParam(name = "sort", defaultValue = "") String sort);

  @GetMapping("/clients")
  ResponseEntity<List<ClientSearchResult>> searchClients(
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientAcronym,
      @RequestParam(required = false) String clientName,
      @RequestParam(required = false) String legalFirstName,
      @RequestParam(required = false) String legalMiddleName);
}

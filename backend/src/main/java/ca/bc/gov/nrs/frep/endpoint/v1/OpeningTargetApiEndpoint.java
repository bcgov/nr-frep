package ca.bc.gov.nrs.frep.endpoint.v1;

import ca.bc.gov.nrs.frep.security.FrepAuthorities;
import ca.bc.gov.nrs.frep.struct.v1.frep.OpeningSearchResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.PagedResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.TargetedSiteRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.TargetedSiteValidationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the FREP200 "Add Target Site" flow (v1): search the opening inventory and validate
 * a chosen opening before it is targeted. Both are editor actions (targeting creates work).
 *
 * <p>Legacy equivalent: the SIL56 Opening Tenure Search reached via the "Add Target Site" button, plus
 * {@code FREP_200_ACCEPTED_SITES.ADD_TARGETED_SITE}.
 */
@RequestMapping("/api/v1")
public interface OpeningTargetApiEndpoint {

  @PreAuthorize(FrepAuthorities.CONTENT_EDIT)
  @GetMapping("/openings/search")
  ResponseEntity<PagedResponse<OpeningSearchResult>> searchOpenings(
      @RequestParam String orgUnit,
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientLocnCode,
      @RequestParam(required = false) String openingNumber1,
      @RequestParam(required = false) String openingNumber2,
      @RequestParam(required = false) String openingNumber3,
      @RequestParam(required = false) String openingNumber4,
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String openingId,
      @RequestParam(required = false) String licenseeOpeningId,
      @RequestParam(required = false) String cuttingPermitId,
      @RequestParam(required = false) String timberMark,
      @RequestParam(required = false) String cutBlockId,
      @RequestParam(required = false) String blockStatusSt,
      @RequestParam(required = false) String openCategoryCode,
      @RequestParam(required = false) String openingStatusCode,
      @RequestParam(required = false) String dateType,
      @RequestParam(required = false) String distStartDate,
      @RequestParam(required = false) String distEndDate,
      @RequestParam(required = false) String dueLateDateFrom,
      @RequestParam(required = false) String dueLateDateTo,
      @RequestParam(required = false) String fgDueEarlyDate,
      @RequestParam(required = false) String fgDueLateDate,
      @RequestParam(required = false) String updateDateFrom,
      @RequestParam(required = false) String updateDateTo,
      @RequestParam(required = false) String includeAllP87Ind,
      @RequestParam(required = false) String sortBy,
      @RequestParam(defaultValue = "0") int pageNumber,
      @RequestParam(defaultValue = "100") int pageSize);

  @PreAuthorize(FrepAuthorities.CONTENT_EDIT)
  @PostMapping("/accepted-sites/targeted")
  ResponseEntity<TargetedSiteValidationResponse> validateTargetedSite(
      @RequestBody TargetedSiteRequest request);
}

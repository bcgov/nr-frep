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
 * a chosen opening before it is targeted.
 *
 * <p>Both are {@link FrepAuthorities#SITE_EDIT} — editors <em>or</em> any per-district CHR editor —
 * to match {@code SiteDetailApiEndpoint.createTargetedSite}, which is the call that actually creates
 * the site. They were {@code CONTENT_EDIT} until 2026-09-14, which made the flow unreachable for the
 * one role its final step admits: a CHR district editor was allowed to create a targeted site but
 * was denied the opening search needed to choose one, so the button led to a 403 rather than to a
 * site. Keep all three steps on the same authority — a stricter gate on a step *before* the one that
 * writes buys nothing and only breaks the journey.
 *
 * <p>Legacy equivalent: the SIL56 Opening Tenure Search reached via the "Add Target Site" button, plus
 * {@code FREP_200_ACCEPTED_SITES.ADD_TARGETED_SITE}.
 */
@RequestMapping("/api/v1")
public interface OpeningTargetApiEndpoint {

  @PreAuthorize(FrepAuthorities.SITE_EDIT)
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

  @PreAuthorize(FrepAuthorities.SITE_EDIT)
  @PostMapping("/accepted-sites/targeted")
  ResponseEntity<TargetedSiteValidationResponse> validateTargetedSite(
      @RequestBody TargetedSiteRequest request);
}

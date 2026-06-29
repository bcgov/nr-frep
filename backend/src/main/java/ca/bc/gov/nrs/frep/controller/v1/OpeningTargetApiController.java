package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.endpoint.v1.OpeningTargetApiEndpoint;
import ca.bc.gov.nrs.frep.service.v1.frep.OpeningTargetService;
import ca.bc.gov.nrs.frep.struct.v1.frep.OpeningSearchCriteria;
import ca.bc.gov.nrs.frep.struct.v1.frep.OpeningSearchResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.PagedResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.TargetedSiteRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.TargetedSiteValidationResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements {@link OpeningTargetApiEndpoint}: opening search + targeting validation for the FREP200
 * "Add Target Site" flow. Thin — delegates to {@link OpeningTargetService}.
 */
@RestController
public class OpeningTargetApiController implements OpeningTargetApiEndpoint {

  private final OpeningTargetService openingTargetService;

  public OpeningTargetApiController(OpeningTargetService openingTargetService) {
    this.openingTargetService = openingTargetService;
  }

  @Override
  public ResponseEntity<PagedResponse<OpeningSearchResult>> searchOpenings(
      String orgUnit,
      String clientNumber,
      String clientLocnCode,
      String openingNumber1,
      String openingNumber2,
      String openingNumber3,
      String openingNumber4,
      String forestFileId,
      String openingId,
      String licenseeOpeningId,
      String cuttingPermitId,
      String timberMark,
      String cutBlockId,
      String blockStatusSt,
      String openCategoryCode,
      String openingStatusCode,
      String dateType,
      String distStartDate,
      String distEndDate,
      String dueLateDateFrom,
      String dueLateDateTo,
      String fgDueEarlyDate,
      String fgDueLateDate,
      String updateDateFrom,
      String updateDateTo,
      String includeAllP87Ind,
      String sortBy,
      int pageNumber,
      int pageSize) {
    if (StringUtils.isBlank(orgUnit)) {
      return ResponseEntity.badRequest().build();
    }
    OpeningSearchCriteria criteria = new OpeningSearchCriteria(
        orgUnit.trim(), clientNumber, clientLocnCode,
        openingNumber1, openingNumber2, openingNumber3, openingNumber4,
        forestFileId, openingId, licenseeOpeningId, cuttingPermitId, timberMark, cutBlockId,
        blockStatusSt, openCategoryCode, openingStatusCode,
        dateType, distStartDate, distEndDate, dueLateDateFrom, dueLateDateTo,
        fgDueEarlyDate, fgDueLateDate, updateDateFrom, updateDateTo,
        includeAllP87Ind, sortBy);
    return ResponseEntity.ok(openingTargetService.searchOpenings(criteria, pageNumber, pageSize));
  }

  @Override
  public ResponseEntity<TargetedSiteValidationResponse> validateTargetedSite(
      TargetedSiteRequest request) {
    if (request == null
        || StringUtils.isBlank(request.openingId())
        || StringUtils.isBlank(request.orgUnit())) {
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok(
        openingTargetService.validateTargetedSite(
            request.openingId().trim(), request.orgUnit().trim()));
  }
}

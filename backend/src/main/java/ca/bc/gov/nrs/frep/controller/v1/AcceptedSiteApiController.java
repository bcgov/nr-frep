package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.AcceptedSiteResponse;
import ca.bc.gov.nrs.frep.endpoint.v1.AcceptedSiteApiEndpoint;
import ca.bc.gov.nrs.frep.service.v1.frep.AcceptedSiteService;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only accepted sites API (Phase 1 vertical slice). Request mappings are declared on
 * {@link AcceptedSiteApiEndpoint}; this controller implements the contract and delegates to the
 * service.
 *
 * <p>Legacy equivalent: {@code GET /ext/frep/resource/acceptedSites}
 */
@RestController
public class AcceptedSiteApiController implements AcceptedSiteApiEndpoint {

  private final AcceptedSiteService acceptedSiteService;

  public AcceptedSiteApiController(AcceptedSiteService acceptedSiteService) {
    this.acceptedSiteService = acceptedSiteService;
  }

  @Override
  public ResponseEntity<List<AcceptedSiteResponse>> getAcceptedSites(
      String effectiveYear,
      String orgUnit,
      String protocolType
  ) {
    if (StringUtils.isBlank(effectiveYear) || StringUtils.isBlank(orgUnit)) {
      return ResponseEntity.badRequest().build();
    }

    return ResponseEntity.ok(
        acceptedSiteService.findAcceptedSites(
            effectiveYear.trim(),
            orgUnit.trim(),
            protocolType
        )
    );
  }
}

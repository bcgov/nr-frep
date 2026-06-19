package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.RandomListResponse;
import ca.bc.gov.nrs.frep.endpoint.v1.RandomListApiEndpoint;
import ca.bc.gov.nrs.frep.service.v1.frep.RandomListService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * FREP100 District Random List API. Mappings declared on {@link RandomListApiEndpoint}.
 *
 * <p>Legacy equivalent: {@code frep100RandomListAction} backed by {@code FREP_100_DIST_RAND_LIST.get}.
 */
@RestController
public class RandomListApiController implements RandomListApiEndpoint {

  private final RandomListService randomListService;

  public RandomListApiController(RandomListService randomListService) {
    this.randomListService = randomListService;
  }

  @Override
  public ResponseEntity<RandomListResponse> getRandomList(String effectiveYear, String orgUnit) {
    if (StringUtils.isBlank(effectiveYear)) {
      return ResponseEntity.badRequest().build();
    }

    return ResponseEntity.ok(
        randomListService.findRandomList(
            effectiveYear.trim(),
            StringUtils.isBlank(orgUnit) ? null : orgUnit.trim()
        )
    );
  }

  // CSV export moved to ReportApiController (GET /api/v1/reports/random-list/csv).
}

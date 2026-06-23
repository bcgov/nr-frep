package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.exception.InvalidPayloadException;
import ca.bc.gov.nrs.frep.exception.errors.ApiError;
import ca.bc.gov.nrs.frep.struct.v1.frep.RandomListResponse;
import ca.bc.gov.nrs.frep.endpoint.v1.RandomListApiEndpoint;
import ca.bc.gov.nrs.frep.service.v1.frep.RandomListService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

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
      ApiError error = ApiError.builder().timestamp(LocalDateTime.now()).message("Effective year cannot be blank").status(BAD_REQUEST).build();
      throw new InvalidPayloadException(error);
    }

    return ResponseEntity.ok(
        randomListService.findRandomList(
            effectiveYear.trim(),
            StringUtils.isBlank(orgUnit) ? null : orgUnit.trim()
        )
    );
  }
}

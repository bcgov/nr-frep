package ca.bc.gov.nrs.frep.controller;

import ca.bc.gov.nrs.frep.dto.frep.NotImplementedResponse;
import ca.bc.gov.nrs.frep.dto.frep.RandomListSiteResponse;
import ca.bc.gov.nrs.frep.service.frep.RandomListService;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FREP100 District Random List API.
 *
 * <p>Legacy equivalent: {@code frep100RandomListAction} backed by
 * {@code FREP_100_DIST_RAND_LIST.get(...)}.
 */
@RestController
@RequestMapping("/api/v1")
public class RandomListController {

  private final RandomListService randomListService;

  public RandomListController(RandomListService randomListService) {
    this.randomListService = randomListService;
  }

  @GetMapping("/random-list")
  public ResponseEntity<List<RandomListSiteResponse>> getRandomList(
      @RequestParam String effectiveYear,
      @RequestParam(required = false) String orgUnit
  ) {
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

  /**
   * Export the district random list to Excel/CSV (legacy "Export to Excel" button on
   * {@code frep100RandomList.jsp}).
   *
   * <p>TODO: implement server-side CSV/XLSX generation from the random-list rows.
   * Returns HTTP 501 until then.
   */
  @GetMapping("/random-list/export")
  public ResponseEntity<NotImplementedResponse> exportRandomList() {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(NotImplementedResponse.of(
        "export-random-list",
        "Export to Excel for the district random list is not yet available."));
  }
}

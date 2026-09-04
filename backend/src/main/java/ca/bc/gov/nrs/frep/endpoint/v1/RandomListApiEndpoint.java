package ca.bc.gov.nrs.frep.endpoint.v1;

import ca.bc.gov.nrs.frep.security.FrepAuthorities;
import ca.bc.gov.nrs.frep.struct.v1.frep.RandomListResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the FREP100 District Random List API (v1). Implemented by
 * {@link ca.bc.gov.nrs.frep.controller.v1.RandomListApiController}.
 *
 * <p>Legacy equivalent: {@code frep100RandomListAction} backed by {@code FREP_100_DIST_RAND_LIST.get}.
 */
@RequestMapping("/api/v1")
public interface RandomListApiEndpoint {

  /**
   * The district random list — the year's sampling frame of selected openings.
   *
   * <p>{@link FrepAuthorities#SITE_EDIT} because it is the widest gate FREP has
   * ({@code canEdit() || canAnyChr()}), so every role that can use the app keeps access while a
   * caller holding no FREP role is turned away. It is <em>not</em> a district scope: the response
   * still spans every district, and {@code orgUnit} is optional.
   */
  @PreAuthorize(FrepAuthorities.SITE_EDIT)
  @GetMapping("/random-list")
  ResponseEntity<RandomListResponse> getRandomList(
      @RequestParam String effectiveYear,
      @RequestParam(required = false) String orgUnit);
}

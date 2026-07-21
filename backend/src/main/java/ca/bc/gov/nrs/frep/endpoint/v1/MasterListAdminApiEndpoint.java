package ca.bc.gov.nrs.frep.endpoint.v1;

import ca.bc.gov.nrs.frep.security.FrepAuthorities;
import ca.bc.gov.nrs.frep.struct.v1.frep.GenerateMasterListRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.MasterListAdminResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the FREP700 Master List admin endpoints. Implemented by
 * {@link ca.bc.gov.nrs.frep.controller.v1.MasterListAdminApiController}. Every operation — including
 * the read — is {@code FREP_ADMIN} only ({@link FrepAuthorities#ADMIN}).
 */
@RequestMapping("/api/v1/admin/master-list")
public interface MasterListAdminApiEndpoint {

  @PreAuthorize(FrepAuthorities.ADMIN)
  @GetMapping
  ResponseEntity<MasterListAdminResponse> getMasterListCriteria(
      @RequestParam String effectiveYear);

  @PreAuthorize(FrepAuthorities.ADMIN)
  @PostMapping("/generate")
  ResponseEntity<MasterListAdminResponse> generateMasterList(
      @RequestBody GenerateMasterListRequest request);

  @PreAuthorize(FrepAuthorities.ADMIN)
  @PostMapping("/comments")
  ResponseEntity<MasterListAdminResponse> saveComments(
      @RequestBody GenerateMasterListRequest request);

  @PreAuthorize(FrepAuthorities.ADMIN)
  @DeleteMapping
  ResponseEntity<MasterListAdminResponse> deleteMasterList(
      @RequestParam String effectiveYear);
}

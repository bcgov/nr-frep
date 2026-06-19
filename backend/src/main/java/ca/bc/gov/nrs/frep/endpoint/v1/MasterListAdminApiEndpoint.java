package ca.bc.gov.nrs.frep.endpoint.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.GenerateMasterListRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.MasterListAdminResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the FREP700 Master List admin endpoints (sys-admin only). Implemented by
 * {@link ca.bc.gov.nrs.frep.controller.v1.MasterListAdminApiController}.
 */
@RequestMapping("/api/v1/admin/master-list")
public interface MasterListAdminApiEndpoint {

  @GetMapping
  ResponseEntity<MasterListAdminResponse> getMasterListCriteria(
      @RequestParam String effectiveYear);

  @PostMapping("/generate")
  ResponseEntity<MasterListAdminResponse> generateMasterList(
      @RequestBody GenerateMasterListRequest request);

  @PostMapping("/regenerate")
  ResponseEntity<MasterListAdminResponse> regenerateDistrict(
      @RequestParam String effectiveYear,
      @RequestParam String orgUnitNo);

  @PostMapping("/comments")
  ResponseEntity<MasterListAdminResponse> saveComments(
      @RequestBody GenerateMasterListRequest request);

  @DeleteMapping
  ResponseEntity<MasterListAdminResponse> deleteMasterList(
      @RequestParam String effectiveYear);
}

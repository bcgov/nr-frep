package ca.bc.gov.nrs.frep.controller;

import ca.bc.gov.nrs.frep.dto.GenerateMasterListRequest;
import ca.bc.gov.nrs.frep.dto.MasterListAdminResponse;
import ca.bc.gov.nrs.frep.service.MasterListAdminService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FREP700 Master List admin endpoints.
 *
 * <p>Sys-admin only. Authorization is enforced at the URL level by the
 * commented-out {@code /api/v1/admin/**} rule in {@code ApiAuthorizationCustomizer}
 * (re-enabled at deploy time).
 */
@RestController
@RequestMapping("/api/v1/admin/master-list")
public class MasterListAdminController {

  private final MasterListAdminService masterListAdminService;

  public MasterListAdminController(MasterListAdminService masterListAdminService) {
    this.masterListAdminService = masterListAdminService;
  }

  @GetMapping
  public ResponseEntity<MasterListAdminResponse> getMasterListCriteria(
      @RequestParam String effectiveYear
  ) {
    if (StringUtils.isBlank(effectiveYear)) {
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok(
        masterListAdminService.getMasterListCriteria(effectiveYear.trim()));
  }

  @PostMapping("/generate")
  public ResponseEntity<MasterListAdminResponse> generateMasterList(
      @RequestBody GenerateMasterListRequest request
  ) {
    if (request == null || StringUtils.isBlank(request.effectiveYear())) {
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok(masterListAdminService.generateMasterList(request));
  }
}

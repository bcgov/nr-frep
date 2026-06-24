package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.exception.InvalidPayloadException;
import ca.bc.gov.nrs.frep.exception.errors.ApiError;
import ca.bc.gov.nrs.frep.struct.v1.frep.GenerateMasterListRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.MasterListAdminResponse;
import ca.bc.gov.nrs.frep.endpoint.v1.MasterListAdminApiEndpoint;
import ca.bc.gov.nrs.frep.service.v1.frep.MasterListAdminService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * FREP700 Master List admin endpoints. Mappings declared on {@link MasterListAdminApiEndpoint}.
 *
 * <p>Sys-admin only. Authorization is enforced at the URL level by the {@code /api/v1/admin/**} rule
 * in {@code ApiAuthorizationCustomizer}.
 */
@RestController
public class MasterListAdminApiController implements MasterListAdminApiEndpoint {

  private final MasterListAdminService masterListAdminService;

  public MasterListAdminApiController(MasterListAdminService masterListAdminService) {
    this.masterListAdminService = masterListAdminService;
  }

  @Override
  public ResponseEntity<MasterListAdminResponse> getMasterListCriteria(String effectiveYear) {
    if (StringUtils.isBlank(effectiveYear)) {
      ApiError error = ApiError.builder().timestamp(LocalDateTime.now()).message("Effective year cannot be blank").status(BAD_REQUEST).build();
      throw new InvalidPayloadException(error);
    }
    return ResponseEntity.ok(masterListAdminService.getMasterListCriteria(effectiveYear.trim()));
  }

  @Override
  public ResponseEntity<MasterListAdminResponse> generateMasterList(GenerateMasterListRequest request) {
    if (StringUtils.isBlank(request.effectiveYear())) {
      ApiError error = ApiError.builder().timestamp(LocalDateTime.now()).message("Effective year cannot be blank").status(BAD_REQUEST).build();
      throw new InvalidPayloadException(error);
    }
    return ResponseEntity.ok(masterListAdminService.generateMasterList(request));
  }

  @Override
  public ResponseEntity<MasterListAdminResponse> regenerateDistrict(
      String effectiveYear, String orgUnitNo) {
    if (StringUtils.isBlank(effectiveYear)) {
      ApiError error = ApiError.builder().timestamp(LocalDateTime.now()).message("Effective year cannot be blank").status(BAD_REQUEST).build();
      throw new InvalidPayloadException(error);
    }

    if (StringUtils.isBlank(orgUnitNo)) {
      ApiError error = ApiError.builder().timestamp(LocalDateTime.now()).message("Org unit cannot be blank").status(BAD_REQUEST).build();
      throw new InvalidPayloadException(error);
    }
    return ResponseEntity.ok(
        masterListAdminService.regenerateDistrict(effectiveYear, orgUnitNo));
  }

  @Override
  public ResponseEntity<MasterListAdminResponse> saveComments(GenerateMasterListRequest request) {
    if (StringUtils.isBlank(request.effectiveYear())) {
      ApiError error = ApiError.builder().timestamp(LocalDateTime.now()).message("Effective year cannot be blank").status(BAD_REQUEST).build();
      throw new InvalidPayloadException(error);
    }
    return ResponseEntity.ok(
        masterListAdminService.saveComments(request.effectiveYear(), request.comments()));
  }

  @Override
  public ResponseEntity<MasterListAdminResponse> deleteMasterList(String effectiveYear) {
    if (StringUtils.isBlank(effectiveYear)) {
      ApiError error = ApiError.builder().timestamp(LocalDateTime.now()).message("Effective year cannot be blank").status(BAD_REQUEST).build();
      throw new InvalidPayloadException(error);
    }
    return ResponseEntity.ok(masterListAdminService.deleteList(effectiveYear));
  }
}

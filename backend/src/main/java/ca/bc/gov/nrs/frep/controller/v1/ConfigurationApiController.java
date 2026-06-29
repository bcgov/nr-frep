package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.BecRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.CodeOptionResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.EvaluatorSearchResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.MasterListYearResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.OrgUnitResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.ProtocolResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.RejectionReasonResponse;
import ca.bc.gov.nrs.frep.endpoint.v1.ConfigurationApiEndpoint;
import ca.bc.gov.nrs.frep.service.v1.frep.ConfigurationService;
import ca.bc.gov.nrs.frep.service.v1.frep.FamUserDirectoryService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reference / lookup endpoints used to populate UI dropdowns. Mappings declared on
 * {@link ConfigurationApiEndpoint}.
 *
 * <p>Legacy equivalents are itemized in {@link ConfigurationService}.
 */
@RestController
public class ConfigurationApiController implements ConfigurationApiEndpoint {

  private final ConfigurationService configurationService;
  private final FamUserDirectoryService famUserDirectoryService;

  public ConfigurationApiController(
      ConfigurationService configurationService,
      FamUserDirectoryService famUserDirectoryService) {
    this.configurationService = configurationService;
    this.famUserDirectoryService = famUserDirectoryService;
  }

  @Override
  public ResponseEntity<List<MasterListYearResponse>> getMasterListYears() {
    return ResponseEntity.ok(configurationService.getMasterListYears());
  }

  @Override
  public ResponseEntity<List<OrgUnitResponse>> getOrgUnits() {
    return ResponseEntity.ok(configurationService.getOrgUnits());
  }

  @Override
  public ResponseEntity<List<ProtocolResponse>> getProtocols() {
    return ResponseEntity.ok(configurationService.getProtocols());
  }

  @Override
  public ResponseEntity<List<RejectionReasonResponse>> getRejectionReasons() {
    return ResponseEntity.ok(configurationService.getRejectionReasons());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getStreamClasses() {
    return ResponseEntity.ok(configurationService.getStreamClasses());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getSiteAccessCodes() {
    return ResponseEntity.ok(configurationService.getSiteAccessCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getSiteEvaluationCodes() {
    return ResponseEntity.ok(configurationService.getSiteEvaluationCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getBlockStatusCodes() {
    return ResponseEntity.ok(configurationService.getBlockStatusCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getOpenCategoryCodes() {
    return ResponseEntity.ok(configurationService.getOpenCategoryCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getOpeningStatusCodes() {
    return ResponseEntity.ok(configurationService.getOpeningStatusCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getStrataTypes() {
    return ResponseEntity.ok(configurationService.getStrataTypes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getResourceValueStatusCodes() {
    return ResponseEntity.ok(configurationService.getResourceValueStatusCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getChecklistStatusCodes() {
    return ResponseEntity.ok(configurationService.getChecklistStatusCodes());
  }

  @Override
  public ResponseEntity<List<BecRow>> searchBec(
      String zone, String subzone, String variant, String phase, String siteSeries,
      String siteSeriesPhase, String seral) {
    return ResponseEntity.ok(
        configurationService.searchBec(zone, subzone, variant, phase, siteSeries, siteSeriesPhase,
            seral));
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getChecklistAnswers(String exclude) {
    return ResponseEntity.ok(configurationService.getChecklistAnswers(exclude));
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getSpecies() {
    return ResponseEntity.ok(configurationService.getSpeciesCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getWildlifeTreeDecay() {
    return ResponseEntity.ok(configurationService.getWildlifeTreeDecayCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getCwdDecay() {
    return ResponseEntity.ok(configurationService.getCwdDecayCodes());
  }

  @Override
  public ResponseEntity<List<CodeOptionResponse>> getEvaluators(String checklistId, String protocol) {
    return ResponseEntity.ok(configurationService.getEvaluators(checklistId, protocol));
  }

  @Override
  public ResponseEntity<EvaluatorSearchResponse> searchEvaluators(
      String userId, String firstName, String lastName, int page, int size) {
    return ResponseEntity.ok(
        famUserDirectoryService.searchEvaluators(userId, firstName, lastName, page, size));
  }
}

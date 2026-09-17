package ca.bc.gov.nrs.frep.endpoint.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.BecRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.CodeOptionResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.EvaluatorSearchResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.MasterListYearResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.OrgUnitResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.ProtocolResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.RejectionReasonResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the reference / lookup endpoints used to populate UI dropdowns. Implemented by
 * {@link ca.bc.gov.nrs.frep.controller.v1.ConfigurationApiController}.
 */
@RequestMapping("/api/v1/configuration")
public interface ConfigurationApiEndpoint {

  @GetMapping("/master-list-years")
  ResponseEntity<List<MasterListYearResponse>> getMasterListYears();

  /** Existing years plus the next not-yet-created year — for the FREP700 Generate Master List screen. */
  @GetMapping("/master-list-years/new")
  ResponseEntity<List<MasterListYearResponse>> getNewMasterListYears();

  @GetMapping("/org-units")
  ResponseEntity<List<OrgUnitResponse>> getOrgUnits();

  @GetMapping("/protocols")
  ResponseEntity<List<ProtocolResponse>> getProtocols();

  @GetMapping("/rejection-reasons")
  ResponseEntity<List<RejectionReasonResponse>> getRejectionReasons();

  @GetMapping("/stream-classes")
  ResponseEntity<List<CodeOptionResponse>> getStreamClasses();

  @GetMapping("/site-access-codes")
  ResponseEntity<List<CodeOptionResponse>> getSiteAccessCodes();

  @GetMapping("/site-evaluation-codes")
  ResponseEntity<List<CodeOptionResponse>> getSiteEvaluationCodes();

  @GetMapping("/block-status-codes")
  ResponseEntity<List<CodeOptionResponse>> getBlockStatusCodes();

  @GetMapping("/open-category-codes")
  ResponseEntity<List<CodeOptionResponse>> getOpenCategoryCodes();

  @GetMapping("/opening-status-codes")
  ResponseEntity<List<CodeOptionResponse>> getOpeningStatusCodes();

  @GetMapping("/strata-types")
  ResponseEntity<List<CodeOptionResponse>> getStrataTypes();

  @GetMapping("/resource-value-status-codes")
  ResponseEntity<List<CodeOptionResponse>> getResourceValueStatusCodes(
      @RequestParam(name = "exclude", required = false) String exclude);

  /*
   * CHR code lists. Prefixed `chr-` deliberately: `/site-evaluation-codes` above already serves
   * SLR from FREP_SITE_EVALUATION_CODE, and CHR's ratings come from a different table entirely.
   */

  @GetMapping("/chr-feature-class-codes")
  ResponseEntity<List<CodeOptionResponse>> getChrFeatureClassCodes();

  @GetMapping("/chr-feature-info-source-codes")
  ResponseEntity<List<CodeOptionResponse>> getChrFeatureInfoSourceCodes();

  @GetMapping("/chr-reserve-type-codes")
  ResponseEntity<List<CodeOptionResponse>> getChrReserveTypeCodes();

  @GetMapping("/chr-site-evaluation-codes")
  ResponseEntity<List<CodeOptionResponse>> getChrSiteEvaluationCodes();

  @GetMapping("/chr-participant-role-codes")
  ResponseEntity<List<CodeOptionResponse>> getChrParticipantRoleCodes();

  @GetMapping("/checklist-status-codes")
  ResponseEntity<List<CodeOptionResponse>> getChecklistStatusCodes();

  @GetMapping("/bec-search")
  ResponseEntity<List<BecRow>> searchBec(
      @RequestParam(name = "zone", required = false) String zone,
      @RequestParam(name = "subzone", required = false) String subzone,
      @RequestParam(name = "variant", required = false) String variant,
      @RequestParam(name = "phase", required = false) String phase,
      @RequestParam(name = "siteSeries", required = false) String siteSeries,
      @RequestParam(name = "siteSeriesPhase", required = false) String siteSeriesPhase,
      @RequestParam(name = "seral", required = false) String seral);

  @GetMapping("/checklist-answers")
  ResponseEntity<List<CodeOptionResponse>> getChecklistAnswers(
      @RequestParam(name = "exclude", required = false) String exclude);

  @GetMapping("/species")
  ResponseEntity<List<CodeOptionResponse>> getSpecies();

  @GetMapping("/wildlife-tree-decay")
  ResponseEntity<List<CodeOptionResponse>> getWildlifeTreeDecay();

  @GetMapping("/cwd-decay")
  ResponseEntity<List<CodeOptionResponse>> getCwdDecay();

  @GetMapping("/evaluators")
  ResponseEntity<List<CodeOptionResponse>> getEvaluators(
      @RequestParam(name = "checklistId") String checklistId,
      @RequestParam(name = "protocol", required = false, defaultValue = "SLR") String protocol);

  @GetMapping("/evaluator-search")
  ResponseEntity<EvaluatorSearchResponse> searchEvaluators(
      @RequestParam(name = "userId", required = false) String userId,
      @RequestParam(name = "firstName", required = false) String firstName,
      @RequestParam(name = "lastName", required = false) String lastName,
      @RequestParam(name = "page", required = false, defaultValue = "1") int page,
      @RequestParam(name = "size", required = false, defaultValue = "25") int size);
}

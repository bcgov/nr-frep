package ca.bc.gov.nrs.frep.controller;

import ca.bc.gov.nrs.frep.dto.frep.BecRow;
import ca.bc.gov.nrs.frep.dto.frep.CodeOptionResponse;
import ca.bc.gov.nrs.frep.dto.frep.MasterListYearResponse;
import ca.bc.gov.nrs.frep.dto.frep.OrgUnitResponse;
import ca.bc.gov.nrs.frep.dto.frep.ProtocolResponse;
import ca.bc.gov.nrs.frep.dto.frep.RejectionReasonResponse;

import java.util.List;

import ca.bc.gov.nrs.frep.service.frep.ConfigurationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reference / lookup endpoints used to populate UI dropdowns.
 *
 * <p>Legacy equivalents are itemized in {@link ConfigurationService}.
 */
@RestController
@RequestMapping("/api/v1/configuration")
public class ConfigurationController {

  private final ConfigurationService configurationService;

  public ConfigurationController(ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  @GetMapping("/master-list-years")
  public ResponseEntity<List<MasterListYearResponse>> getMasterListYears() {
    return ResponseEntity.ok(configurationService.getMasterListYears());
  }

  @GetMapping("/org-units")
  public ResponseEntity<List<OrgUnitResponse>> getOrgUnits() {
    return ResponseEntity.ok(configurationService.getOrgUnits());
  }

  @GetMapping("/protocols")
  public ResponseEntity<List<ProtocolResponse>> getProtocols() {
    return ResponseEntity.ok(configurationService.getProtocols());
  }

  @GetMapping("/rejection-reasons")
  public ResponseEntity<List<RejectionReasonResponse>> getRejectionReasons() {
    return ResponseEntity.ok(configurationService.getRejectionReasons());
  }

  @GetMapping("/stream-classes")
  public ResponseEntity<List<CodeOptionResponse>> getStreamClasses() {
    return ResponseEntity.ok(configurationService.getStreamClasses());
  }

  @GetMapping("/strata-types")
  public ResponseEntity<List<CodeOptionResponse>> getStrataTypes() {
    return ResponseEntity.ok(configurationService.getStrataTypes());
  }

  @GetMapping("/bec-search")
  public ResponseEntity<List<BecRow>> searchBec(
      @RequestParam(name = "zone", required = false) String zone,
      @RequestParam(name = "subzone", required = false) String subzone,
      @RequestParam(name = "variant", required = false) String variant,
      @RequestParam(name = "phase", required = false) String phase,
      @RequestParam(name = "siteSeries", required = false) String siteSeries,
      @RequestParam(name = "siteSeriesPhase", required = false) String siteSeriesPhase,
      @RequestParam(name = "seral", required = false) String seral) {
    return ResponseEntity.ok(
        configurationService.searchBec(zone, subzone, variant, phase, siteSeries, siteSeriesPhase,
            seral));
  }

  @GetMapping("/checklist-answers")
  public ResponseEntity<List<CodeOptionResponse>> getChecklistAnswers(
      @RequestParam(name = "exclude", required = false) String exclude) {
    return ResponseEntity.ok(configurationService.getChecklistAnswers(exclude));
  }

  @GetMapping("/species")
  public ResponseEntity<List<CodeOptionResponse>> getSpecies() {
    return ResponseEntity.ok(configurationService.getSpeciesCodes());
  }

  @GetMapping("/wildlife-tree-decay")
  public ResponseEntity<List<CodeOptionResponse>> getWildlifeTreeDecay() {
    return ResponseEntity.ok(configurationService.getWildlifeTreeDecayCodes());
  }

  @GetMapping("/cwd-decay")
  public ResponseEntity<List<CodeOptionResponse>> getCwdDecay() {
    return ResponseEntity.ok(configurationService.getCwdDecayCodes());
  }

  /** Evaluators for a checklist (defaults to biodiversity/SLB) — the FREP212 "Evaluated By" list. */
  @GetMapping("/evaluators")
  public ResponseEntity<List<CodeOptionResponse>> getEvaluators(
      @RequestParam(name = "checklistId") String checklistId,
      @RequestParam(name = "protocol", required = false, defaultValue = "SLB") String protocol) {
    return ResponseEntity.ok(configurationService.getEvaluators(checklistId, protocol));
  }
}

package ca.bc.gov.nrs.frep.controller;

import ca.bc.gov.nrs.frep.dto.frep.BioPlot;
import ca.bc.gov.nrs.frep.dto.frep.BioPlotRow;
import ca.bc.gov.nrs.frep.dto.frep.BioStratum;
import ca.bc.gov.nrs.frep.dto.frep.BioStratumRow;
import ca.bc.gov.nrs.frep.dto.frep.BiodiversityOpening;
import ca.bc.gov.nrs.frep.dto.frep.AttachmentContent;
import ca.bc.gov.nrs.frep.dto.frep.AttachmentRow;
import ca.bc.gov.nrs.frep.dto.frep.AttachmentUploadRequest;
import ca.bc.gov.nrs.frep.dto.frep.RiparianFieldData;
import ca.bc.gov.nrs.frep.dto.frep.RiparianNotes;
import ca.bc.gov.nrs.frep.dto.frep.RiparianFinalComments;
import ca.bc.gov.nrs.frep.dto.frep.RiparianOtherIndicators;
import ca.bc.gov.nrs.frep.dto.frep.RiparianQuestions;
import ca.bc.gov.nrs.frep.dto.frep.RiparianSpecificImpacts;
import ca.bc.gov.nrs.frep.dto.frep.AdministrationData;
import ca.bc.gov.nrs.frep.dto.frep.RiparianStreamOpening;
import ca.bc.gov.nrs.frep.dto.frep.WaterAssessment;
import ca.bc.gov.nrs.frep.dto.frep.WaterRange;
import ca.bc.gov.nrs.frep.dto.frep.WaterSampleArea;
import ca.bc.gov.nrs.frep.dto.frep.WaterSampleSite;
import ca.bc.gov.nrs.frep.dto.frep.ProtocolChecklistResponse;
import ca.bc.gov.nrs.frep.service.frep.ProtocolChecklistService;
import ca.bc.gov.nrs.frep.service.frep.ProtocolChecklistService.ProtocolSubmitValidationException;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read + edit/submit API for protocol checklists.
 *
 * <p>Legacy equivalents: {@code frep210BIOOpeningAction} … {@code frep254WtrSummaryAction};
 * submit/unsubmit via {@code FrepTombstoneAction} / {@code FREP_TOMBSTONE}.
 */
@RestController
@RequestMapping("/api/v1")
public class ProtocolChecklistController {

  private final ProtocolChecklistService protocolChecklistService;

  public ProtocolChecklistController(ProtocolChecklistService protocolChecklistService) {
    this.protocolChecklistService = protocolChecklistService;
  }

  @GetMapping("/protocol-checklists/{protocolType}/{checklistId}")
  public ResponseEntity<ProtocolChecklistResponse> getChecklist(
      @PathVariable String protocolType,
      @PathVariable String checklistId
  ) {
    return protocolChecklistService.findChecklist(protocolType, checklistId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /** Submit a checklist (all protocols). Returns 400 + message codes on validation failure. */
  @PostMapping("/protocol-checklists/{protocolType}/{checklistId}/submit")
  public ResponseEntity<?> submit(
      @PathVariable String protocolType,
      @PathVariable String checklistId
  ) {
    try {
      protocolChecklistService.submit(protocolType, checklistId);
      return ResponseEntity.ok().build();
    } catch (ProtocolSubmitValidationException ex) {
      return ResponseEntity.badRequest().body(Map.of("validationErrors", ex.getMessages()));
    }
  }

  /** Revert a submitted checklist to active (all protocols). */
  @PostMapping("/protocol-checklists/{protocolType}/{checklistId}/unsubmit")
  public ResponseEntity<Void> unsubmit(
      @PathVariable String protocolType,
      @PathVariable String checklistId
  ) {
    protocolChecklistService.unsubmit(protocolType, checklistId);
    return ResponseEntity.ok().build();
  }

  /** Typed read of the Biodiversity Opening (screen 210) for editing. */
  @GetMapping("/protocol-checklists/bio/{checklistId}/opening")
  public ResponseEntity<BiodiversityOpening> getBiodiversityOpening(@PathVariable String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getBiodiversityOpening(checklistId));
  }

  /** Save the Biodiversity Opening via FREP_210_BIO_OPENING.SAVE. */
  @PutMapping("/protocol-checklists/bio/{checklistId}/opening")
  public ResponseEntity<BiodiversityOpening> saveBiodiversityOpening(
      @PathVariable String checklistId,
      @RequestBody BiodiversityOpening opening
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveBiodiversityOpening(checklistId, opening));
  }

  // --- Biodiversity strata (FREP screen 211) ---

  @GetMapping("/protocol-checklists/bio/{checklistId}/strata")
  public ResponseEntity<List<BioStratumRow>> listBioStrata(@PathVariable String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.listBioStrata(checklistId));
  }

  @GetMapping("/protocol-checklists/bio/strata/{stratumId}")
  public ResponseEntity<BioStratum> getBioStratum(@PathVariable String stratumId) {
    return ResponseEntity.ok(protocolChecklistService.getBioStratum(stratumId));
  }

  @GetMapping("/protocol-checklists/bio/strata-next-number")
  public ResponseEntity<Map<String, String>> nextStratumNumber() {
    return ResponseEntity.ok(Map.of("stratumNumber", protocolChecklistService.nextStratumNumber()));
  }

  /** Upsert a stratum (FREP_211_BIOSTRATUM.SAVE_STRATUM); checklist id from the path is authoritative. */
  @PostMapping("/protocol-checklists/bio/{checklistId}/strata")
  public ResponseEntity<BioStratum> saveBioStratum(
      @PathVariable String checklistId,
      @RequestBody BioStratum stratum
  ) {
    return ResponseEntity.ok(
        protocolChecklistService.saveBioStratum(stratum.withChecklist(checklistId)));
  }

  @DeleteMapping("/protocol-checklists/bio/strata/{stratumId}")
  public ResponseEntity<Void> deleteBioStratum(
      @PathVariable String stratumId,
      @RequestParam String revisionCount
  ) {
    protocolChecklistService.deleteBioStratum(stratumId, revisionCount);
    return ResponseEntity.ok().build();
  }

  // --- Biodiversity plots (FREP screen 212) ---

  @GetMapping("/protocol-checklists/bio/strata/{stratumId}/plots")
  public ResponseEntity<List<BioPlotRow>> listBioPlots(@PathVariable String stratumId) {
    return ResponseEntity.ok(protocolChecklistService.listBioPlots(stratumId));
  }

  @GetMapping("/protocol-checklists/bio/plots/{plotId}")
  public ResponseEntity<BioPlot> getBioPlot(@PathVariable String plotId) {
    return ResponseEntity.ok(protocolChecklistService.getBioPlot(plotId));
  }

  /** Upsert a plot + its stand/CWD rows (FREP_212_BIOPLOT); stratum id from the path is authoritative. */
  @PostMapping("/protocol-checklists/bio/strata/{stratumId}/plots")
  public ResponseEntity<BioPlot> saveBioPlot(
      @PathVariable String stratumId,
      @RequestBody BioPlot plot
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveBioPlot(plot.withStratum(stratumId)));
  }

  @DeleteMapping("/protocol-checklists/bio/plots/{plotId}")
  public ResponseEntity<Void> deleteBioPlot(
      @PathVariable String plotId,
      @RequestParam String revisionCount
  ) {
    protocolChecklistService.deleteBioPlot(plotId, revisionCount);
    return ResponseEntity.ok().build();
  }

  // --- Administration (FREP301) ---

  @GetMapping("/protocol-checklists/rip/{checklistId}/administration")
  public ResponseEntity<AdministrationData> getRipAdministration(@PathVariable String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getRipAdministration(checklistId));
  }

  @PutMapping("/protocol-checklists/rip/{checklistId}/administration")
  public ResponseEntity<AdministrationData> saveRipAdministration(
      @PathVariable String checklistId,
      @RequestBody AdministrationData admin
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveRipAdministration(checklistId, admin));
  }

  @PostMapping("/protocol-checklists/rip/{checklistId}/administration/team")
  public ResponseEntity<AdministrationData> addRipTeamMember(
      @PathVariable String checklistId,
      @RequestParam String evaluator,
      @RequestParam(defaultValue = "false") boolean teamLead
  ) {
    return ResponseEntity.ok(
        protocolChecklistService.addRipTeamMember(checklistId, evaluator, teamLead));
  }

  @DeleteMapping("/protocol-checklists/rip/{checklistId}/administration/team/{evaluatorUserid}")
  public ResponseEntity<AdministrationData> removeRipTeamMember(
      @PathVariable String checklistId,
      @PathVariable String evaluatorUserid,
      @RequestParam(required = false) String revisionCount
  ) {
    return ResponseEntity.ok(
        protocolChecklistService.removeRipTeamMember(checklistId, evaluatorUserid, revisionCount));
  }

  // --- Notes ---

  @GetMapping("/protocol-checklists/rip/{checklistId}/notes")
  public ResponseEntity<RiparianNotes> getRipNotes(@PathVariable String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getRipNotes(checklistId));
  }

  @PutMapping("/protocol-checklists/rip/{checklistId}/notes")
  public ResponseEntity<RiparianNotes> saveRipNotes(
      @PathVariable String checklistId,
      @RequestBody RiparianNotes notes
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveRipNotes(checklistId, notes));
  }

  // --- Attachments ---

  @GetMapping("/protocol-checklists/rip/{checklistId}/attachments")
  public ResponseEntity<List<AttachmentRow>> getRipAttachments(@PathVariable String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getRipAttachments(checklistId));
  }

  // Content is returned as JSON; Jackson base64-encodes the byte[] data (like the CHR photo flow).
  @GetMapping("/protocol-checklists/rip/{checklistId}/attachments/{attachmentId}/content")
  public ResponseEntity<AttachmentContent> getRipAttachmentContent(
      @PathVariable String checklistId,
      @PathVariable String attachmentId
  ) {
    return ResponseEntity.ok(
        protocolChecklistService.getRipAttachmentContent(checklistId, attachmentId));
  }

  @PostMapping("/protocol-checklists/rip/{checklistId}/attachments")
  public ResponseEntity<List<AttachmentRow>> uploadRipAttachment(
      @PathVariable String checklistId,
      @RequestBody AttachmentUploadRequest request
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveRipAttachment(
        checklistId, request.fileName(), request.description(), request.contentType(),
        request.data() == null ? new byte[0] : request.data()));
  }

  @DeleteMapping("/protocol-checklists/rip/{checklistId}/attachments/{attachmentId}")
  public ResponseEntity<List<AttachmentRow>> deleteRipAttachment(
      @PathVariable String checklistId,
      @PathVariable String attachmentId
  ) {
    return ResponseEntity.ok(
        protocolChecklistService.deleteRipAttachment(checklistId, attachmentId));
  }

  // --- Riparian stream opening (FREP screen 230) ---

  @GetMapping("/protocol-checklists/rip/{checklistId}/stream-opening")
  public ResponseEntity<RiparianStreamOpening> getRipStreamOpening(@PathVariable String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getRipStreamOpening(checklistId));
  }

  @PutMapping("/protocol-checklists/rip/{checklistId}/stream-opening")
  public ResponseEntity<RiparianStreamOpening> saveRipStreamOpening(
      @PathVariable String checklistId,
      @RequestBody RiparianStreamOpening opening
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveRipStreamOpening(checklistId, opening));
  }

  @GetMapping("/protocol-checklists/rip/{checklistId}/final-comments")
  public ResponseEntity<RiparianFinalComments> getRipFinalComments(@PathVariable String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getRipFinalComments(checklistId));
  }

  @PutMapping("/protocol-checklists/rip/{checklistId}/final-comments")
  public ResponseEntity<RiparianFinalComments> saveRipFinalComments(
      @PathVariable String checklistId,
      @RequestBody RiparianFinalComments comments
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveRipFinalComments(checklistId, comments));
  }

  @GetMapping("/protocol-checklists/rip/{checklistId}/field-data")
  public ResponseEntity<RiparianFieldData> getRipFieldData(@PathVariable String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getRipFieldData(checklistId));
  }

  @PutMapping("/protocol-checklists/rip/{checklistId}/field-data")
  public ResponseEntity<RiparianFieldData> saveRipFieldData(
      @PathVariable String checklistId,
      @RequestBody RiparianFieldData data
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveRipFieldData(checklistId, data));
  }

  @GetMapping("/protocol-checklists/rip/{checklistId}/other-indicators")
  public ResponseEntity<RiparianOtherIndicators> getRipOtherIndicators(@PathVariable String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getRipOtherIndicators(checklistId));
  }

  @PutMapping("/protocol-checklists/rip/{checklistId}/other-indicators")
  public ResponseEntity<RiparianOtherIndicators> saveRipOtherIndicators(
      @PathVariable String checklistId,
      @RequestBody RiparianOtherIndicators data
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveRipOtherIndicators(checklistId, data));
  }

  @GetMapping("/protocol-checklists/rip/{checklistId}/questions")
  public ResponseEntity<RiparianQuestions> getRipQuestions(@PathVariable String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getRipQuestions(checklistId));
  }

  @PutMapping("/protocol-checklists/rip/{checklistId}/questions")
  public ResponseEntity<RiparianQuestions> saveRipQuestions(
      @PathVariable String checklistId,
      @RequestBody RiparianQuestions data
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveRipQuestions(checklistId, data));
  }

  @GetMapping("/protocol-checklists/rip/{checklistId}/specific-impacts")
  public ResponseEntity<RiparianSpecificImpacts> getRipSpecificImpacts(@PathVariable String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getRipSpecificImpacts(checklistId));
  }

  @PutMapping("/protocol-checklists/rip/{checklistId}/specific-impacts")
  public ResponseEntity<RiparianSpecificImpacts> saveRipSpecificImpacts(
      @PathVariable String checklistId,
      @RequestBody RiparianSpecificImpacts data
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveRipSpecificImpacts(checklistId, data));
  }

  // --- Water (FREP screens 250-253) ---

  @GetMapping("/protocol-checklists/wtr/{checklistId}/sample-area")
  public ResponseEntity<WaterSampleArea> getWaterSampleArea(@PathVariable String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getWaterSampleArea(checklistId));
  }

  @PutMapping("/protocol-checklists/wtr/{checklistId}/sample-area")
  public ResponseEntity<WaterSampleArea> saveWaterSampleArea(
      @PathVariable String checklistId,
      @RequestBody WaterSampleArea area
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveWaterSampleArea(checklistId, area));
  }

  @GetMapping("/protocol-checklists/wtr/{checklistId}/sample-site")
  public ResponseEntity<WaterSampleSite> getWaterSampleSite(@PathVariable String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getWaterSampleSite(checklistId));
  }

  @PutMapping("/protocol-checklists/wtr/{checklistId}/sample-site")
  public ResponseEntity<WaterSampleSite> saveWaterSampleSite(
      @PathVariable String checklistId,
      @RequestBody WaterSampleSite site
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveWaterSampleSite(checklistId, site));
  }

  @GetMapping("/protocol-checklists/wtr/site/{sampleSiteId}/assessment")
  public ResponseEntity<WaterAssessment> getWaterAssessment(@PathVariable String sampleSiteId) {
    return ResponseEntity.ok(protocolChecklistService.getWaterAssessment(sampleSiteId));
  }

  @PutMapping("/protocol-checklists/wtr/site/{sampleSiteId}/assessment")
  public ResponseEntity<WaterAssessment> saveWaterAssessment(
      @PathVariable String sampleSiteId,
      @RequestBody WaterAssessment data
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveWaterAssessment(sampleSiteId, data));
  }

  @GetMapping("/protocol-checklists/wtr/site/{sampleSiteId}/range")
  public ResponseEntity<WaterRange> getWaterRange(@PathVariable String sampleSiteId) {
    return ResponseEntity.ok(protocolChecklistService.getWaterRange(sampleSiteId));
  }

  @PutMapping("/protocol-checklists/wtr/site/{sampleSiteId}/range")
  public ResponseEntity<WaterRange> saveWaterRange(
      @PathVariable String sampleSiteId,
      @RequestBody WaterRange data
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveWaterRange(sampleSiteId, data));
  }
}

package ca.bc.gov.nrs.frep.controller;

import ca.bc.gov.nrs.frep.dto.frep.BioPlot;
import ca.bc.gov.nrs.frep.dto.frep.BioPlotRow;
import ca.bc.gov.nrs.frep.dto.frep.BioStratum;
import ca.bc.gov.nrs.frep.dto.frep.BioStratumRow;
import ca.bc.gov.nrs.frep.dto.frep.StratumComputed;
import ca.bc.gov.nrs.frep.dto.frep.BiodiversityOpening;
import ca.bc.gov.nrs.frep.dto.frep.AttachmentContent;
import ca.bc.gov.nrs.frep.dto.frep.AttachmentRow;
import ca.bc.gov.nrs.frep.dto.frep.AttachmentUploadRequest;
import ca.bc.gov.nrs.frep.dto.frep.RiparianNotes;
import ca.bc.gov.nrs.frep.dto.frep.AdministrationData;
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

  @GetMapping("/protocol-checklists/bio/strata/{stratumId}/computed")
  public ResponseEntity<StratumComputed> getStratumComputed(@PathVariable String stratumId) {
    return ResponseEntity.ok(protocolChecklistService.getStratumComputed(stratumId));
  }

  @GetMapping("/protocol-checklists/bio/{checklistId}/new-stratum-computed")
  public ResponseEntity<StratumComputed> getNewStratumComputed(@PathVariable String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getNewStratumComputed(checklistId));
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

  // --- Administration / Notes / Attachments (shared across bio / rip / wat) ---

  @GetMapping("/protocol-checklists/{protocol}/{checklistId}/administration")
  public ResponseEntity<AdministrationData> getAdministration(
      @PathVariable String protocol, @PathVariable String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getAdministration(protocol, checklistId));
  }

  @PutMapping("/protocol-checklists/{protocol}/{checklistId}/administration")
  public ResponseEntity<AdministrationData> saveAdministration(
      @PathVariable String protocol,
      @PathVariable String checklistId,
      @RequestBody AdministrationData admin
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveAdministration(protocol, admin));
  }

  @PostMapping("/protocol-checklists/{protocol}/{checklistId}/administration/team")
  public ResponseEntity<AdministrationData> addTeamMember(
      @PathVariable String protocol,
      @PathVariable String checklistId,
      @RequestParam String evaluator,
      @RequestParam(defaultValue = "false") boolean teamLead
  ) {
    return ResponseEntity.ok(
        protocolChecklistService.addTeamMember(protocol, checklistId, evaluator, teamLead));
  }

  @DeleteMapping(
      "/protocol-checklists/{protocol}/{checklistId}/administration/team/{evaluatorUserid}")
  public ResponseEntity<AdministrationData> removeTeamMember(
      @PathVariable String protocol,
      @PathVariable String checklistId,
      @PathVariable String evaluatorUserid,
      @RequestParam(required = false) String revisionCount
  ) {
    return ResponseEntity.ok(protocolChecklistService.removeTeamMember(
        protocol, checklistId, evaluatorUserid, revisionCount));
  }

  @GetMapping("/protocol-checklists/{protocol}/{checklistId}/notes")
  public ResponseEntity<RiparianNotes> getNotes(
      @PathVariable String protocol, @PathVariable String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getNotes(protocol, checklistId));
  }

  @PutMapping("/protocol-checklists/{protocol}/{checklistId}/notes")
  public ResponseEntity<RiparianNotes> saveNotes(
      @PathVariable String protocol,
      @PathVariable String checklistId,
      @RequestBody RiparianNotes notes
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveNotes(protocol, checklistId, notes));
  }

  @GetMapping("/protocol-checklists/{protocol}/{checklistId}/attachments")
  public ResponseEntity<List<AttachmentRow>> getAttachments(
      @PathVariable String protocol, @PathVariable String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getAttachments(protocol, checklistId));
  }

  // Content is returned as JSON; Jackson base64-encodes the byte[] data (like the CHR photo flow).
  @GetMapping("/protocol-checklists/{protocol}/{checklistId}/attachments/{attachmentId}/content")
  public ResponseEntity<AttachmentContent> getAttachmentContent(
      @PathVariable String protocol,
      @PathVariable String checklistId,
      @PathVariable String attachmentId
  ) {
    return ResponseEntity.ok(
        protocolChecklistService.getAttachmentContent(protocol, checklistId, attachmentId));
  }

  @PostMapping("/protocol-checklists/{protocol}/{checklistId}/attachments")
  public ResponseEntity<List<AttachmentRow>> uploadAttachment(
      @PathVariable String protocol,
      @PathVariable String checklistId,
      @RequestBody AttachmentUploadRequest request
  ) {
    return ResponseEntity.ok(protocolChecklistService.saveAttachment(
        protocol, checklistId, request.fileName(), request.description(), request.contentType(),
        request.data() == null ? new byte[0] : request.data()));
  }

  @DeleteMapping("/protocol-checklists/{protocol}/{checklistId}/attachments/{attachmentId}")
  public ResponseEntity<List<AttachmentRow>> deleteAttachment(
      @PathVariable String protocol,
      @PathVariable String checklistId,
      @PathVariable String attachmentId
  ) {
    return ResponseEntity.ok(
        protocolChecklistService.deleteAttachment(protocol, checklistId, attachmentId));
  }
}

package ca.bc.gov.nrs.frep.controller;

import ca.bc.gov.nrs.frep.dto.frep.BiodiversityOpening;
import ca.bc.gov.nrs.frep.dto.frep.ProtocolChecklistResponse;
import ca.bc.gov.nrs.frep.service.frep.ProtocolChecklistService;
import ca.bc.gov.nrs.frep.service.frep.ProtocolChecklistService.ProtocolSubmitValidationException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}

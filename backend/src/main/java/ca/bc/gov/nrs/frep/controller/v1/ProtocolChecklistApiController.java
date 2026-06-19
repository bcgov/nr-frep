package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.AdministrationData;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentContent;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentUploadRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlot;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlotRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratum;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratumRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BiodiversityOpening;
import ca.bc.gov.nrs.frep.struct.v1.frep.ProtocolChecklistResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.RiparianNotes;
import ca.bc.gov.nrs.frep.struct.v1.frep.StratumComputed;
import ca.bc.gov.nrs.frep.endpoint.v1.ProtocolChecklistApiEndpoint;
import ca.bc.gov.nrs.frep.service.v1.frep.ProtocolChecklistService;
import ca.bc.gov.nrs.frep.service.v1.frep.ProtocolChecklistService.ProtocolSubmitValidationException;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read + edit/submit API for protocol checklists. Mappings declared on
 * {@link ProtocolChecklistApiEndpoint}.
 *
 * <p>Legacy equivalents: {@code frep210BIOOpeningAction} … {@code frep254WtrSummaryAction};
 * submit/unsubmit via {@code FrepTombstoneAction} / {@code FREP_TOMBSTONE}.
 */
@RestController
public class ProtocolChecklistApiController implements ProtocolChecklistApiEndpoint {

  private final ProtocolChecklistService protocolChecklistService;

  public ProtocolChecklistApiController(ProtocolChecklistService protocolChecklistService) {
    this.protocolChecklistService = protocolChecklistService;
  }

  @Override
  public ResponseEntity<ProtocolChecklistResponse> getChecklist(
      String protocolType, String checklistId) {
    return protocolChecklistService.findChecklist(protocolType, checklistId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /** Submit a checklist (all protocols). Returns 400 + message codes on validation failure. */
  @Override
  public ResponseEntity<?> submit(String protocolType, String checklistId) {
    try {
      protocolChecklistService.submit(protocolType, checklistId);
      return ResponseEntity.ok().build();
    } catch (ProtocolSubmitValidationException ex) {
      return ResponseEntity.badRequest().body(Map.of("validationErrors", ex.getMessages()));
    }
  }

  /** Revert a submitted checklist to active (all protocols). */
  @Override
  public ResponseEntity<Void> unsubmit(String protocolType, String checklistId) {
    protocolChecklistService.unsubmit(protocolType, checklistId);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<BiodiversityOpening> getBiodiversityOpening(String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getBiodiversityOpening(checklistId));
  }

  @Override
  public ResponseEntity<BiodiversityOpening> saveBiodiversityOpening(
      String checklistId, BiodiversityOpening opening) {
    return ResponseEntity.ok(protocolChecklistService.saveBiodiversityOpening(checklistId, opening));
  }

  @Override
  public ResponseEntity<List<BioStratumRow>> listBioStrata(String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.listBioStrata(checklistId));
  }

  @Override
  public ResponseEntity<BioStratum> getBioStratum(String stratumId) {
    return ResponseEntity.ok(protocolChecklistService.getBioStratum(stratumId));
  }

  @Override
  public ResponseEntity<StratumComputed> getStratumComputed(String stratumId) {
    return ResponseEntity.ok(protocolChecklistService.getStratumComputed(stratumId));
  }

  @Override
  public ResponseEntity<StratumComputed> getNewStratumComputed(String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getNewStratumComputed(checklistId));
  }

  @Override
  public ResponseEntity<BioStratum> saveBioStratum(String checklistId, BioStratum stratum) {
    return ResponseEntity.ok(
        protocolChecklistService.saveBioStratum(stratum.withChecklist(checklistId)));
  }

  @Override
  public ResponseEntity<Void> deleteBioStratum(String stratumId, String revisionCount) {
    protocolChecklistService.deleteBioStratum(stratumId, revisionCount);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<List<BioPlotRow>> listBioPlots(String stratumId) {
    return ResponseEntity.ok(protocolChecklistService.listBioPlots(stratumId));
  }

  @Override
  public ResponseEntity<BioPlot> getBioPlot(String plotId) {
    return ResponseEntity.ok(protocolChecklistService.getBioPlot(plotId));
  }

  @Override
  public ResponseEntity<BioPlot> saveBioPlot(String stratumId, BioPlot plot) {
    return ResponseEntity.ok(protocolChecklistService.saveBioPlot(plot.withStratum(stratumId)));
  }

  @Override
  public ResponseEntity<Void> deleteBioPlot(String plotId, String revisionCount) {
    protocolChecklistService.deleteBioPlot(plotId, revisionCount);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<AdministrationData> getAdministration(String protocol, String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getAdministration(protocol, checklistId));
  }

  @Override
  public ResponseEntity<AdministrationData> saveAdministration(
      String protocol, String checklistId, AdministrationData admin) {
    return ResponseEntity.ok(protocolChecklistService.saveAdministration(protocol, admin));
  }

  @Override
  public ResponseEntity<AdministrationData> addTeamMember(
      String protocol, String checklistId, String evaluator, boolean teamLead) {
    return ResponseEntity.ok(
        protocolChecklistService.addTeamMember(protocol, checklistId, evaluator, teamLead));
  }

  @Override
  public ResponseEntity<AdministrationData> removeTeamMember(
      String protocol, String checklistId, String evaluatorUserid, String revisionCount) {
    return ResponseEntity.ok(protocolChecklistService.removeTeamMember(
        protocol, checklistId, evaluatorUserid, revisionCount));
  }

  @Override
  public ResponseEntity<RiparianNotes> getNotes(String protocol, String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getNotes(protocol, checklistId));
  }

  @Override
  public ResponseEntity<RiparianNotes> saveNotes(
      String protocol, String checklistId, RiparianNotes notes) {
    return ResponseEntity.ok(protocolChecklistService.saveNotes(protocol, checklistId, notes));
  }

  @Override
  public ResponseEntity<List<AttachmentRow>> getAttachments(String protocol, String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getAttachments(protocol, checklistId));
  }

  @Override
  public ResponseEntity<AttachmentContent> getAttachmentContent(
      String protocol, String checklistId, String attachmentId) {
    return ResponseEntity.ok(
        protocolChecklistService.getAttachmentContent(protocol, checklistId, attachmentId));
  }

  @Override
  public ResponseEntity<List<AttachmentRow>> uploadAttachment(
      String protocol, String checklistId, AttachmentUploadRequest request) {
    return ResponseEntity.ok(protocolChecklistService.saveAttachment(
        protocol, checklistId, request.fileName(), request.description(), request.contentType(),
        request.data() == null ? new byte[0] : request.data()));
  }

  @Override
  public ResponseEntity<List<AttachmentRow>> deleteAttachment(
      String protocol, String checklistId, String attachmentId) {
    return ResponseEntity.ok(
        protocolChecklistService.deleteAttachment(protocol, checklistId, attachmentId));
  }
}

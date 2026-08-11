package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentContent;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentPageResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlot;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlotRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratum;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratumRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioCheckout;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioCheckoutState;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioSnapshot;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioSnapshotUpload;
import ca.bc.gov.nrs.frep.struct.v1.frep.ReleaseCheckoutRequest;
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
import org.springframework.web.multipart.MultipartFile;

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
  public ResponseEntity<BioSnapshot> getSnapshot(String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getSnapshot(checklistId));
  }

  @Override
  public ResponseEntity<BioCheckout> uploadSnapshot(String checklistId, BioSnapshotUpload upload) {
    return ResponseEntity.ok(protocolChecklistService.uploadSnapshot(checklistId, upload));
  }

  @Override
  public ResponseEntity<BioCheckoutState> getCheckoutState(
      String checklistId, String deviceCheckoutGuid) {
    return ResponseEntity.ok(
        protocolChecklistService.getCheckoutState(checklistId, deviceCheckoutGuid));
  }

  @Override
  public ResponseEntity<BioCheckout> takeOffline(String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.takeOffline(checklistId));
  }

  @Override
  public ResponseEntity<BioCheckout> releaseCheckout(String checklistId, ReleaseCheckoutRequest body) {
    return ResponseEntity.ok(protocolChecklistService.releaseCheckout(
        checklistId, body == null ? null : body.deviceCheckoutGuid()));
  }

  @Override
  public ResponseEntity<BioCheckout> activate(String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.activate(checklistId));
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
  public ResponseEntity<RiparianNotes> getNotes(String protocol, String checklistId) {
    return ResponseEntity.ok(protocolChecklistService.getNotes(protocol, checklistId));
  }

  @Override
  public ResponseEntity<RiparianNotes> saveNotes(
      String protocol, String checklistId, RiparianNotes notes) {
    return ResponseEntity.ok(protocolChecklistService.saveNotes(protocol, checklistId, notes));
  }

  @Override
  public ResponseEntity<AttachmentPageResponse> getAttachments(
      String protocol, String checklistId, int page, int size) {
    ProtocolChecklistService.AttachmentPage result =
        protocolChecklistService.getAttachments(protocol, checklistId, page, size);
    return ResponseEntity.ok(
        new AttachmentPageResponse(result.attachments(), result.totalCount()));
  }

  @Override
  public ResponseEntity<AttachmentContent> getAttachmentContent(
      String protocol, String checklistId, String attachmentId) {
    return ResponseEntity.ok(
        protocolChecklistService.getAttachmentContent(protocol, checklistId, attachmentId));
  }

  @Override
  public ResponseEntity<Void> uploadAttachment(
      String protocol, String checklistId, MultipartFile file, String description,
      String deviceCheckoutGuid) {
    protocolChecklistService.saveAttachment(
        protocol, checklistId, file, description, deviceCheckoutGuid);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> deleteAttachment(
      String protocol, String checklistId, String attachmentId, String deviceCheckoutGuid) {
    protocolChecklistService.deleteAttachment(
        protocol, checklistId, attachmentId, deviceCheckoutGuid);
    return ResponseEntity.noContent().build();
  }
}

package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
import ca.bc.gov.nrs.frep.endpoint.v1.ChrChecklistApiEndpoint;
import ca.bc.gov.nrs.frep.service.v1.chr.ChrChecklistService;
import ca.bc.gov.nrs.frep.service.v1.chr.ChrChecklistService.ChrSubmitValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cultural Heritage (CHR) checklist API. Mappings declared on {@link ChrChecklistApiEndpoint}.
 */
@RestController
public class ChrChecklistApiController implements ChrChecklistApiEndpoint {

  private final ChrChecklistService chrChecklistService;

  public ChrChecklistApiController(ChrChecklistService chrChecklistService) {
    this.chrChecklistService = chrChecklistService;
  }

  @Override
  public ResponseEntity<CheckList> getChecklist(long id) {
    return ResponseEntity.ok(chrChecklistService.getChecklist(id));
  }

  @Override
  public ResponseEntity<CheckList> saveChecklist(CheckList checklist) {
    return ResponseEntity.ok(chrChecklistService.saveChecklist(checklist));
  }

  @Override
  public ResponseEntity<CheckList> saveOpening(long id, CheckList checklist) {
    return ResponseEntity.ok(chrChecklistService.saveOpeningSection(checklist));
  }

  @Override
  public ResponseEntity<CheckList> saveBlockSummary(long id, CheckList checklist) {
    return ResponseEntity.ok(chrChecklistService.saveBlockSummarySection(checklist));
  }

  @Override
  public ResponseEntity<CheckList> saveContacts(long id, CheckList checklist) {
    return ResponseEntity.ok(chrChecklistService.saveContactsSection(checklist));
  }

  @Override
  public ResponseEntity<CheckList> saveFeatures(long id, CheckList checklist) {
    return ResponseEntity.ok(chrChecklistService.saveFeaturesSection(checklist));
  }

  @Override
  public ResponseEntity<CheckList> savePhotos(long id, CheckList checklist) {
    return ResponseEntity.ok(chrChecklistService.savePicturesSection(checklist));
  }

  @Override
  public ResponseEntity<?> submitChecklist(long id, CheckList checklist) {
    try {
      return ResponseEntity.ok(chrChecklistService.submitChecklist(id, checklist));
    } catch (ChrSubmitValidationException ex) {
      return ResponseEntity.badRequest().body(ex.getValidationErrors());
    }
  }

  @Override
  public ResponseEntity<CheckList> activateChecklist(long id) {
    return ResponseEntity.ok(chrChecklistService.activateChecklist(id));
  }

  @Override
  public ResponseEntity<CheckList> takeOffline(long id) {
    return ResponseEntity.ok(chrChecklistService.takeOffline(id));
  }

  @Override
  public ResponseEntity<CheckList> unsubmitChecklist(long id) {
    return ResponseEntity.ok(chrChecklistService.unsubmitChecklist(id));
  }
}

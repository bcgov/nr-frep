package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.AssociationsRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
import ca.bc.gov.nrs.frep.struct.v1.frep.CompositeCreateRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.CompositeUngroupRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.CompositeUpdateRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.FeatureSaveRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.FeatureSaveResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.ReleaseCheckoutRequest;
import ca.bc.gov.nrs.frep.endpoint.v1.ChrChecklistApiEndpoint;
import ca.bc.gov.nrs.frep.service.v1.chr.ChrChecklistService;
import ca.bc.gov.nrs.frep.service.v1.chr.ChrChecklistService.ChrSubmitValidationException;
import ca.bc.gov.nrs.frep.struct.v1.frep.PhotoPageResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
  public ResponseEntity<Void> addPhoto(
      long id, MultipartFile file, String description, String date, Long featureId,
      String deviceCheckoutGuid) {
    chrChecklistService.addPhoto(id, file, description, date, featureId, deviceCheckoutGuid);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<PhotoPageResponse> getPhotos(long id, int page, int size) {
    ChrChecklistService.PhotoPage result = chrChecklistService.getPhotos(id, page, size);
    return ResponseEntity.ok(new PhotoPageResponse(result.photos(), result.totalCount()));
  }

  @Override
  public ResponseEntity<byte[]> getPhotoContent(long id, long photoId) {
    ChrChecklistService.PhotoContent photo = chrChecklistService.getPhotoContent(id, photoId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
            .filename(photo.fileName() == null ? "photo" : photo.fileName(), StandardCharsets.UTF_8)
            .build().toString())
        .contentType(MediaType.parseMediaType(photo.mimeType()))
        .body(photo.content());
  }

  @Override
  public ResponseEntity<Void> deletePhoto(long id, long photoId, String deviceCheckoutGuid) {
    chrChecklistService.deletePhoto(id, photoId, deviceCheckoutGuid);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<FeatureSaveResponse> createComposite(
      long id, CompositeCreateRequest request) {
    return ResponseEntity.ok(chrChecklistService.createComposite(id, request));
  }

  @Override
  public ResponseEntity<FeatureSaveResponse> ungroupComposite(
      long id, long anchorId, CompositeUngroupRequest request) {
    return ResponseEntity.ok(chrChecklistService.ungroupComposite(id, anchorId, request));
  }

  @Override
  public ResponseEntity<FeatureSaveResponse> updateComposite(
      long id, long anchorId, CompositeUpdateRequest request) {
    return ResponseEntity.ok(chrChecklistService.updateComposite(id, anchorId, request));
  }

  @Override
  public ResponseEntity<FeatureSaveResponse> saveFeature(
      long id, long featureId, FeatureSaveRequest request) {
    return ResponseEntity.ok(chrChecklistService.saveFeature(id, featureId, request));
  }

  @Override
  public ResponseEntity<FeatureSaveResponse> saveFeatureAssociations(
      long id, long featureId, AssociationsRequest request) {
    return ResponseEntity.ok(
        chrChecklistService.saveFeatureAssociations(id, featureId, request));
  }

  @Override
  public ResponseEntity<Void> deleteFeature(long id, long featureId, String revisionCount) {
    chrChecklistService.deleteFeature(id, featureId, revisionCount);
    return ResponseEntity.noContent().build();
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
  public ResponseEntity<CheckList> releaseCheckout(long id, ReleaseCheckoutRequest body) {
    return ResponseEntity.ok(chrChecklistService.releaseCheckout(id, body.deviceCheckoutGuid()));
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

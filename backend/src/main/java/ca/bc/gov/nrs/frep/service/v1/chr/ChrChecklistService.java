package ca.bc.gov.nrs.frep.service.v1.chr;

import ca.bc.gov.nrs.frep.ChrConstants;
import ca.bc.gov.nrs.frep.exception.ConflictFoundException;
import ca.bc.gov.nrs.frep.exception.EntityNotFoundException;
import ca.bc.gov.nrs.frep.exception.FrepApiRuntimeException;
import ca.bc.gov.nrs.frep.exception.InvalidParameterException;
import ca.bc.gov.nrs.frep.service.v1.ChrChecklistPersistenceService;
import ca.bc.gov.nrs.frep.struct.v1.frep.AcceptedSite;
import ca.bc.gov.nrs.frep.struct.v1.frep.AssociationsRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
import ca.bc.gov.nrs.frep.struct.v1.frep.Feature;
import ca.bc.gov.nrs.frep.struct.v1.frep.CompositeCreateRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.CompositeUngroupRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.CompositeUpdateRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.FeatureSaveRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.FeatureSaveResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.Contact;
import ca.bc.gov.nrs.frep.struct.v1.frep.Feature;
import ca.bc.gov.nrs.frep.struct.v1.frep.OtherPlannedManagementStrategy;
import ca.bc.gov.nrs.frep.struct.v1.frep.Picture;
import ca.bc.gov.nrs.frep.struct.v1.frep.ValidationError;
import ca.bc.gov.nrs.frep.mapper.AcceptedSiteListMapper;
import ca.bc.gov.nrs.frep.mapper.CheckListMapper;
import ca.bc.gov.nrs.frep.util.ChrDateUtils;
import ca.bc.gov.nrs.frep.util.ChrStringUtils;
import ca.bc.gov.nrs.frep.validation.ChrSubmitValidationService;
import ca.bc.gov.nrs.frep.configuration.ObjectStorageProperties;
import ca.bc.gov.nrs.frep.service.v1.ObjectStorageService;
import ca.bc.gov.nrs.frep.service.v1.VirusScanner;
import ca.bc.gov.nrs.frep.entity.ChrChecklist;
import ca.bc.gov.nrs.frep.repository.v1.ChrChecklistRepository;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import ca.bc.gov.nrs.frep.service.v1.frep.FamUserDirectoryService;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChrChecklistService {

  private static final Logger log = LoggerFactory.getLogger(ChrChecklistService.class);

  // CHR photos are image-only. The derived code is stored in CHR_CHECKLIST_ATTACHMENT.MIME_TYPE_CODE
  // (VARCHAR2(3), NOT NULL, FK to MIME_TYPE_CODE), so a non-image (or an image type whose code isn't a
  // valid 3-char code, e.g. WEBP/TIFF) would fail on save with ORA-12899 / ORA-02291. Guard new photos
  // up front. Mirrors deriveMimeType's output (jpeg->jpg) against the image codes in MIME_TYPE_CODE.
  // TIF is deliberately absent. It fell through every net: browsers can't decode TIFF, so the
  // client-side downscale in Photos.tsx silently kept the full-resolution original, no thumbnail
  // could ever render, and it is excluded from server-side normalization — leaving a photo stored at
  // full size that never displays. TIF remains valid for Biodiversity *attachments*, where scanned
  // maps have a real fidelity argument; it has none for a site photo.
  private static final Set<String> ALLOWED_IMAGE_CODES = Set.of("JPG", "PNG", "GIF", "BMP");

  /** Hard cap on photo rows returned per call; matches SearchService / OpeningTargetService. */
  private static final int MAX_PAGE_SIZE = 100;

  private final ChrChecklistPersistenceService persistenceService;
  private final ChrChecklistRepository checklistRepository;
  private final ChrSubmitValidationService submitValidationService;
  private final ObjectStorageService objectStorageService;
  private final ObjectStorageProperties objectStorageProperties;
  private final LoggedUserHelper loggedUserHelper;
  private final VirusScanner virusScanner;
  private final FamUserDirectoryService famUserDirectoryService;

  public ChrChecklistService(
      ChrChecklistPersistenceService persistenceService,
      ChrChecklistRepository checklistRepository,
      ChrSubmitValidationService submitValidationService,
      ObjectStorageService objectStorageService,
      ObjectStorageProperties objectStorageProperties,
      LoggedUserHelper loggedUserHelper,
      FamUserDirectoryService famUserDirectoryService,
      VirusScanner virusScanner
  ) {
    this.persistenceService = persistenceService;
    this.checklistRepository = checklistRepository;
    this.submitValidationService = submitValidationService;
    this.objectStorageService = objectStorageService;
    this.objectStorageProperties = objectStorageProperties;
    this.loggedUserHelper = loggedUserHelper;
    this.virusScanner = virusScanner;
    this.famUserDirectoryService = famUserDirectoryService;
  }

  public CheckList getChecklist(long checklistId) {
    ChrChecklist chrChecklist = persistenceService.getAcceptedSiteForChr(checklistId);
    if (chrChecklist == null) {
      throw new EntityNotFoundException("Checklist " + checklistId + " was not found.");
    }
    return mapChecklist(chrChecklist);
  }

  @Transactional
  public CheckList saveChecklist(CheckList checklist) {
    validateSaveRequest(checklist);

    if (!ChrStringUtils.hasAValue(checklist.getChecklistID())) {
      throw new InvalidParameterException(
          "checkListId is missing in JSON body and is required to perform save");
    }

    long checklistId = Long.parseLong(checklist.getChecklistID());
    String status = checklistRepository.getChecklistStatus(checklistId);

    if (ChrConstants.FrepChecklistStatusCode.ACT.equals(status)
        && ChrConstants.FrepChecklistStatusCode.ACT.equals(checklist.getStatus())) {
      assertRevisionCount(checklist, checklistId);
      persistenceService.saveChecklist(checklist, loggedUserHelper.getLoggedUserId());
      return checklist;
    }

    if (ChrConstants.FrepChecklistStatusCode.RDO.equals(status)
        && ChrConstants.FrepChecklistStatusCode.RDO.equals(checklist.getStatus())) {
      persistenceService.uploadChecklist(checklist, loggedUserHelper.getLoggedUserId());
      return checklist;
    }

    throw new InvalidParameterException(ChrConstants.RestMessages.ERROR_CHANGE_STATUS);
  }

  @Transactional
  public CheckList saveOpeningSection(CheckList checklist) {
    return saveSection(checklist, persistenceService::saveOpeningSection, null);
  }

  @Transactional
  public CheckList saveBlockSummarySection(CheckList checklist) {
    return saveSection(checklist, persistenceService::saveBlockSummarySection, null);
  }

  @Transactional
  public CheckList saveContactsSection(CheckList checklist) {
    return saveSection(checklist, persistenceService::saveContactsSection, this::validateContacts);
  }

  @Transactional
  public CheckList saveFeaturesSection(CheckList checklist) {
    return saveSection(checklist, persistenceService::saveFeaturesSection, this::validateFeatures);
  }

  /**
   * Attach one photo. A leaf operation on the checklist, not a section save: it does not run the
   * optimistic-lock check and does not bump {@code revision_count}, so uploading a photo never
   * invalidates a client's in-flight checklist edit. It does still require the checklist to be
   * editable — the status check that {@code saveSection} would have applied is enforced here, since
   * nothing else guards these endpoints.
   *
   * <p>{@code featureId} is optional: set once, at upload, to record which feature the photo
   * documents. The persistence layer rejects a feature that belongs to another checklist.
   */
  @Transactional
  public void addPhoto(long checklistId, MultipartFile file, String description, String fileDate,
      Long featureId, String deviceCheckoutGuid) {
    assertPhotoEditable(checklistId, deviceCheckoutGuid);
    validateNewPhoto(file, description);
    byte[] content = readBytes(file);
    // Scan before anything is persisted — a hit throws VirusDetectedException (→ 422).
    virusScanner.scanOrThrow(content, file.getOriginalFilename());
    persistenceService.addPhoto(
        checklistId, file.getOriginalFilename(), description.trim(), fileDate, featureId,
        file.getContentType(), content, loggedUserHelper.getLoggedUserId());
    log.info("Added attachment :: {} ({} bytes) to CHR checklist :: {} by user :: {}",
        file.getOriginalFilename(), content.length, checklistId,
        loggedUserHelper.getLoggedUserId());
  }

  /** Remove one photo. Leaf operation, same guarantees as {@link #addPhoto}. */
  @Transactional
  public void deletePhoto(long checklistId, long photoId, String deviceCheckoutGuid) {
    assertPhotoEditable(checklistId, deviceCheckoutGuid);
    persistenceService.deletePhoto(checklistId, photoId, loggedUserHelper.getLoggedUserId());
    log.info("Deleted attachment :: {} from CHR checklist :: {} by user :: {}", photoId,
        checklistId, loggedUserHelper.getLoggedUserId());
  }

  /**
   * Photos may only be added or removed while the checklist is editable. The per-section saves get
   * this from {@code saveSection}; the photo endpoints bypass that (deliberately — they must stay
   * token-neutral), so the check is applied explicitly rather than inherited.
   *
   * <p>Two editable states, not one:
   * <ul>
   *   <li>{@code ACT} — editable online by anyone with district access.</li>
   *   <li>{@code RDO} — checked out to a device. Still editable, but <em>only</em> by the holder of
   *       that checkout, so the caller must present the matching {@code deviceCheckoutGuid} (same
   *       rule as {@link #releaseCheckout} and {@code uploadChecklist}).</li>
   * </ul>
   *
   * <p>RDO has to be allowed or offline check-in cannot work at all: photos are flushed through
   * these endpoints <em>before</em> the document save, and the {@code RDO → ACT} flip happens inside
   * that save — so at flush time the checklist is still checked out. Requiring the guid keeps the
   * guarantee that a checked-out checklist can't be altered from another device, which an ACT-or-RDO
   * check alone would have dropped. Anything else (notably {@code SUB}) is refused: a submitted
   * checklist is the genuinely immutable state this guard exists for.
   */
  private void assertPhotoEditable(long checklistId, String deviceCheckoutGuid) {
    String status = checklistRepository.getChecklistStatus(checklistId);
    if (ChrConstants.FrepChecklistStatusCode.ACT.equals(status)) {
      return;
    }
    if (ChrConstants.FrepChecklistStatusCode.RDO.equals(status)) {
      UUID serverGuid = checklistRepository.getDeviceCheckoutGuid(checklistId);
      if (serverGuid == null || !serverGuid.toString().equals(deviceCheckoutGuid)) {
        throw new InvalidParameterException(
            "This checklist is checked out on another device, so its photos can't be changed here.");
      }
      return;
    }
    throw new InvalidParameterException(
        "The checklist status is currently "
            + ChrConstants.frepChecklistStatusDescriptions().getOrDefault(status, status)
            + ", so its photos can't be changed.");
  }

  private void validateNewPhoto(MultipartFile file, String description) {
    if (file == null || file.isEmpty()) {
      throw new InvalidParameterException(
          "The selected file is empty. Choose a file with content and try again.");
    }
    if (!ChrStringUtils.hasAValue(description)) {
      throw new InvalidParameterException("A description is required for every photo.");
    }
    String mimeType = deriveMimeType(file.getContentType()).toUpperCase();
    if (!ALLOWED_IMAGE_CODES.contains(mimeType)) {
      throw new InvalidParameterException(
          "Only image files (JPG, PNG, GIF, BMP) can be uploaded as photos.");
    }
  }

  private static byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException ex) {
      throw new InvalidParameterException("Could not read the uploaded photo.");
    }
  }

  /**
   * Shared gate for per-section saves: authorize, require an id, confirm the checklist is ACT,
   * run the optimistic-lock check, run any section-specific validation, persist just that section,
   * and return the freshly re-read checklist (new revision count + any server-assigned ids). Only
   * the relevant section is validated, so e.g. saving Opening info is not blocked by a photo that
   * is missing its description.
   */
  /**
   * Create a composite over two or more features.
   *
   * <p>Validates the anchor and any newly created members the same way the features section does —
   * they are ordinary features, and the dialog can describe them in place.
   */
  @Transactional
  public FeatureSaveResponse createComposite(long checklistId, CompositeCreateRequest request) {
    if (request == null || request.anchor() == null) {
      throw new InvalidParameterException("A composite needs an anchor feature.");
    }
    String status = checklistRepository.getChecklistStatus(checklistId);
    if (!ChrConstants.FrepChecklistStatusCode.ACT.equals(status)) {
      throw new InvalidParameterException(ChrConstants.RestMessages.ERROR_CHANGE_STATUS);
    }
    assertRevisionCount(request.revisionCount(), checklistId);

    CheckList carrier = new CheckList();
    List<Feature> toValidate = new ArrayList<>();
    toValidate.add(request.anchor());
    if (request.newMembers() != null) {
      toValidate.addAll(request.newMembers());
    }
    carrier.setFeatures(toValidate);
    validateFeatures(carrier);

    List<Feature> saved;
    try {
      saved = persistenceService.createComposite(
          checklistId,
          request.anchor(),
          request.memberIds(),
          request.newMembers(),
          loggedUserHelper.getLoggedUserId());
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new FrepApiRuntimeException("Could not read back the composite after creating it.", ex);
    }
    return new FeatureSaveResponse(
        saved, Long.toString(checklistRepository.getRevisionCount(checklistId)));
  }

  /** Dissolve a composite, releasing its members and deleting the ones the caller names. */
  @Transactional
  public FeatureSaveResponse ungroupComposite(
      long checklistId, long anchorId, CompositeUngroupRequest request) {
    if (request == null) {
      throw new InvalidParameterException("An ungroup request body is required.");
    }
    String status = checklistRepository.getChecklistStatus(checklistId);
    if (!ChrConstants.FrepChecklistStatusCode.ACT.equals(status)) {
      throw new InvalidParameterException(ChrConstants.RestMessages.ERROR_CHANGE_STATUS);
    }
    assertRevisionCount(request.revisionCount(), checklistId);

    List<Feature> survivors;
    try {
      survivors = persistenceService.ungroupComposite(
          checklistId, anchorId, request.deleteMemberIds(), loggedUserHelper.getLoggedUserId());
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new FrepApiRuntimeException("Could not read back the features after ungrouping.", ex);
    }
    log.info("Ungrouped composite :: {} on CHR checklist :: {} by user :: {}", anchorId,
        checklistId, loggedUserHelper.getLoggedUserId());
    return new FeatureSaveResponse(
        survivors, Long.toString(checklistRepository.getRevisionCount(checklistId)));
  }

  /** Re-point an existing composite at a new set of members. */
  @Transactional
  public FeatureSaveResponse updateComposite(
      long checklistId, long anchorId, CompositeUpdateRequest request) {
    if (request == null) {
      throw new InvalidParameterException("A composite update request body is required.");
    }
    String status = checklistRepository.getChecklistStatus(checklistId);
    if (!ChrConstants.FrepChecklistStatusCode.ACT.equals(status)) {
      throw new InvalidParameterException(ChrConstants.RestMessages.ERROR_CHANGE_STATUS);
    }
    assertRevisionCount(request.revisionCount(), checklistId);

    if (request.newMembers() != null && !request.newMembers().isEmpty()) {
      CheckList carrier = new CheckList();
      carrier.setFeatures(new ArrayList<>(request.newMembers()));
      validateFeatures(carrier);
    }

    List<Feature> saved;
    try {
      saved = persistenceService.updateComposite(
          checklistId,
          anchorId,
          request.featureDescriptionCode(),
          request.featureInfoSourceCode(),
          request.memberIds(),
          request.newMembers(),
          loggedUserHelper.getLoggedUserId());
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new FrepApiRuntimeException("Could not read back the composite after updating it.", ex);
    }
    return new FeatureSaveResponse(
        saved, Long.toString(checklistRepository.getRevisionCount(checklistId)));
  }

  /** Add one standalone feature to a checklist. Same gate and validation as an edit. */
  @Transactional
  public FeatureSaveResponse createFeature(long checklistId, FeatureSaveRequest request) {
    if (request == null || request.feature() == null) {
      throw new InvalidParameterException("A feature is required.");
    }
    String status = checklistRepository.getChecklistStatus(checklistId);
    if (!ChrConstants.FrepChecklistStatusCode.ACT.equals(status)) {
      throw new InvalidParameterException(ChrConstants.RestMessages.ERROR_CHANGE_STATUS);
    }
    assertRevisionCount(request.revisionCount(), checklistId);

    CheckList carrier = new CheckList();
    carrier.setFeatures(new ArrayList<>(List.of(request.feature())));
    validateFeatures(carrier);

    List<Feature> saved;
    try {
      saved = persistenceService.createStandaloneFeature(
          checklistId, request.feature(), loggedUserHelper.getLoggedUserId());
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new FrepApiRuntimeException("Could not read back the feature after creating it.", ex);
    }
    return new FeatureSaveResponse(
        saved, Long.toString(checklistRepository.getRevisionCount(checklistId)));
  }

  /**
   * Save one feature's own fields.
   *
   * <p>Runs the same validation the features section runs — {@code validateFeatures} is already
   * per-feature, so it ports unchanged — plus the {@code ACT} status and revision-token gate.
   */
  @Transactional
  public FeatureSaveResponse saveFeature(
      long checklistId, long featureId, FeatureSaveRequest request) {
    if (request == null || request.feature() == null) {
      throw new InvalidParameterException("A feature is required.");
    }
    String status = checklistRepository.getChecklistStatus(checklistId);
    if (!ChrConstants.FrepChecklistStatusCode.ACT.equals(status)) {
      throw new InvalidParameterException(ChrConstants.RestMessages.ERROR_CHANGE_STATUS);
    }
    assertRevisionCount(request.revisionCount(), checklistId);

    // The section validator walks a CheckList's features; one feature is that list with one entry.
    CheckList carrier = new CheckList();
    carrier.setFeatures(new ArrayList<>(List.of(request.feature())));
    validateFeatures(carrier);

    List<Feature> saved;
    try {
      saved = persistenceService.saveFeature(
          checklistId, featureId, request.feature(), loggedUserHelper.getLoggedUserId());
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new FrepApiRuntimeException("Could not read back the feature after saving it.", ex);
    }
    return new FeatureSaveResponse(
        saved, Long.toString(checklistRepository.getRevisionCount(checklistId)));
  }

  /**
   * Replace one feature's associations, both directions.
   *
   * <p>Same gate as a section save — {@code ACT} plus a matching revision token — since an
   * association is stored state on two features, not a leaf resource.
   */
  @Transactional
  public FeatureSaveResponse saveFeatureAssociations(
      long checklistId, long featureId, AssociationsRequest request) {
    if (request == null) {
      throw new InvalidParameterException("An associations request body is required.");
    }
    String status = checklistRepository.getChecklistStatus(checklistId);
    if (!ChrConstants.FrepChecklistStatusCode.ACT.equals(status)) {
      throw new InvalidParameterException(ChrConstants.RestMessages.ERROR_CHANGE_STATUS);
    }
    assertRevisionCount(request.revisionCount(), checklistId);
    List<Feature> touched;
    try {
      touched = persistenceService.saveFeatureAssociations(
          checklistId, featureId, request.featureIds(), loggedUserHelper.getLoggedUserId());
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      // CheckListMapper.toFeature is declared `throws Exception` (legacy signature); anything it
      // raises is a mapping failure, not something the caller can act on.
      throw new FrepApiRuntimeException(
          "Could not read back the features after saving associations.", ex);
    }
    return new FeatureSaveResponse(
        touched, Long.toString(checklistRepository.getRevisionCount(checklistId)));
  }

  /**
   * Remove one feature from a checklist.
   *
   * <p>Runs the same gate as a section save — the checklist must be {@code ACT} and the caller's
   * revision token must match — because a delete is as destructive as any edit and shares the
   * checklist's optimistic lock. Photos deliberately skip both (they are leaf resources that do not
   * advance the token); a feature is not a leaf, since removing one detaches composite members and
   * drops association links on other features.
   */
  @Transactional
  public void deleteFeature(long checklistId, long featureId, String revisionCount) {
    String status = checklistRepository.getChecklistStatus(checklistId);
    if (!ChrConstants.FrepChecklistStatusCode.ACT.equals(status)) {
      throw new InvalidParameterException(ChrConstants.RestMessages.ERROR_CHANGE_STATUS);
    }
    assertRevisionCount(revisionCount, checklistId);
    persistenceService.deleteFeature(checklistId, featureId, loggedUserHelper.getLoggedUserId());
    log.info("Deleted feature :: {} from CHR checklist :: {} by user :: {}", featureId, checklistId,
        loggedUserHelper.getLoggedUserId());
  }

  private CheckList saveSection(
      CheckList checklist,
      java.util.function.BiConsumer<CheckList, String> persist,
      java.util.function.Consumer<CheckList> validate
  ) {
    if (!ChrStringUtils.hasAValue(checklist.getChecklistID())) {
      throw new InvalidParameterException(
          "checkListId is missing in JSON body and is required to perform save");
    }
    if (validate != null) {
      validate.accept(checklist);
    }
    long checklistId = Long.parseLong(checklist.getChecklistID());
    String status = checklistRepository.getChecklistStatus(checklistId);
    if (!ChrConstants.FrepChecklistStatusCode.ACT.equals(status)) {
      throw new InvalidParameterException(ChrConstants.RestMessages.ERROR_CHANGE_STATUS);
    }
    assertRevisionCount(checklist, checklistId);
    persist.accept(checklist, loggedUserHelper.getLoggedUserId());
    return getChecklist(checklistId);
  }

  @Transactional
  public CheckList submitChecklist(long checklistId, CheckList checklist) {
    if (checklist != null && !Long.toString(checklistId).equals(checklist.getChecklistID())) {
      throw new InvalidParameterException(
          "The checklist ID is not matching:" + checklist.getChecklistID());
    }

    String status = checklistRepository.getChecklistStatus(checklistId);
    if (!ChrConstants.FrepChecklistStatusCode.ACT.equals(status)) {
      throw new InvalidParameterException(
          "The checklist status is currently " + status + " when ACT is expected.");
    }

    // Photos are independent resources now, so the submitted payload's `pictures` are only a stale
    // client-side copy — a caller could omit them and skip the per-photo checks entirely. Replace
    // them with what the record actually holds before validating.
    checklist.setPictures(persistenceService.getPhotoMetadata(checklistId));
    List<ValidationError> validationErrors = submitValidationService.validateBeforeSubmit(checklist);
    if (!validationErrors.isEmpty()) {
      // Not an error: the user's data is incomplete. Logged so support can see how often submit is
      // blocked and on how many rules, without needing the user to report it.
      log.info("Submit blocked for CHR checklist :: {} by {} validation failure(s)", checklistId,
          validationErrors.size());
      throw new ChrSubmitValidationException(validationErrors);
    }

    checklist.setStatus(ChrConstants.FrepChecklistStatusCode.SUB);
    persistenceService.saveChecklist(checklist, loggedUserHelper.getLoggedUserId());
    log.info("Submitted CHR checklist :: {} by user :: {}", checklistId,
        loggedUserHelper.getLoggedUserId());
    return getChecklist(checklistId);
  }

  @Transactional
  public CheckList activateChecklist(long checklistId) {
    String status = checklistRepository.getChecklistStatus(checklistId);
    if (!ChrConstants.FrepChecklistStatusCode.RDO.equals(status)) {
      throw new InvalidParameterException(
          "Activate failed. Checklist status is " + status + " when RDO is expected.");
    }
    persistenceService.activateChecklist(checklistId, loggedUserHelper.getLoggedUserId());
    log.info("Activated CHR checklist :: {} by user :: {}", checklistId,
        loggedUserHelper.getLoggedUserId());
    return getChecklist(checklistId);
  }

  /**
   * Release an offline checkout (RDO → ACT) on behalf of the device that holds it, so the online copy
   * is editable again — used when a user removes their offline copy. Idempotent: if the checklist
   * isn't checked out, the current state is returned. Rejects when the supplied deviceCheckoutGuid
   * doesn't match the server's, i.e. the checkout belongs to another device (admin activate is the
   * fallback for that case).
   */
  @Transactional
  public CheckList releaseCheckout(long checklistId, String deviceCheckoutGuid) {
    String status = checklistRepository.getChecklistStatus(checklistId);
    if (!ChrConstants.FrepChecklistStatusCode.RDO.equals(status)) {
      return getChecklist(checklistId);
    }
    UUID serverGuid = checklistRepository.getDeviceCheckoutGuid(checklistId);
    if (serverGuid == null || !serverGuid.toString().equals(deviceCheckoutGuid)) {
      throw new InvalidParameterException(
          "Release failed. This checklist is checked out on another device.");
    }
    persistenceService.activateChecklist(checklistId, loggedUserHelper.getLoggedUserId());
    log.info("Released offline checkout of CHR checklist :: {} by user :: {}", checklistId,
        loggedUserHelper.getLoggedUserId());
    return getChecklist(checklistId);
  }

  @Transactional
  public CheckList takeOffline(long checklistId) {
    String status = checklistRepository.getChecklistStatus(checklistId);
    if (!ChrConstants.FrepChecklistStatusCode.ACT.equals(status)) {
      throw new InvalidParameterException(
          "Download failed. The checklist status is currently "
              + ChrConstants.frepChecklistStatusDescriptions().getOrDefault(status, status)
              + " when "
              + ChrConstants.frepChecklistStatusDescriptions().get(ChrConstants.FrepChecklistStatusCode.ACT)
              + " is expected."
      );
    }
    persistenceService.updateChecklistOffline(checklistId, loggedUserHelper.getLoggedUserId());
    log.info("Took CHR checklist :: {} offline for user :: {}", checklistId,
        loggedUserHelper.getLoggedUserId());
    return getChecklist(checklistId);
  }

  @Transactional
  public CheckList unsubmitChecklist(long checklistId) {
    String status = checklistRepository.getChecklistStatus(checklistId);
    if (!ChrConstants.FrepChecklistStatusCode.SUB.equals(status)) {
      throw new InvalidParameterException(
          "Unsubmit failed. Checklist status is " + status + " when SUB is expected.");
    }
    persistenceService.unsubmitChecklist(checklistId, loggedUserHelper.getLoggedUserId());
    log.info("Unsubmitted CHR checklist :: {} by user :: {}", checklistId,
        loggedUserHelper.getLoggedUserId());
    return getChecklist(checklistId);
  }

  private CheckList mapChecklist(ChrChecklist chrChecklist) {
    try {
      AcceptedSite acceptedSite = AcceptedSiteListMapper.getAcceptedSite(chrChecklist);
      CheckList checkList = CheckListMapper.getChecklist(
          chrChecklist,
          acceptedSite,
          loggedUserHelper.getLoggedUserId(),
          ChrDateUtils.getSystemDateTime(),
          "",
          objectStorageProperties.host(),
          objectStorageProperties.bucket(),
          objectStorageProperties.accessKey(),
          objectStorageProperties.secretKey()
      );
      // Opening number is the formatted mapsheet designator (e.g. "93A 026 0.0 110"), fetched via
      // THE.frep_formatted_mapsheet so it matches the Biodiversity header and Accepted Sites list —
      // the raw OPENING_NUMBER column is only the last fragment.
      checkList.setOpeningNumber(persistenceService.getFormattedOpeningNumber(
          chrChecklist.getFrepResourceValue().getFrepSelectedSite().getFrepSelectedSiteId()));
      // Resolve the evaluator (assessed-by) userid to a "Name (USERID)" display via FAM, matching the
      // Biodiversity evaluator field. The raw userid stays in assessedBy for the save round-trip and
      // the "Assign it to me" comparison; assessedByName is display-only.
      if (ChrStringUtils.hasAValue(checkList.getAssessedBy())) {
        checkList.setAssessedByName(famUserDirectoryService.resolveName(checkList.getAssessedBy())
            .orElse(checkList.getAssessedBy()));
      }
      // Photo *metadata* rides along (the mapper fills it); the bytes do not. Every photo is fetched
      // individually from the content endpoint — including by take-offline, which downloads them
      // before taking the checkout. Embedding them here meant one response held every photo at
      // ~2.33x its stored size (byte[] + base64), the last unbounded read path in the app.
      return checkList;
    } catch (Exception ex) {
      throw new FrepApiRuntimeException(ex.getMessage(), ex);
    }
  }

  /**
   * One photo's stored bytes. The object key is {@code {checklistId}-{attachmentId}.{ext}} — the same
   * key {@code addPhoto} writes with.
   */
  public PhotoContent getPhotoContent(long checklistId, long photoId) {
    Picture picture = persistenceService.getPhotoMetadata(checklistId).stream()
        .filter(p -> String.valueOf(photoId).equals(p.getId()))
        .findFirst()
        .orElseThrow(() -> new EntityNotFoundException(
            "Photo " + photoId + " was not found on checklist " + checklistId + "."));
    String mimeType = deriveMimeType(picture.getMimeTypeCode());
    String key = checklistId + "-" + photoId + "." + mimeType;
    byte[] bytes = objectStorageService.getObjectBytes(key);
    if (bytes == null || bytes.length == 0) {
      throw new EntityNotFoundException("Photo " + photoId + " has no stored content.");
    }
    return new PhotoContent(picture.getFileName(), "image/" + mimeType, bytes);
  }

  /** A photo's stored bytes, served as a binary download rather than embedded base64. */
  public record PhotoContent(String fileName, String mimeType, byte[] content) {}

  /**
   * One page of the checklist's photo metadata, newest-page-friendly ordering: creation order, with
   * the id as tiebreaker because {@code entry_timestamp} is an Oracle DATE (second precision) and
   * photos added in the same second would otherwise page non-deterministically.
   */
  public PhotoPage getPhotos(long checklistId, int page, int size) {
    int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    int safePage = Math.max(0, page);
    List<Picture> all = persistenceService.getPhotoMetadata(checklistId);
    // Offset computed as a long before narrowing: safePage is only floored at 0, not capped, so
    // `safePage * safeSize` on an int would overflow to a negative and blow up subList().
    int from = (int) Math.min((long) safePage * safeSize, all.size());
    int to = Math.min(from + safeSize, all.size());
    return new PhotoPage(all.subList(from, to), all.size());
  }

  /** A page of photo metadata plus the total, for the pager. */
  public record PhotoPage(List<Picture> photos, int totalCount) {}

  private void validateSaveRequest(CheckList checklist) {
    // Pictures are not validated here: a checklist save no longer persists them at all (they are
    // added and removed through the photo endpoints), so anything in the payload is ignored.
    validateFeatures(checklist);
  }

  /**
   * Contacts section validation. Nothing about a contact is <em>required</em> — every column this
   * section writes on {@code CHR_CHECKLIST_PARTICIPANT} / {@code CHR_CHECKLIST_PARTICIPATION} is
   * nullable, and neither legacy nor {@link ChrSubmitValidationService} has ever required a contact —
   * so this only rejects values the database or the date parser would mishandle:
   *
   * <ul>
   *   <li><b>Lengths.</b> {@code FIRST_NAME}/{@code LAST_NAME} are {@code VARCHAR2(40 BYTE)} and
   *       {@code ORGANIZATION_NAME} is {@code VARCHAR2(60 BYTE)}, and nothing between the request and
   *       the flush truncates or checks. The web form's {@code maxLength} caps typing and paste, but
   *       an offline check-in or a direct API call can exceed it — which surfaced as a raw
   *       {@code ORA-12899} at flush rather than a usable error. Measured in <em>bytes</em>, since
   *       that is what the column counts: an accented or syllabic character costs more than one.</li>
   *   <li><b>Contacted date.</b> Previously an unparseable date was caught and logged at debug in
   *       {@code ChrChecklistPersistenceService.saveContacts}, so the save reported success with the
   *       date silently dropped. Parsing is also strict here: {@code SimpleDateFormat} is lenient by
   *       default, so {@code 2026-02-31} would have rolled forward to March 3 rather than failing.</li>
   * </ul>
   *
   * <p>Only the section save runs this (same as {@link #validateFeatures}); the whole-checklist and
   * offline-upload paths keep the persistence-layer catch as their backstop.
   */
  private void validateContacts(CheckList checklist) {
    if (checklist.getContacts() == null) {
      return;
    }
    int position = 0;
    for (Contact contact : checklist.getContacts()) {
      position++;
      String who = contactLabel(contact, position);
      assertMaxBytes(contact.getFirstName(), 40, "First name", who);
      assertMaxBytes(contact.getLastName(), 40, "Last name", who);
      assertMaxBytes(contact.getOrganization(), 60, "Organization", who);
      // The date is only persisted when the contact is marked as contacted; it is discarded
      // otherwise, so validating it in that case would reject a value that never reaches the column.
      if ("true".equals(contact.getContactedInd())
          && ChrStringUtils.hasAValue(contact.getContactedDate())
          && !ChrDateUtils.isStrictDate(contact.getContactedDate())) {
        throw new InvalidParameterException(
            who + ": Contacted date must be a real calendar date in YYYY-MM-DD format.");
      }
    }
  }

  /** Name a contact in an error message, falling back to its position when it has no name yet. */
  private String contactLabel(Contact contact, int position) {
    String name = ((contact.getFirstName() == null ? "" : contact.getFirstName().trim())
        + " "
        + (contact.getLastName() == null ? "" : contact.getLastName().trim())).trim();
    return ChrStringUtils.hasAValue(name) ? "Contact " + name : "Contact " + position;
  }

  /**
   * Reject a value longer than the column allows. Byte length in the database charset, not character
   * count, because the columns are declared {@code BYTE}.
   */
  private void assertMaxBytes(String value, int maxBytes, String field, String who) {
    if (value == null) {
      return;
    }
    int length = value.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    if (length > maxBytes) {
      throw new InvalidParameterException(
          who + ": " + field + " is too long — " + length + " bytes, limit " + maxBytes
              + " (an accented character can count as more than one).");
    }
  }

  private void validateFeatures(CheckList checklist) {
    if (checklist.getFeatures() != null) {
      for (Feature feature : checklist.getFeatures()) {
        if (feature.getOtherPlannedManagementStrategy() == null) {
          continue;
        }
        for (OtherPlannedManagementStrategy strategy : feature.getOtherPlannedManagementStrategy()) {
          if (!ChrStringUtils.hasAValue(strategy.getOtherStrategy())) {
            throw new InvalidParameterException(
                "Feature ID " + feature.getFeatureLabel()
                    + " has a missing description for 'Other' management strategy.");
          }
          if (!("true".equals(strategy.getFnInd())
              || "true".equals(strategy.getAiaInd())
              || "true".equals(strategy.getSpInd()))) {
            throw new InvalidParameterException(
                "Feature ID " + feature.getFeatureLabel()
                    + " - 'Other' management strategy must have a Management Strategy defined.");
          }
        }
      }
    }
  }


  private void assertRevisionCount(String revisionCount, long checklistId) {
    CheckList carrier = new CheckList();
    carrier.setChecklistID(Long.toString(checklistId));
    carrier.setRevisionCount(revisionCount);
    assertRevisionCount(carrier, checklistId);
  }

  private void assertRevisionCount(CheckList checklist, long checklistId) {
    long revisionCount = checklistRepository.getRevisionCount(checklistId);
    if (Long.parseLong(checklist.getRevisionCount()) != revisionCount) {
      String lastUpdateUser = checklistRepository.getLastUpdatedUser(checklistId);
      throw new ConflictFoundException(
          "Checklist " + checklist.getChecklistID()
              + " has been modified by another user (" + lastUpdateUser
              + ") since you've retreived it. Any changes made have been lost and the latest version has been retrieved.");
    }
  }

  private String deriveMimeType(String mimeType) {
    String derivedValue = mimeType == null ? "" : mimeType.toLowerCase();
    if (ChrStringUtils.hasAValue(derivedValue)) {
      if (derivedValue.contains("image/")) {
        derivedValue = derivedValue.replaceFirst("image/", "");
      }
      derivedValue = "jpeg".equals(derivedValue) ? "jpg" : derivedValue;
    } else {
      derivedValue = "jpg";
    }
    return derivedValue;
  }

  public static class ChrSubmitValidationException extends RuntimeException {
    private final List<ValidationError> validationErrors;

    public ChrSubmitValidationException(List<ValidationError> validationErrors) {
      super("CHR submit validation failed");
      this.validationErrors = validationErrors;
    }

    public List<ValidationError> getValidationErrors() {
      return validationErrors;
    }
  }
}

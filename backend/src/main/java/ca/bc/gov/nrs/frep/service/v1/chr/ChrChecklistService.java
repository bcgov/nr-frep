package ca.bc.gov.nrs.frep.service.v1.chr;

import ca.bc.gov.nrs.frep.ChrConstants;
import ca.bc.gov.nrs.frep.exception.ConflictFoundException;
import ca.bc.gov.nrs.frep.exception.EntityNotFoundException;
import ca.bc.gov.nrs.frep.exception.FrepApiRuntimeException;
import ca.bc.gov.nrs.frep.exception.InvalidParameterException;
import ca.bc.gov.nrs.frep.service.v1.ChrChecklistPersistenceService;
import ca.bc.gov.nrs.frep.struct.v1.frep.AcceptedSite;
import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
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
import ca.bc.gov.nrs.frep.entity.ChrChecklist;
import ca.bc.gov.nrs.frep.repository.v1.ChrChecklistRepository;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChrChecklistService {

  private static final Logger log = LoggerFactory.getLogger(ChrChecklistService.class);

  // CHR photos are image-only. The derived code is stored in CHR_CHECKLIST_ATTACHMENT.MIME_TYPE_CODE
  // (VARCHAR2(3), NOT NULL, FK to MIME_TYPE_CODE), so a non-image (or an image type whose code isn't a
  // valid 3-char code, e.g. WEBP/TIFF) would fail on save with ORA-12899 / ORA-02291. Guard new photos
  // up front. Mirrors deriveMimeType's output (jpeg->jpg) against the image codes in MIME_TYPE_CODE.
  private static final Set<String> ALLOWED_IMAGE_CODES = Set.of("JPG", "PNG", "GIF", "BMP", "TIF");

  private final ChrChecklistPersistenceService persistenceService;
  private final ChrChecklistRepository checklistRepository;
  private final ChrSubmitValidationService submitValidationService;
  private final ObjectStorageService objectStorageService;
  private final ObjectStorageProperties objectStorageProperties;
  private final LoggedUserHelper loggedUserHelper;

  public ChrChecklistService(
      ChrChecklistPersistenceService persistenceService,
      ChrChecklistRepository checklistRepository,
      ChrSubmitValidationService submitValidationService,
      ObjectStorageService objectStorageService,
      ObjectStorageProperties objectStorageProperties,
      LoggedUserHelper loggedUserHelper
  ) {
    this.persistenceService = persistenceService;
    this.checklistRepository = checklistRepository;
    this.submitValidationService = submitValidationService;
    this.objectStorageService = objectStorageService;
    this.objectStorageProperties = objectStorageProperties;
    this.loggedUserHelper = loggedUserHelper;
  }

  public CheckList getChecklist(long checklistId) {
    assertCanReadChecklist();
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
    return saveSection(checklist, persistenceService::saveContactsSection, null);
  }

  @Transactional
  public CheckList saveFeaturesSection(CheckList checklist) {
    return saveSection(checklist, persistenceService::saveFeaturesSection, this::validateFeatures);
  }

  @Transactional
  public CheckList savePicturesSection(CheckList checklist) {
    return saveSection(checklist, persistenceService::savePicturesSection, this::validatePictures);
  }

  /**
   * Shared gate for per-section saves: authorize, require an id, confirm the checklist is ACT,
   * run the optimistic-lock check, run any section-specific validation, persist just that section,
   * and return the freshly re-read checklist (new revision count + any server-assigned ids). Only
   * the relevant section is validated, so e.g. saving Opening info is not blocked by a photo that
   * is missing its description.
   */
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

    List<ValidationError> validationErrors = submitValidationService.validateBeforeSubmit(checklist);
    if (!validationErrors.isEmpty()) {
      throw new ChrSubmitValidationException(validationErrors);
    }

    checklist.setStatus(ChrConstants.FrepChecklistStatusCode.SUB);
    persistenceService.saveChecklist(checklist, loggedUserHelper.getLoggedUserId());
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
      populatePhotoBytes(checkList);
      return checkList;
    } catch (Exception ex) {
      throw new FrepApiRuntimeException(ex.getMessage(), ex);
    }
  }

  /**
   * Embeds each stored photo's base64 bytes into the checklist response (mirrors legacy
   * {@code CheckListMapper} which fetched photos from object storage on read). The object key is
   * {@code {checklistId}-{attachmentId}.{ext}} — the same key {@code savePictures} /
   * {@code syncChecklistPhotos} write with. {@code code} is set to RAW base64 (no data-URL prefix),
   * matching the legacy contract; the UI prepends the prefix using {@code mimeTypeCode}. A missing
   * or unreadable object is logged and skipped so one bad photo never fails the whole GET.
   */
  private void populatePhotoBytes(CheckList checkList) {
    if (checkList.getPictures() == null) {
      return;
    }
    String checklistId = checkList.getChecklistID();
    for (Picture picture : checkList.getPictures()) {
      if (!ChrStringUtils.hasAValue(picture.getId())) {
        continue;
      }
      String key = checklistId + "-" + picture.getId() + "." + deriveMimeType(picture.getMimeTypeCode());
      try {
        byte[] bytes = objectStorageService.getObjectBytes(key);
        if (bytes != null && bytes.length > 0) {
          picture.setCode(Base64.getEncoder().encodeToString(bytes));
        }
      } catch (Exception ex) {
        log.warn("Could not load CHR photo {} for checklist {}: {}", key, checklistId, ex.getMessage());
      }
    }
  }

  private void validateSaveRequest(CheckList checklist) {
    validateFeatures(checklist);
    validatePictures(checklist);
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

  private void validatePictures(CheckList checklist) {
    if (checklist.getPictures() != null) {
      for (Picture picture : checklist.getPictures()) {
        // Only validate newly-added photos (no id). Existing rows already passed at creation, and
        // re-validating them here would block add/delete of any photo when a legacy row has a blank
        // description. Submit (ChrSubmitValidationService) still validates every photo's description.
        if (ChrStringUtils.hasAValue(picture.getId())) {
          continue;
        }
        if (!ChrStringUtils.hasAValue(picture.getDescription())) {
          throw new InvalidParameterException(
              "One or more photos are missing mandatory descriptions.");
        }
        if (!ALLOWED_IMAGE_CODES.contains(deriveMimeType(picture.getMimeTypeCode()).toUpperCase())) {
          throw new InvalidParameterException(
              "Only image files (JPG, PNG, GIF, BMP, TIF) can be uploaded as photos.");
        }
      }
    }
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

  private void assertCanReadChecklist() {
    // Reads are open to any authenticated user; write/activate authorization is enforced by
    // @PreAuthorize on ChrChecklistApiEndpoint (see FrepAuthorities).
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

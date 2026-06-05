package ca.bc.gov.nrs.frep.service.chr;

import ca.bc.gov.nrs.frep.ChrConstants;
import ca.bc.gov.nrs.frep.exception.ChrRestException;
import ca.bc.gov.nrs.frep.repository.chr.ChrChecklistPersistenceService;
import ca.bc.gov.nrs.frep.dto.frep.AcceptedSite;
import ca.bc.gov.nrs.frep.dto.frep.CheckList;
import ca.bc.gov.nrs.frep.dto.frep.Feature;
import ca.bc.gov.nrs.frep.dto.frep.OtherPlannedManagementStrategy;
import ca.bc.gov.nrs.frep.dto.frep.Picture;
import ca.bc.gov.nrs.frep.dto.frep.ValidationError;
import ca.bc.gov.nrs.frep.mapper.AcceptedSiteListMapper;
import ca.bc.gov.nrs.frep.mapper.CheckListMapper;
import ca.bc.gov.nrs.frep.util.ChrDateUtils;
import ca.bc.gov.nrs.frep.util.ChrStringUtils;
import ca.bc.gov.nrs.frep.validation.ChrSubmitValidationService;
import ca.bc.gov.nrs.frep.configuration.ChrObjectStorageProperties;
import ca.bc.gov.nrs.frep.service.ChrObjectStorageService;
import ca.bc.gov.nrs.frep.entity.ChrChecklist;
import ca.bc.gov.nrs.frep.repository.chr.ChrChecklistRepository;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("oracle")
public class ChrChecklistService {

  private static final Logger log = LoggerFactory.getLogger(ChrChecklistService.class);

  private final ChrChecklistPersistenceService persistenceService;
  private final ChrChecklistRepository checklistRepository;
  private final ChrSubmitValidationService submitValidationService;
  private final ChrObjectStorageService objectStorageService;
  private final ChrObjectStorageProperties objectStorageProperties;
  private final LoggedUserHelper loggedUserHelper;

  public ChrChecklistService(
      ChrChecklistPersistenceService persistenceService,
      ChrChecklistRepository checklistRepository,
      ChrSubmitValidationService submitValidationService,
      ChrObjectStorageService objectStorageService,
      ChrObjectStorageProperties objectStorageProperties,
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
      throw new ChrRestException(
          ChrConstants.RestExceptionTypes.UNEXPECTED,
          "Checklist " + checklistId + " was not found."
      );
    }
    return mapChecklist(chrChecklist);
  }

  @Transactional
  public CheckList saveChecklist(CheckList checklist) {
    assertCanWriteChecklist();
    validateSaveRequest(checklist);

    if (!ChrStringUtils.hasAValue(checklist.getChecklistID())) {
      throw new ChrRestException(
          ChrConstants.RestExceptionTypes.UNEXPECTED,
          "checkListId is missing in JSON body and is required to perform save"
      );
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

    throw new ChrRestException(
        ChrConstants.RestExceptionTypes.UNEXPECTED,
        ChrConstants.RestMessages.ERROR_CHANGE_STATUS
    );
  }

  @Transactional
  public CheckList submitChecklist(long checklistId, CheckList checklist) {
    assertCanWriteChecklist();
    if (checklist != null && !Long.toString(checklistId).equals(checklist.getChecklistID())) {
      throw new ChrRestException(
          ChrConstants.RestExceptionTypes.UNEXPECTED,
          "The checklist ID is not matching:" + checklist.getChecklistID()
      );
    }

    String status = checklistRepository.getChecklistStatus(checklistId);
    if (!ChrConstants.FrepChecklistStatusCode.ACT.equals(status)) {
      throw new ChrRestException(
          ChrConstants.RestExceptionTypes.UNEXPECTED,
          "The checklist status is currently " + status + " when ACT is expected."
      );
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
    assertAdminForActivate();
    String status = checklistRepository.getChecklistStatus(checklistId);
    if (!ChrConstants.FrepChecklistStatusCode.RDO.equals(status)) {
      throw new ChrRestException(
          ChrConstants.RestExceptionTypes.VALIDATION,
          "Activate failed. Checklist status is " + status + " when RDO is expected."
      );
    }
    persistenceService.activateChecklist(checklistId, loggedUserHelper.getLoggedUserId());
    return getChecklist(checklistId);
  }

  @Transactional
  public CheckList takeOffline(long checklistId) {
    assertCanWriteChecklist();
    String status = checklistRepository.getChecklistStatus(checklistId);
    if (!ChrConstants.FrepChecklistStatusCode.ACT.equals(status)) {
      throw new ChrRestException(
          ChrConstants.RestExceptionTypes.VALIDATION,
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
    assertCanWriteChecklist();
    checklistRepository.throwIfUnsubmitError(Long.toString(checklistId), loggedUserHelper.getLoggedUserId());
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
      throw new ChrRestException(ChrConstants.RestExceptionTypes.UNEXPECTED, ex.getMessage());
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
    if (checklist.getFeatures() != null) {
      for (Feature feature : checklist.getFeatures()) {
        if (feature.getOtherPlannedManagementStrategy() == null) {
          continue;
        }
        for (OtherPlannedManagementStrategy strategy : feature.getOtherPlannedManagementStrategy()) {
          if (!ChrStringUtils.hasAValue(strategy.getOtherStrategy())) {
            throw new ChrRestException(
                ChrConstants.RestExceptionTypes.VALIDATION,
                "Feature ID " + feature.getFeatureLabel()
                    + " has a missing description for 'Other' management strategy."
            );
          }
          if (!("true".equals(strategy.getFnInd())
              || "true".equals(strategy.getAiaInd())
              || "true".equals(strategy.getSpInd()))) {
            throw new ChrRestException(
                ChrConstants.RestExceptionTypes.VALIDATION,
                "Feature ID " + feature.getFeatureLabel()
                    + " - 'Other' management strategy must have a Management Strategy defined."
            );
          }
        }
      }
    }
    if (checklist.getPictures() != null) {
      for (Picture picture : checklist.getPictures()) {
        if (!ChrStringUtils.hasAValue(picture.getDescription())) {
          throw new ChrRestException(
              ChrConstants.RestExceptionTypes.VALIDATION,
              "One or more photos are missing mandatory descriptions."
          );
        }
      }
    }
  }

  private void assertRevisionCount(CheckList checklist, long checklistId) {
    long revisionCount = checklistRepository.getRevisionCount(checklistId);
    if (Long.parseLong(checklist.getRevisionCount()) != revisionCount) {
      String lastUpdateUser = checklistRepository.getLastUpdatedUser(checklistId);
      throw new ChrRestException(
          ChrConstants.RestExceptionTypes.UNEXPECTED,
          ChrConstants.RestExceptionSubType.REVISION_CONTROL,
          "Checklist " + checklist.getChecklistID()
              + " has been modified by another user (" + lastUpdateUser
              + ") since you've retreived it. Any changes made have been lost and the latest version has been retrieved."
      );
    }
  }

  private void assertCanReadChecklist() {
    // GET is authorized by ApiAuthorizationCustomizer for all FREP roles.
  }

  private void assertCanWriteChecklist() {
    if (loggedUserHelper.isViewOnly()) {
      throw new ChrRestException(
          ChrConstants.RestExceptionTypes.AUTHORIZATION,
          ChrConstants.RestMessages.ERROR_AUTHORIZATION
      );
    }
    if (!loggedUserHelper.canWrite()) {
      throw new ChrRestException(
          ChrConstants.RestExceptionTypes.AUTHORIZATION,
          ChrConstants.RestMessages.ERROR_AUTHORIZATION
      );
    }
  }

  private void assertAdminForActivate() {
    if (loggedUserHelper.isUpdate()) {
      throw new ChrRestException(
          ChrConstants.RestExceptionTypes.AUTHORIZATION,
          ChrConstants.RestMessages.ERROR_AUTHORIZATION
      );
    }
    if (!loggedUserHelper.isSysAdmin()) {
      throw new ChrRestException(
          ChrConstants.RestExceptionTypes.AUTHORIZATION,
          ChrConstants.RestMessages.ERROR_AUTHORIZATION
      );
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

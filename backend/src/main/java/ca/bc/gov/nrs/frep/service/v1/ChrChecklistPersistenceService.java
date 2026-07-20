package ca.bc.gov.nrs.frep.service.v1;

import ca.bc.gov.nrs.frep.ChrConstants;
import ca.bc.gov.nrs.frep.service.v1.ObjectStorageService;
import ca.bc.gov.nrs.frep.service.v1.ObjectStorageService.PhotoUpload;
import ca.bc.gov.nrs.frep.entity.ChrAssociatedFeatureXref;
import ca.bc.gov.nrs.frep.entity.ChrAssociatedFeatureXrefId;
import ca.bc.gov.nrs.frep.entity.ChrChecklist;
import ca.bc.gov.nrs.frep.entity.ChrChecklistAttachment;
import ca.bc.gov.nrs.frep.entity.ChrChecklistParticipant;
import ca.bc.gov.nrs.frep.entity.ChrChecklistParticipation;
import ca.bc.gov.nrs.frep.entity.ChrChecklistParticipationId;
import ca.bc.gov.nrs.frep.entity.ChrFeatWindthrTreatXref;
import ca.bc.gov.nrs.frep.entity.ChrFeatWindthrTreatXrefId;
import ca.bc.gov.nrs.frep.entity.ChrFeatureAgeXref;
import ca.bc.gov.nrs.frep.entity.ChrFeatureAgeXrefId;
import ca.bc.gov.nrs.frep.entity.ChrFeatureClassCode;
import ca.bc.gov.nrs.frep.entity.ChrFeatureDamageAgentXref;
import ca.bc.gov.nrs.frep.entity.ChrFeatureDamageAgentXrefId;
import ca.bc.gov.nrs.frep.entity.ChrFeatureDetail;
import ca.bc.gov.nrs.frep.entity.ChrFeatureIdentity;
import ca.bc.gov.nrs.frep.entity.ChrFeatureInfoSourceCode;
import ca.bc.gov.nrs.frep.entity.ChrFeatureInfoSourceXref;
import ca.bc.gov.nrs.frep.entity.ChrFeatureInfoSourceXrefId;
import ca.bc.gov.nrs.frep.entity.ChrFeatureLocationDetail;
import ca.bc.gov.nrs.frep.entity.ChrFeatureLocationDetailId;
import ca.bc.gov.nrs.frep.entity.ChrFeatureTypeXref;
import ca.bc.gov.nrs.frep.entity.ChrFeatureTypeXrefId;
import ca.bc.gov.nrs.frep.entity.ChrMgmtStrategyPlanned;
import ca.bc.gov.nrs.frep.entity.ChrMgmtStrategySourceCode;
import ca.bc.gov.nrs.frep.entity.ChrMgmtStrategyTypeCode;
import ca.bc.gov.nrs.frep.entity.ChrMgmtStrategyUsed;
import ca.bc.gov.nrs.frep.entity.ChrReserveTypeCode;
import ca.bc.gov.nrs.frep.entity.ChrSiteEvaluationCode;
import ca.bc.gov.nrs.frep.entity.FrepChecklistAnswerCode;
import ca.bc.gov.nrs.frep.entity.FrepChecklistStatusCode;
import ca.bc.gov.nrs.frep.entity.FrepMrvaRatingCode;
import ca.bc.gov.nrs.frep.entity.FrepResourceValueStatCode;
import ca.bc.gov.nrs.frep.exception.EntityNotFoundException;
import ca.bc.gov.nrs.frep.exception.InvalidParameterException;
import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
import ca.bc.gov.nrs.frep.struct.v1.frep.Contact;
import ca.bc.gov.nrs.frep.struct.v1.frep.Feature;
import ca.bc.gov.nrs.frep.struct.v1.frep.OtherPlannedManagementStrategy;
import ca.bc.gov.nrs.frep.struct.v1.frep.Picture;
import ca.bc.gov.nrs.frep.util.ChrDateUtils;
import ca.bc.gov.nrs.frep.util.ChrStringUtils;
import ca.bc.gov.nrs.frep.util.UuidUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring adaptation of legacy {@code RestDataManager} CHR persistence.
 */
@Service
@Transactional
public class ChrChecklistPersistenceService {

  private static final Logger log = LoggerFactory.getLogger(ChrChecklistPersistenceService.class);

  @PersistenceContext
  private EntityManager entityManager;

  private final ObjectStorageService objectStorageService;

  public ChrChecklistPersistenceService(ObjectStorageService objectStorageService) {
    this.objectStorageService = objectStorageService;
  }

  public ChrChecklist getAcceptedSiteForChr(long checklistId) {
    return getAcceptedSiteForChr(checklistId, ChrConstants.CHR_PROTOCOL_TYPE);
  }

  @SuppressWarnings("unchecked")
  public ChrChecklist getAcceptedSiteForChr(long checklistId, String protocolTypeCode) {
    // SELECT chr.* (not *): FREP_SELECTED_SITE and FREP_RESOURCE_VALUE also carry an
    // ENTRY_TIMESTAMP column, and a bare * yields duplicate aliases that break Hibernate's
    // entity auto-discovery (NonUniqueDiscoveredSqlAliasException). The result maps only to
    // ChrChecklist, so the join tables are filters, not projected columns.
    List<ChrChecklist> results = entityManager.createNativeQuery(
            "SELECT chr.* FROM THE.CHR_CHECKLIST chr, THE.FREP_SELECTED_SITE fss, THE.frep_resource_value frv "
                + "WHERE fss.frep_selected_site_id = frv.frep_selected_site_id "
                + "AND chr.frep_resource_value_id = frv.frep_resource_value_id "
                + "AND frv.frep_resource_value_type_code = :protocolTypeCode "
                + "AND chr.chr_checklist_id = :checklistId",
            ChrChecklist.class)
        .setParameter("protocolTypeCode", protocolTypeCode)
        .setParameter("checklistId", checklistId)
        .getResultList();
    return results.isEmpty() ? null : results.get(0);
  }

  public ChrChecklist getChecklist(Long checklistId) {
    return entityManager.find(ChrChecklist.class, checklistId);
  }

  /**
   * The formatted mapsheet opening designator (e.g. "93A 026 0.0 110") for a selected site. Uses the
   * same {@code THE.frep_formatted_mapsheet} function the Accepted Sites list and the Biodiversity
   * header use, so the CHR header shows the identical value — not the raw {@code OPENING_NUMBER}
   * fragment. Returns null when the site has no mapsheet/opening data.
   */
  public String getFormattedOpeningNumber(long frepSelectedSiteId) {
    List<?> rows = entityManager.createNativeQuery(
            "SELECT THE.frep_formatted_mapsheet(fss.mapsheet_grid, fss.mapsheet_letter, "
                + "fss.mapsheet_square, fss.mapsheet_quad, fss.mapsheet_sub_quad, fss.opening_number) "
                + "FROM THE.frep_selected_site fss WHERE fss.frep_selected_site_id = :selectedSiteId")
        .setParameter("selectedSiteId", frepSelectedSiteId)
        .getResultList();
    return rows.isEmpty() || rows.get(0) == null ? null : rows.get(0).toString();
  }

  public ChrChecklist updateChecklistOffline(Long checklistId, String userId) {
    ChrChecklist chrChecklist = entityManager.find(ChrChecklist.class, checklistId);
    FrepChecklistStatusCode status = entityManager.find(
        FrepChecklistStatusCode.class,
        ChrConstants.FrepChecklistStatusCode.RDO
    );
    chrChecklist.setFrepChecklistStatusCode(status);
    chrChecklist.setDeviceCheckoutGuid(UuidUtils.asBytes(UUID.randomUUID()));
    // Whoever takes a checklist offline to assess it becomes the assessor — default "Assessed by" to
    // the checking-out user when it's still unset (this persists server-side immediately).
    if (!ChrStringUtils.hasAValue(chrChecklist.getAssessedBy())) {
      chrChecklist.setAssessedBy(userId);
    }
    chrChecklist.setUpdateUserid(userId);
    chrChecklist.setUpdateTimestamp(new Date());
    return chrChecklist;
  }

  public ChrChecklist activateChecklist(Long checklistId, String userId) {
    ChrChecklist chrChecklist = entityManager.find(ChrChecklist.class, checklistId);
    FrepChecklistStatusCode status = entityManager.find(
        FrepChecklistStatusCode.class,
        ChrConstants.FrepChecklistStatusCode.ACT
    );
    chrChecklist.setFrepChecklistStatusCode(status);
    chrChecklist.setDeviceCheckoutGuid(null);
    chrChecklist.setUpdateUserid(userId);
    chrChecklist.setUpdateTimestamp(new Date());
    return chrChecklist;
  }

  /** Unsubmit a submitted checklist: SUB → ACT. Mirrors the JPA lifecycle used by activate/offline
   *  rather than the FREP_TOMBSTONE.UNSUBMIT proc, whose CASE has no CHR branch (ORA-06592). */
  public ChrChecklist unsubmitChecklist(Long checklistId, String userId) {
    ChrChecklist chrChecklist = entityManager.find(ChrChecklist.class, checklistId);
    FrepChecklistStatusCode status = entityManager.find(
        FrepChecklistStatusCode.class,
        ChrConstants.FrepChecklistStatusCode.ACT
    );
    chrChecklist.setFrepChecklistStatusCode(status);
    chrChecklist.setUpdateUserid(userId);
    chrChecklist.setUpdateTimestamp(new Date());
    return chrChecklist;
  }

  public void uploadChecklist(CheckList resource, String userId) {
    String guidSavedInDb = getDeviceCheckoutGuid(Long.parseLong(resource.getChecklistID()));
    if (!resource.getDeviceCheckoutGuid().equals(guidSavedInDb)) {
      throw new InvalidParameterException(
          "Upload failed: The resource deviceCheckoutGuid doesn't match the saved value for the checklist in the database.");
    }
    resource.setDeviceCheckoutGuid(null);
    resource.setStatus(ChrConstants.FrepChecklistStatusCode.ACT);
    saveChecklist(resource, userId);
  }

  public void saveChecklist(CheckList resource, String userId) {
    ChrChecklist chrChecklist = loadChecklistForSave(resource);

    chrChecklist.setDeviceCheckoutGuid(null);
    applyOpeningFields(chrChecklist, resource);

    FrepChecklistStatusCode status = entityManager.find(
        FrepChecklistStatusCode.class,
        resource.getStatus()
    );
    chrChecklist.setFrepChecklistStatusCode(status);

    applyBlockSummaryFields(chrChecklist, resource);
    stampChecklistUpdate(chrChecklist, userId);

    saveContacts(chrChecklist, resource, userId);
    saveFeatures(chrChecklist, resource, userId);
    savePictures(chrChecklist, resource, userId);

    entityManager.flush();
    resource.setRevisionCount(Long.toString(chrChecklist.getRevisionCount()));
  }

  /**
   * Per-section saves (Opening info, Block summary, Contacts, Features, Attachments) — each loads
   * the checklist row, applies only its own section, stamps the parent (bumping the shared
   * {@code revision_count} optimistic-lock token), flushes, and echoes the new revision count.
   * Unlike {@link #saveChecklist} these never touch the other sections, so e.g. saving Opening info
   * does not re-sync photos to object storage. They mirror the Biodiversity per-section save model.
   */
  public void saveOpeningSection(CheckList resource, String userId) {
    ChrChecklist chrChecklist = loadChecklistForSave(resource);
    chrChecklist.setDeviceCheckoutGuid(null);
    applyOpeningFields(chrChecklist, resource);
    // "Assessed by" is set ONLY when the saving user explicitly assigns it to themselves via the
    // "Assign it to me" action (the payload sends the current user's id). It is never auto-defaulted
    // on first save, and never set to anyone but the saving user — so it stays unset until assigned.
    if (userId.equals(resource.getAssessedBy())) {
      chrChecklist.setAssessedBy(userId);
    }
    finishSectionSave(chrChecklist, resource, userId);
  }

  public void saveBlockSummarySection(CheckList resource, String userId) {
    ChrChecklist chrChecklist = loadChecklistForSave(resource);
    chrChecklist.setDeviceCheckoutGuid(null);
    applyBlockSummaryFields(chrChecklist, resource);
    finishSectionSave(chrChecklist, resource, userId);
  }

  public void saveContactsSection(CheckList resource, String userId) {
    ChrChecklist chrChecklist = loadChecklistForSave(resource);
    chrChecklist.setDeviceCheckoutGuid(null);
    saveContacts(chrChecklist, resource, userId);
    finishSectionSave(chrChecklist, resource, userId);
  }

  public void saveFeaturesSection(CheckList resource, String userId) {
    ChrChecklist chrChecklist = loadChecklistForSave(resource);
    chrChecklist.setDeviceCheckoutGuid(null);
    saveFeatures(chrChecklist, resource, userId);
    finishSectionSave(chrChecklist, resource, userId);
    // The feature writes rewrote child xrefs by delete-then-reinsert and the new rows carry only
    // their embedded ids (the code associations the mapper reads are insertable=false). Drop the
    // persistence context so the response re-read (getChecklist) reloads the whole graph fresh from
    // the now-flushed database state, with every code association populated.
    entityManager.clear();
  }

  public void savePicturesSection(CheckList resource, String userId) {
    ChrChecklist chrChecklist = loadChecklistForSave(resource);
    chrChecklist.setDeviceCheckoutGuid(null);
    savePictures(chrChecklist, resource, userId);
    finishSectionSave(chrChecklist, resource, userId);
  }

  private ChrChecklist loadChecklistForSave(CheckList resource) {
    ChrChecklist chrChecklist = entityManager.find(ChrChecklist.class, Long.parseLong(resource.getChecklistID()));
    if (chrChecklist == null) {
      throw new EntityNotFoundException("Checklist " + resource.getChecklistID() + " was not found.");
    }
    return chrChecklist;
  }

  private void finishSectionSave(ChrChecklist chrChecklist, CheckList resource, String userId) {
    stampChecklistUpdate(chrChecklist, userId);
    entityManager.flush();
    resource.setRevisionCount(Long.toString(chrChecklist.getRevisionCount()));
  }

  private void stampChecklistUpdate(ChrChecklist chrChecklist, String userId) {
    chrChecklist.setUpdateUserid(userId);
    chrChecklist.setUpdateTimestamp(new Date());
  }

  private void applyOpeningFields(ChrChecklist chrChecklist, CheckList resource) {
    try {
      chrChecklist.setEvaluationDate(ChrDateUtils.getDate(resource.getEvaluationDate()));
    } catch (Exception ex) {
      throw new InvalidParameterException(
          "Invalid evaluation date: " + resource.getEvaluationDate());
    }
    chrChecklist.setFirstNationsPlacename(resource.getFirstNationName());
    chrChecklist.setLocationDescription(resource.getGeneralLocation());
    updateTargetedStatus(chrChecklist, resource.getTargeted());
  }

  private void applyBlockSummaryFields(ChrChecklist chrChecklist, CheckList resource) {
    chrChecklist.setLimitingOperatnlFactorsInd(
        ChrStringUtils.booleanToIndictor(resource.getQ8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock()));
    chrChecklist.setLimitingOperatnlFactorsDesc(resource.getQ8Comments());
    chrChecklist.setEffectiveStratsUsedInd(
        ChrStringUtils.booleanToIndictor(resource.getQ9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues()));
    chrChecklist.setEffectiveStratsUsedDesc(resource.getQ9Comments());
    chrChecklist.setAlternateStratsAvailInd(
        ChrStringUtils.booleanToIndictor(resource.getQ10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock()));
    chrChecklist.setAlternateStratsAvailDesc(resource.getQ10Comments());

    FrepMrvaRatingCode mrvaRatingCode = null;
    if (ChrStringUtils.hasAValue(resource.getMrvaRatingCode())
        && !"NUL".equals(resource.getMrvaRatingCode())) {
      mrvaRatingCode = entityManager.find(FrepMrvaRatingCode.class, resource.getMrvaRatingCode());
    }
    chrChecklist.setFrepMrvaRatingCode(mrvaRatingCode);

    if (ChrStringUtils.hasAValue(resource.getRating())) {
      ChrSiteEvaluationCode siteEvaluationCode = entityManager.find(
          ChrSiteEvaluationCode.class,
          resource.getRating()
      );
      chrChecklist.setChrSiteEvaluationCode(siteEvaluationCode);
    }
    chrChecklist.setEvaluationRatingRationale(resource.getRatingRationale());
    chrChecklist.setBlockComments(resource.getCommentaires());
  }

  public String getDeviceCheckoutGuid(Long checklistId) {
    byte[] bytes = (byte[]) entityManager.createNativeQuery(
            "SELECT device_checkout_guid FROM the.chr_checklist WHERE chr_checklist_id = :checklistId")
        .setParameter("checklistId", checklistId)
        .getSingleResult();
    if (bytes == null) {
      return null;
    }
    return UuidUtils.asUuid(bytes).toString();
  }

  private void updateTargetedStatus(ChrChecklist chrChecklist, String targeted) {
    String current = chrChecklist.getFrepResourceValue().getFrepResourceValueStatCode()
        .getFrepResourceValueStatCode();
    if (!ChrConstants.FrepResourceValueStatusCode.TAR.equals(current) && "true".equals(targeted)) {
      chrChecklist.getFrepResourceValue().setFrepResourceValueStatCode(
          getResourceValueStatCode(ChrConstants.FrepResourceValueStatusCode.TAR));
    } else if (ChrConstants.FrepResourceValueStatusCode.TAR.equals(current) && "false".equals(targeted)) {
      chrChecklist.getFrepResourceValue().setFrepResourceValueStatCode(
          getResourceValueStatCode(ChrConstants.FrepResourceValueStatusCode.ACC));
    }
  }

  private FrepResourceValueStatCode getResourceValueStatCode(String statusCode) {
    return entityManager.createQuery(
            "SELECT c FROM FrepResourceValueStatCode c WHERE c.frepResourceValueStatCode = :code",
            FrepResourceValueStatCode.class)
        .setParameter("code", statusCode)
        .getSingleResult();
  }

  private void saveContacts(ChrChecklist chrChecklist, CheckList resource, String userId) {
    if (resource.getContacts() == null) {
      return;
    }

    // Participant ids still present in the payload (newly-added contacts have no id yet).
    Set<Long> retainedParticipantIds = new HashSet<>();
    for (Contact contact : resource.getContacts()) {
      if (ChrStringUtils.hasAValue(contact.getId())) {
        retainedParticipantIds.add(Long.parseLong(contact.getId()));
      }
    }

    // Remove participations (and their participant) no longer in the payload. The checklist's
    // chrChecklistParticipations is an EAGER inverse Set, so we must also drop the deleted element
    // from it — otherwise the managed checklist still references a removed row at flush time and
    // Hibernate raises a TransientObjectException. Flush the deletes before re-persisting, matching
    // saveFeatures.
    List<ChrChecklistParticipation> existing = entityManager.createQuery(
            "SELECT p FROM ChrChecklistParticipation p WHERE p.id.chrChecklistId = :cid",
            ChrChecklistParticipation.class)
        .setParameter("cid", chrChecklist.getChrChecklistId())
        .getResultList();
    for (ChrChecklistParticipation participation : existing) {
      if (!retainedParticipantIds.contains(participation.getId().getChrChecklistParticipantId())) {
        chrChecklist.getChrChecklistParticipations().remove(participation);
        entityManager.remove(participation);
        entityManager.remove(participation.getChrChecklistParticipant());
      }
    }
    entityManager.flush();

    for (Contact contact : resource.getContacts()) {
      ChrChecklistParticipant participant;
      if (ChrStringUtils.hasAValue(contact.getId())) {
        participant = entityManager.find(ChrChecklistParticipant.class, Long.parseLong(contact.getId()));
        participant.setUpdateTimestamp(new Date());
        participant.setUpdateUserid(userId);
      } else {
        participant = new ChrChecklistParticipant();
        participant.setEntryTimestamp(new Date());
        participant.setUpdateTimestamp(new Date());
        participant.setEntryUserid(userId);
        participant.setUpdateUserid(userId);
      }
      participant.setFirstName(contact.getFirstName());
      participant.setLastName(contact.getLastName());
      participant.setOrganizationName(contact.getOrganization());
      entityManager.persist(participant);
      contact.setId(participant.getChrChecklistParticipantId().toString());

      ChrChecklistParticipationId participationId = new ChrChecklistParticipationId(
          chrChecklist.getChrChecklistId(),
          participant.getChrChecklistParticipantId()
      );
      ChrChecklistParticipation participation = entityManager.find(
          ChrChecklistParticipation.class,
          participationId
      );
      boolean newParticipation = participation == null;
      if (newParticipation) {
        participation = new ChrChecklistParticipation();
        participation.setId(participationId);
        participation.setEntryTimestamp(new Date());
        participation.setEntryUserid(userId);
        // CHR_CHECKLIST_PARTICIPANT_ID is read-only (derived from the embedded id); set the
        // association so the same-session re-read (CheckListMapper) can resolve the participant.
        participation.setChrChecklistParticipant(participant);
      }
      participation.setUpdateTimestamp(new Date());
      participation.setUpdateUserid(userId);
      participation.setChrParticipantRoleCode(contact.getRoleCode());
      participation.setContactedInd(ChrStringUtils.booleanToIndictor(contact.getContactedInd()));
      if ("true".equals(contact.getContactedInd())) {
        try {
          participation.setContactedDate(ChrDateUtils.getDate(contact.getContactedDate()));
        } catch (Exception ex) {
          log.debug("Invalid contact date for contact {}", contact.getId(), ex);
        }
      } else {
        participation.setContactedDate(null);
      }
      participation.setAttendingOnSiteInd(ChrStringUtils.booleanToIndictor(contact.getAttendingOnSiteInd()));
      entityManager.persist(participation);
      if (newParticipation) {
        chrChecklist.getChrChecklistParticipations().add(participation);
      }
    }
  }

  private void savePictures(ChrChecklist chrChecklist, CheckList resource, String userId) {
    if (resource.getPictures() == null) {
      resource.setPictures(new ArrayList<>());
    }

    // Remove attachments no longer in the payload. Iterate a copy and also drop the row from the
    // checklist's eager chrChecklistAttachments set, otherwise the managed checklist still
    // references the removed row at flush time and Hibernate raises a TransientObjectException.
    for (Object attachmentObj : new ArrayList<>(chrChecklist.getChrChecklistAttachments())) {
      ChrChecklistAttachment attachment = (ChrChecklistAttachment) attachmentObj;
      boolean exists = resource.getPictures().stream()
          .anyMatch(p -> ChrStringUtils.hasAValue(p.getId())
              && Long.parseLong(p.getId()) == attachment.getChrchecklistAttachmentId());
      if (!exists) {
        chrChecklist.getChrChecklistAttachments().remove(attachment);
        entityManager.remove(attachment);
      }
    }

    List<PhotoUpload> uploads = new ArrayList<>();
    for (Picture picture : resource.getPictures()) {
      ChrChecklistAttachment attachment;
      boolean newAttachment = !ChrStringUtils.hasAValue(picture.getId());
      if (!newAttachment) {
        attachment = entityManager.find(ChrChecklistAttachment.class, Long.parseLong(picture.getId()));
      } else {
        attachment = new ChrChecklistAttachment();
        attachment.setChrChecklist(chrChecklist);
        attachment.setEntryTimestamp(new Date());
        attachment.setEntryUserid(userId);
      }

      if (ChrStringUtils.hasAValue(picture.getMimeTypeCode())
          && !picture.getMimeTypeCode().contains("image/")) {
        picture.setMimeTypeCode("image/" + picture.getMimeTypeCode().toLowerCase());
      }

      String mimeType = deriveMimeType(picture.getMimeTypeCode()).toUpperCase();
      attachment.setMimeTypeCode(mimeType);
      attachment.setDescription(picture.getDescription());
      String fileNameWithoutExt = FilenameUtils.removeExtension(picture.getFileName());
      attachment.setFileName(fileNameWithoutExt + "." + mimeType);
      attachment.setFileDate(parseDate(picture.getDate()));
      attachment.setUpdateTimestamp(new Date());
      attachment.setUpdateUserid(userId);
      entityManager.persist(attachment);
      if (newAttachment) {
        chrChecklist.getChrChecklistAttachments().add(attachment);
      }
      picture.setId(attachment.getChrchecklistAttachmentId().toString());

      if (ChrStringUtils.hasAValue(picture.getCode())) {
        byte[] decoded = Base64.getDecoder().decode(picture.getCode());
        uploads.add(new PhotoUpload(
            picture.getId() + "." + deriveMimeType(picture.getMimeTypeCode()),
            picture.getMimeTypeCode(),
            decoded
        ));
      }
    }

    objectStorageService.syncChecklistPhotos(resource.getChecklistID(), uploads);
  }

  /**
   * Persists the CHR features for a checklist, mirroring the legacy
   * {@code RestDataManager.saveChecklist} feature blocks. Two passes are required: pass one
   * upserts each feature identity/detail and rebuilds its cross-reference collections; pass two
   * rebuilds associated-feature links once every identity (and its generated id) exists.
   */
  private void saveFeatures(ChrChecklist chrChecklist, CheckList resource, String userId) {
    if (resource.getFeatures() == null) {
      resource.setFeatures(new ArrayList<>());
    }
    long checklistId = chrChecklist.getChrChecklistId();

    // Remove features no longer present in the payload. Also drop them from the checklist's eager
    // chrFeatureIdentities set so the same-session re-read (CheckListMapper) doesn't show a removed
    // (or, for new rows below, a missing) feature.
    List<ChrFeatureIdentity> existingIdentities = entityManager.createQuery(
            "SELECT fi FROM ChrFeatureIdentity fi WHERE fi.chrChecklist.chrChecklistId = :cid",
            ChrFeatureIdentity.class)
        .setParameter("cid", checklistId)
        .getResultList();
    for (ChrFeatureIdentity identity : existingIdentities) {
      boolean stillPresent = resource.getFeatures().stream()
          .anyMatch(f -> ChrStringUtils.hasAValue(f.getId())
              && identity.getChrFeatureId().equals(Long.parseLong(f.getId())));
      if (!stillPresent) {
        chrChecklist.getChrFeatureIdentities().remove(identity);
        deleteFeature(identity);
      }
    }
    entityManager.flush();

    // Pass 1: identity, info source, detail, and all per-feature cross-reference collections.
    for (Feature feature : resource.getFeatures()) {
      ChrFeatureIdentity identity;
      boolean newIdentity = !ChrStringUtils.hasAValue(feature.getId());
      if (!newIdentity) {
        identity = entityManager.find(ChrFeatureIdentity.class, Long.parseLong(feature.getId()));
        identity.setUpdateTimestamp(new Date());
        identity.setUpdateUserid(userId);
      } else {
        identity = new ChrFeatureIdentity();
        identity.setEntryTimestamp(new Date());
        identity.setUpdateTimestamp(new Date());
        identity.setEntryUserid(userId);
        identity.setUpdateUserid(userId);
      }
      if (ChrStringUtils.hasAValue(feature.getFeatureDescriptionCode())) {
        identity.setChrFeatureClassCode(
            entityManager.find(ChrFeatureClassCode.class, feature.getFeatureDescriptionCode()));
      }
      identity.setComments(feature.getFeatureComment());
      identity.setChrChecklist(chrChecklist);
      identity.setFeatureLabel(feature.getFeatureLabel());
      identity.setCompositeFeatureInd(ChrStringUtils.booleanToIndictor(feature.getCompositeFeatureInd()));
      // Always reconcile the composite back-reference: link to the named composite parent when present,
      // otherwise clear it. Only ever *setting* it (the legacy behaviour) leaves a stale
      // COMPOSITE_CHR_FEATURE_ID when a feature is uncombined, so it never actually becomes individual.
      ChrFeatureIdentity compositeParent = ChrStringUtils.hasAValue(feature.getCompositeFeature())
          ? findFeatureIdentity(checklistId, feature.getCompositeFeature())
          : null;
      identity.setCompositeChrFeatureIdentity(
          compositeParent != null ? compositeParent.getChrFeatureId() : null);
      entityManager.persist(identity);
      if (newIdentity) {
        chrChecklist.getChrFeatureIdentities().add(identity);
      }
      feature.setId(identity.getChrFeatureId().toString());
      long featureId = identity.getChrFeatureId();

      // Detach the eager-loaded child collections before the delete-then-reinsert rewrites below.
      clearIdentityChildren(identity);
      saveFeatureInfoSource(feature, featureId, userId);
      ChrFeatureDetail detail = saveFeatureDetail(feature, identity, userId);
      clearDetailChildren(detail);
      saveFeatureTypeXrefs(feature, detail, featureId, userId);
      saveFeatureLocationDetails(feature, detail, featureId, userId);
      saveFeatureAgeXrefs(feature, featureId, userId);
      savePlannedStrategies(feature, detail, featureId, userId);
      saveUsedStrategies(feature, detail, featureId, userId);
      saveDamageAgentXrefs(feature, featureId, userId);
      saveWindthrowTreatmentXrefs(feature, featureId, userId);
    }

    // Pass 2: associated features (label -> id) once all identities are persisted.
    for (Feature feature : resource.getFeatures()) {
      saveAssociatedFeatures(feature, checklistId, userId);
    }
  }

  private ChrFeatureIdentity findFeatureIdentity(long checklistId, String featureLabel) {
    List<ChrFeatureIdentity> rows = entityManager.createQuery(
            "SELECT fi FROM ChrFeatureIdentity fi "
                + "WHERE fi.chrChecklist.chrChecklistId = :cid AND fi.featureLabel = :label",
            ChrFeatureIdentity.class)
        .setParameter("cid", checklistId)
        .setParameter("label", featureLabel)
        .getResultList();
    return rows.isEmpty() ? null : rows.get(0);
  }

  /** Delete-then-reinsert of the single info-source xref (the code is part of the PK). */
  private void saveFeatureInfoSource(Feature feature, long featureId, String userId) {
    removeXrefsByFeatureId("ChrFeatureInfoSourceXref", featureId);
    if (ChrStringUtils.hasAValue(feature.getFeatureInfoSourceCode())) {
      ChrFeatureInfoSourceXref xref = new ChrFeatureInfoSourceXref();
      xref.setId(new ChrFeatureInfoSourceXrefId(featureId, feature.getFeatureInfoSourceCode()));
      stampEntry(xref::setEntryTimestamp, xref::setEntryUserid, userId);
      stampUpdate(xref::setUpdateTimestamp, xref::setUpdateUserid, userId);
      entityManager.persist(xref);
    }
  }

  private ChrFeatureDetail saveFeatureDetail(Feature feature, ChrFeatureIdentity identity, String userId) {
    ChrFeatureDetail detail = entityManager.find(ChrFeatureDetail.class, identity.getChrFeatureId());
    if (detail != null) {
      detail.setUpdateTimestamp(new Date());
      detail.setUpdateUserid(userId);
    } else {
      detail = new ChrFeatureDetail();
      // Legacy defaults for inserts; overwritten below from the payload when present.
      detail.setEffectiveStratsUsedInd("N");
      detail.setAlternateStratsAvailInd("N");
      detail.setEntryTimestamp(new Date());
      detail.setUpdateTimestamp(new Date());
      detail.setEntryUserid(userId);
      detail.setUpdateUserid(userId);
    }
    detail.setChrFeatureIdentity(identity);
    detail.setUniformStrategyAppliedInd(ChrStringUtils.booleanToIndictor(feature.getForCompositeFeaturesInd()));
    detail.setFeatureLocatedInd(ChrStringUtils.booleanToIndictorInverseLogic(feature.getUnabletoLocate()));
    detail.setManagementAppliedInd(ChrStringUtils.booleanToIndictorInverseLogic(feature.getNoManagement()));
    detail.setRegdArchaeologicalSiteInd(ChrStringUtils.booleanToIndictor(feature.getChrRegisteredSite()));
    detail.setDescription(feature.getFeatureDescription());
    detail.setAreaWidthMeters(toBigDecimal(feature.getWidthofFeature()));
    detail.setAreaLengthMeters(toBigDecimal(feature.getLengthofFeature()));
    if (ChrStringUtils.hasAValue(feature.getAreaofFeature())) {
      detail.setAreaHectares(new BigDecimal(feature.getAreaofFeature()));
    }
    detail.setFnMgmtRecommendationsInd(ChrStringUtils.booleanToIndictor(feature.getManagementStrategyFN()));
    detail.setSitePlanStratsRecommndInd(ChrStringUtils.booleanToIndictor(feature.getManagementStrategySP()));
    detail.setPermitIssuedInd(ChrStringUtils.booleanToIndictorInverseLogic(feature.getSitePermitIssued()));
    detail.setBordenNo(feature.getBorden());
    detail.setPermitNumber(feature.getPermit());
    detail.setEvidenceOfDamageInd(
        ChrStringUtils.booleanToIndictor(feature.getQ1Isthereevidenceofdamagetothesiteorfeature()));
    detail.setDamageDescription(feature.getDescriptionofdamage());
    FrepChecklistAnswerCode answer = entityManager.find(
        FrepChecklistAnswerCode.class,
        feature.getQ3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse());
    if (answer == null) {
      answer = entityManager.find(FrepChecklistAnswerCode.class, "N");
    }
    detail.setDamageIrreversibleAnswerCd(answer);
    detail.setWindthrowMgmtApplicableInd(ChrStringUtils.booleanToIndictor(feature.getWindthrowManagement()));
    detail.setAreaWindfirmInd(ChrStringUtils.booleanToIndictor(feature.getWindthrow()));
    if (ChrStringUtils.hasAValue(feature.getEstwindthrow())) {
      detail.setEstWindthrowPercent(Short.parseShort(feature.getEstwindthrow()));
    }
    detail.setTrailFeaturesApplicableInd(ChrStringUtils.booleanToIndictor(feature.getTrailfeatures()));
    detail.setTrailLocatableInd(ChrStringUtils.booleanToIndictor(feature.getCanthetrailstillbelocated()));
    detail.setTrailLessPassableInd(ChrStringUtils.booleanToIndictor(feature.getHasthetrailbeenmadelesspassble()));
    detail.setTrailAreaDamagedInd(ChrStringUtils.booleanToIndictor(feature.getIsthereevidenceofdamage()));
    if (ChrStringUtils.hasAValue(feature.getTrailLength())) {
      detail.setEstTrailDamagePercent(Short.parseShort(feature.getTrailLength()));
    }
    detail.setLimitingOperatnlFactorsInd(ChrStringUtils.booleanToIndictor(
        feature.getQ4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature()));
    detail.setLimitingOperatnlFactorsDesc(feature.getQ4Description());
    detail.setEffectiveStratsUsedInd(ChrStringUtils.booleanToIndictor(
        feature.getQ5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective()));
    detail.setEffectiveStratsUsedDesc(feature.getQ5Description());
    detail.setAlternateStratsAvailInd(ChrStringUtils.booleanToIndictor(
        feature.getQ6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature()));
    detail.setAlternateStratsAvailDesc(feature.getQ6Description());
    if (ChrStringUtils.hasAValue(feature.getFeatureRating())) {
      detail.setChrSiteEvaluationCode(
          entityManager.find(ChrSiteEvaluationCode.class, feature.getFeatureRating()));
    }
    detail.setEvaluationRatingRationale(feature.getFeatureRatingRationale());
    entityManager.persist(detail);
    return detail;
  }

  private void saveFeatureTypeXrefs(Feature feature, ChrFeatureDetail detail, long featureId, String userId) {
    removeXrefsByFeatureId("ChrFeatureTypeXref", featureId);
    persistIf(feature.getCulturalTraildesignated(), () -> newTypeXref(featureId, ChrConstants.ChrFeatureTypeCode.CTDESIG, null, null, userId));
    persistIf(feature.getCulturalTrailundesignated(), () -> newTypeXref(featureId, ChrConstants.ChrFeatureTypeCode.CTUNDESIG, null, null, userId));
    persistIf(feature.getBurialSite(), () -> newTypeXref(featureId, ChrConstants.ChrFeatureTypeCode.BURIALSITE, null, null, userId));
    persistIf(feature.getNest(), () -> newTypeXref(featureId, ChrConstants.ChrFeatureTypeCode.NEST, null, null, userId));
    persistIf(feature.getCeremonialSite(), () -> newTypeXref(featureId, ChrConstants.ChrFeatureTypeCode.CERMSITE, null, null, userId));
    persistIf(feature.getCremationSite(), () -> newTypeXref(featureId, ChrConstants.ChrFeatureTypeCode.CREMATSITE, null, null, userId));
    persistIf(feature.getOfCMTs(), () -> newTypeXref(featureId, ChrConstants.ChrFeatureTypeCode.CMT, null, feature.getOfCMTsNumber(), userId));
    persistIf(feature.getCaveorotherKarst(), () -> newTypeXref(featureId, ChrConstants.ChrFeatureTypeCode.CAVE, null, null, userId));
    persistIf(feature.getDen(), () -> newTypeXref(featureId, ChrConstants.ChrFeatureTypeCode.DEN, null, null, userId));
    persistIf(feature.getTraditionalUseSite(), () -> newTypeXref(featureId, ChrConstants.ChrFeatureTypeCode.TUS, null, null, userId));
    persistIf(feature.getCedarBarkStripArea(), () -> newTypeXref(featureId, ChrConstants.ChrFeatureTypeCode.CEDARSTRIP, null, null, userId));
    persistIf(feature.getRockOutcrop(), () -> newTypeXref(featureId, ChrConstants.ChrFeatureTypeCode.ROCKOUTCRP, null, null, userId));
    persistIf(feature.getSpiritualSite(), () -> newTypeXref(featureId, ChrConstants.ChrFeatureTypeCode.SPIRSITE, null, null, userId));
    persistIf(feature.getOfMonumentalCedars(), () -> newTypeXref(featureId, ChrConstants.ChrFeatureTypeCode.MONCEDAR, null, feature.getStandofMonumentalCedar(), userId));
    persistIf(feature.getCulturalDepression(), () -> newTypeXref(featureId, ChrConstants.ChrFeatureTypeCode.CULTDEP, null, null, userId));
    persistIf(feature.getLithics(), () -> newTypeXref(featureId, ChrConstants.ChrFeatureTypeCode.LITHICS, null, null, userId));
    persistIf(feature.getOther(), () -> newTypeXref(featureId, ChrConstants.ChrFeatureTypeCode.OTH, feature.getOtherDescription(), null, userId));
  }

  private void saveFeatureLocationDetails(Feature feature, ChrFeatureDetail detail, long featureId, String userId) {
    removeXrefsByFeatureId("ChrFeatureLocationDetail", featureId);
    persistIf(feature.getInharvestedarea(), () -> newLocationDetail(featureId, ChrConstants.ChrFeatureLocnContextCode.INHARV, null, null, userId));
    persistIf(feature.getAdjacenttoblock(), () -> newLocationDetail(featureId, ChrConstants.ChrFeatureLocnContextCode.ADJBLK, null, null, userId));
    persistIf(feature.getAdjacenttowater(), () -> newLocationDetail(featureId, ChrConstants.ChrFeatureLocnContextCode.ADJWTR, null, null, userId));
    persistIf(feature.getLocationOther(), () -> newLocationDetail(featureId, ChrConstants.ChrFeatureLocnContextCode.OTH, feature.getLocationOtherDescription(), null, userId));
    persistIf(feature.getEntirecutblock(), () -> newLocationDetail(featureId, ChrConstants.ChrFeatureLocnContextCode.CUTBLK, null, null, userId));
    if ("true".equals(feature.getInReserve())) {
      ChrReserveTypeCode reserve = ChrStringUtils.hasAValue(feature.getLocationReservetype())
          ? entityManager.find(ChrReserveTypeCode.class, feature.getLocationReservetype())
          : null;
      entityManager.persist(newLocationDetail(featureId, ChrConstants.ChrFeatureLocnContextCode.RESERV, null, reserve, userId));
    }
  }

  private void saveFeatureAgeXrefs(Feature feature, long featureId, String userId) {
    removeXrefsByFeatureId("ChrFeatureAgeXref", featureId);
    persistIf(feature.getPre1846(), () -> newAgeXref(featureId, ChrConstants.ChrFeatureAgeCode.PRE1846, userId));
    persistIf(feature.getPost1846(), () -> newAgeXref(featureId, ChrConstants.ChrFeatureAgeCode.POST1846, userId));
    persistIf(feature.getAgeUnknown(), () -> newAgeXref(featureId, ChrConstants.ChrFeatureAgeCode.UNK, userId));
    persistIf(feature.getHistoricalUse(), () -> newAgeXref(featureId, ChrConstants.ChrFeatureAgeCode.HIST, userId));
  }

  private void savePlannedStrategies(Feature feature, ChrFeatureDetail detail, long featureId, String userId) {
    removeStrategies("ChrMgmtStrategyPlanned", featureId);
    ChrMgmtStrategySourceCode fn = entityManager.find(ChrMgmtStrategySourceCode.class, ChrConstants.ChrMgmtStrategySourceCode.FN);
    ChrMgmtStrategySourceCode aia = entityManager.find(ChrMgmtStrategySourceCode.class, ChrConstants.ChrMgmtStrategySourceCode.AIASAP);
    ChrMgmtStrategySourceCode sp = entityManager.find(ChrMgmtStrategySourceCode.class, ChrConstants.ChrMgmtStrategySourceCode.SP);
    persistIf(feature.getModifyBlockBoundaryFN(), () -> buildPlanned(detail, fn, ChrConstants.ChrMgmtStrategyTypeCode.MODBLOCK, null, null, null, userId));
    persistIf(feature.getModifyBlockBoundaryAIA(), () -> buildPlanned(detail, aia, ChrConstants.ChrMgmtStrategyTypeCode.MODBLOCK, null, null, null, userId));
    persistIf(feature.getModifyBlockBoundarySP(), () -> buildPlanned(detail, sp, ChrConstants.ChrMgmtStrategyTypeCode.MODBLOCK, null, null, null, userId));
    persistIf(feature.getRetainBufferFN(), () -> buildPlanned(detail, fn, ChrConstants.ChrMgmtStrategyTypeCode.BUFFER, null, feature.getBufferLengthFN(), null, userId));
    persistIf(feature.getRetainBufferAIA(), () -> buildPlanned(detail, aia, ChrConstants.ChrMgmtStrategyTypeCode.BUFFER, null, feature.getBufferLengthAIA(), null, userId));
    persistIf(feature.getRetainBufferSP(), () -> buildPlanned(detail, sp, ChrConstants.ChrMgmtStrategyTypeCode.BUFFER, null, feature.getBufferLengthSP(), null, userId));
    persistIf(feature.getRetaininHarvestAreaFN(), () -> buildPlanned(detail, fn, ChrConstants.ChrMgmtStrategyTypeCode.NOBUFF, null, null, null, userId));
    persistIf(feature.getRetaininHarvestAreaAIA(), () -> buildPlanned(detail, aia, ChrConstants.ChrMgmtStrategyTypeCode.NOBUFF, null, null, null, userId));
    persistIf(feature.getRetaininHarvestAreaSP(), () -> buildPlanned(detail, sp, ChrConstants.ChrMgmtStrategyTypeCode.NOBUFF, null, null, null, userId));
    persistIf(feature.getCrownorstandmodificationFN(), () -> buildPlanned(detail, fn, ChrConstants.ChrMgmtStrategyTypeCode.CROWNMOD, null, null, null, userId));
    persistIf(feature.getCrownorstandmodificationAIA(), () -> buildPlanned(detail, aia, ChrConstants.ChrMgmtStrategyTypeCode.CROWNMOD, null, null, null, userId));
    persistIf(feature.getCrownorstandmodificationSP(), () -> buildPlanned(detail, sp, ChrConstants.ChrMgmtStrategyTypeCode.CROWNMOD, null, null, null, userId));
    persistIf(feature.getConserveinRotationalReserveFN(), () -> buildPlanned(detail, fn, ChrConstants.ChrMgmtStrategyTypeCode.ROTATRES, feature.getConserveRotationalReserveTypeFN(), null, null, userId));
    persistIf(feature.getConserveinRotationalReserveAIA(), () -> buildPlanned(detail, aia, ChrConstants.ChrMgmtStrategyTypeCode.ROTATRES, feature.getConserveRotationalReserveTypeAIA(), null, null, userId));
    persistIf(feature.getConserveinRotationalReserveSP(), () -> buildPlanned(detail, sp, ChrConstants.ChrMgmtStrategyTypeCode.ROTATRES, feature.getConserveRotationalReserveTypeSP(), null, null, userId));
    persistIf(feature.getPermanentReserveFN(), () -> buildPlanned(detail, fn, ChrConstants.ChrMgmtStrategyTypeCode.TEMPRES, feature.getTemporaryRetentionTypeFN(), null, null, userId));
    persistIf(feature.getPermanentReserveAIA(), () -> buildPlanned(detail, aia, ChrConstants.ChrMgmtStrategyTypeCode.TEMPRES, feature.getTemporaryRetentionTypeAIA(), null, null, userId));
    persistIf(feature.getPermanentReserveSP(), () -> buildPlanned(detail, sp, ChrConstants.ChrMgmtStrategyTypeCode.TEMPRES, feature.getTemporaryRetentionTypeSP(), null, null, userId));
    persistIf(feature.getDatetheFeatureFN(), () -> buildPlanned(detail, fn, ChrConstants.ChrMgmtStrategyTypeCode.DATEFEAT, null, null, null, userId));
    persistIf(feature.getDatetheFeatureAIA(), () -> buildPlanned(detail, aia, ChrConstants.ChrMgmtStrategyTypeCode.DATEFEAT, null, null, null, userId));
    persistIf(feature.getDatetheFeatureSP(), () -> buildPlanned(detail, sp, ChrConstants.ChrMgmtStrategyTypeCode.DATEFEAT, null, null, null, userId));
    persistIf(feature.getStubCMTsabovescarFN(), () -> buildPlanned(detail, fn, ChrConstants.ChrMgmtStrategyTypeCode.STUBCMT, null, null, null, userId));
    persistIf(feature.getStubCMTsabovescarAIA(), () -> buildPlanned(detail, aia, ChrConstants.ChrMgmtStrategyTypeCode.STUBCMT, null, null, null, userId));
    persistIf(feature.getStubCMTsabovescarSP(), () -> buildPlanned(detail, sp, ChrConstants.ChrMgmtStrategyTypeCode.STUBCMT, null, null, null, userId));
    persistIf(feature.getStubnonCMTsFN(), () -> buildPlanned(detail, fn, ChrConstants.ChrMgmtStrategyTypeCode.STUBNONCMT, null, null, null, userId));
    persistIf(feature.getStubnonCMTsAIA(), () -> buildPlanned(detail, aia, ChrConstants.ChrMgmtStrategyTypeCode.STUBNONCMT, null, null, null, userId));
    persistIf(feature.getStubnonCMTsSP(), () -> buildPlanned(detail, sp, ChrConstants.ChrMgmtStrategyTypeCode.STUBNONCMT, null, null, null, userId));
    persistIf(feature.getLeaveStandingFN(), () -> buildPlanned(detail, fn, ChrConstants.ChrMgmtStrategyTypeCode.LEAVESTAND, null, null, null, userId));
    persistIf(feature.getLeaveStandingAIA(), () -> buildPlanned(detail, aia, ChrConstants.ChrMgmtStrategyTypeCode.LEAVESTAND, null, null, null, userId));
    persistIf(feature.getLeaveStandingSP(), () -> buildPlanned(detail, sp, ChrConstants.ChrMgmtStrategyTypeCode.LEAVESTAND, null, null, null, userId));
    persistIf(feature.getAvoidSilvAvoidPlantingFN(), () -> buildPlanned(detail, fn, ChrConstants.ChrMgmtStrategyTypeCode.AVOIDPLNT, null, null, null, userId));
    persistIf(feature.getAvoidSilvAvoidPlantingAIA(), () -> buildPlanned(detail, aia, ChrConstants.ChrMgmtStrategyTypeCode.AVOIDPLNT, null, null, null, userId));
    persistIf(feature.getAvoidSilvAvoidPlantingSP(), () -> buildPlanned(detail, sp, ChrConstants.ChrMgmtStrategyTypeCode.AVOIDPLNT, null, null, null, userId));
    persistIf(feature.getAvoidSilvAvoidSitePrepFN(), () -> buildPlanned(detail, fn, ChrConstants.ChrMgmtStrategyTypeCode.AVOIDSPREP, null, null, null, userId));
    persistIf(feature.getAvoidSilvAvoidSitePrepAIA(), () -> buildPlanned(detail, aia, ChrConstants.ChrMgmtStrategyTypeCode.AVOIDSPREP, null, null, null, userId));
    persistIf(feature.getAvoidSilvAvoidSitePrepSP(), () -> buildPlanned(detail, sp, ChrConstants.ChrMgmtStrategyTypeCode.AVOIDSPREP, null, null, null, userId));
    persistIf(feature.getMachineFreeZoneFN(), () -> buildPlanned(detail, fn, ChrConstants.ChrMgmtStrategyTypeCode.MFZ, null, null, null, userId));
    persistIf(feature.getMachineFreeZoneAIA(), () -> buildPlanned(detail, aia, ChrConstants.ChrMgmtStrategyTypeCode.MFZ, null, null, null, userId));
    persistIf(feature.getMachineFreeZoneSP(), () -> buildPlanned(detail, sp, ChrConstants.ChrMgmtStrategyTypeCode.MFZ, null, null, null, userId));
    persistIf(feature.getHarvestUnderSapFN(), () -> buildPlanned(detail, fn, ChrConstants.ChrMgmtStrategyTypeCode.HARVSTSAP, null, null, null, userId));
    persistIf(feature.getHarvestUnderSapAIA(), () -> buildPlanned(detail, aia, ChrConstants.ChrMgmtStrategyTypeCode.HARVSTSAP, null, null, null, userId));
    persistIf(feature.getHarvestUnderSapSP(), () -> buildPlanned(detail, sp, ChrConstants.ChrMgmtStrategyTypeCode.HARVSTSAP, null, null, null, userId));
    persistIf(feature.getWinterHarvestFrozenGroundFN(), () -> buildPlanned(detail, fn, ChrConstants.ChrMgmtStrategyTypeCode.WINTERHARV, null, null, null, userId));
    persistIf(feature.getWinterHarvestFrozenGroundAIA(), () -> buildPlanned(detail, aia, ChrConstants.ChrMgmtStrategyTypeCode.WINTERHARV, null, null, null, userId));
    persistIf(feature.getWinterHarvestFrozenGroundSP(), () -> buildPlanned(detail, sp, ChrConstants.ChrMgmtStrategyTypeCode.WINTERHARV, null, null, null, userId));
    for (OtherPlannedManagementStrategy other : feature.getOtherPlannedManagementStrategy()) {
      persistIf(other.getFnInd(), () -> buildPlanned(detail, fn, ChrConstants.ChrMgmtStrategyTypeCode.OTH, null, null, other.getOtherStrategy(), userId));
      persistIf(other.getAiaInd(), () -> buildPlanned(detail, aia, ChrConstants.ChrMgmtStrategyTypeCode.OTH, null, null, other.getOtherStrategy(), userId));
      persistIf(other.getSpInd(), () -> buildPlanned(detail, sp, ChrConstants.ChrMgmtStrategyTypeCode.OTH, null, null, other.getOtherStrategy(), userId));
    }
  }

  private void saveUsedStrategies(Feature feature, ChrFeatureDetail detail, long featureId, String userId) {
    removeStrategies("ChrMgmtStrategyUsed", featureId);
    persistIf(feature.getPartiallytemporaryreserve(), () -> buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.TEMPRES, feature.getPartiallytemporaryreservetype(), Boolean.FALSE, null, null, userId));
    persistIf(feature.getModifiedblockboundary(), () -> buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.MODBLOCK, null, Boolean.FALSE, null, null, userId));
    persistIf(feature.getFullyconservedintemporaryreserve(), () -> buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.TEMPRES, feature.getFullytemporaryreserve(), Boolean.TRUE, null, null, userId));
    persistIf(feature.getRetainabuffer(), () -> buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.BUFFER, null, null, feature.getBufferWidthMeter(), null, userId));
    persistIf(feature.getPartiallyconservedinpermanentreserve(), () -> buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.ROTATRES, feature.getPartiallyconservedinpermanentreserveType(), Boolean.FALSE, null, null, userId));
    persistIf(feature.getFullyconservedinpermanentreserve(), () -> buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.ROTATRES, feature.getFullyconservedinpermanentreserveType(), Boolean.TRUE, null, null, userId));
    persistIf(feature.getCompledCrownorstandmodification(), () -> buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.CROWNMOD, null, Boolean.FALSE, null, null, userId));
    persistIf(feature.getDatedthefeature(), () -> buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.DATEFEAT, null, Boolean.FALSE, null, null, userId));
    persistIf(feature.getRetainedinharvestareanobuffer(), () -> buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.NOBUFF, null, Boolean.FALSE, null, null, userId));
    persistIf(feature.getLeftStanding(), () -> buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.LEAVESTAND, null, Boolean.FALSE, null, null, userId));
    persistIf(feature.getStubbed(), () -> buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.STUBCMT, null, Boolean.FALSE, null, null, userId));
    persistIf(feature.getStubbedNon(), () -> buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.STUBNONCMT, null, Boolean.FALSE, null, null, userId));
    persistIf(feature.getAvoidSilvAvoidPlanting(), () -> buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.AVOIDPLNT, null, Boolean.FALSE, null, null, userId));
    persistIf(feature.getAvoidSilvAvoidSitePrep(), () -> buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.AVOIDSPREP, null, Boolean.FALSE, null, null, userId));
    persistIf(feature.getMachineFreeZone(), () -> buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.MFZ, null, Boolean.FALSE, null, null, userId));
    persistIf(feature.getHarvestUnderSap(), () -> buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.HARVSTSAP, null, Boolean.FALSE, null, null, userId));
    persistIf(feature.getWinterHarvestFrozenGround(), () -> buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.WINTERHARV, null, Boolean.FALSE, null, null, userId));
    if (ChrStringUtils.hasAValue(feature.getOtherActivities())) {
      entityManager.persist(buildUsed(detail, ChrConstants.ChrMgmtStrategyTypeCode.OTH, null, null, null, feature.getOtherActivities(), userId));
    }
  }

  private void saveDamageAgentXrefs(Feature feature, long featureId, String userId) {
    removeXrefsByFeatureId("ChrFeatureDamageAgentXref", featureId);
    persistIf(feature.getHarvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause(), () -> newDamageAgentXref(featureId, ChrConstants.ChrFeatureDamageAgentCode.HARV, null, userId));
    persistIf(feature.getSafetyQ2Wheredamagehasoccurredwhatisthemostlikelycause(), () -> newDamageAgentXref(featureId, ChrConstants.ChrFeatureDamageAgentCode.SAFETY, null, userId));
    persistIf(feature.getSilvicultureQ2Wheredamagehasoccurredwhatisthemostlikelycause(), () -> newDamageAgentXref(featureId, ChrConstants.ChrFeatureDamageAgentCode.SIL, null, userId));
    persistIf(feature.getRecreationQ2Wheredamagehasoccurredwhatisthemostlikelycause(), () -> newDamageAgentXref(featureId, ChrConstants.ChrFeatureDamageAgentCode.RECUSE, null, userId));
    persistIf(feature.getFireQ2Wheredamagehasoccurredwhatisthemostlikelycause(), () -> newDamageAgentXref(featureId, ChrConstants.ChrFeatureDamageAgentCode.FIRE, null, userId));
    persistIf(feature.getIndustrialUseQ2Wheredamagehasoccurredwhatisthemostlikelycause(), () -> newDamageAgentXref(featureId, ChrConstants.ChrFeatureDamageAgentCode.INDUSTR, null, userId));
    persistIf(feature.getRoadQ2Wheredamagehasoccurredwhatisthemostlikelycause(), () -> newDamageAgentXref(featureId, ChrConstants.ChrFeatureDamageAgentCode.ROADBD, null, userId));
    persistIf(feature.getLivestockQ2Wheredamagehasoccurredwhatisthemostlikelycause(), () -> newDamageAgentXref(featureId, ChrConstants.ChrFeatureDamageAgentCode.LVS, null, userId));
    persistIf(feature.getWindthrowQ2Wheredamagehasoccurredwhatisthemostlikelycause(), () -> newDamageAgentXref(featureId, ChrConstants.ChrFeatureDamageAgentCode.WINDTHR, null, userId));
    persistIf(feature.getOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause(), () -> newDamageAgentXref(featureId, ChrConstants.ChrFeatureDamageAgentCode.OTH, feature.getIfotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause(), userId));
  }

  private void saveWindthrowTreatmentXrefs(Feature feature, long featureId, String userId) {
    removeXrefsByFeatureId("ChrFeatWindthrTreatXref", featureId);
    persistIf(feature.getWindthrowTechniqueNone(), () -> newWindthrowXref(featureId, ChrConstants.ChrWindthrowTreatmentCode.NONE, null, userId));
    persistIf(feature.getWindthrowTechniqueRetentionBuffer(), () -> newWindthrowXref(featureId, ChrConstants.ChrWindthrowTreatmentCode.BUFFER, null, userId));
    persistIf(feature.getWindthrowTechniquePruning(), () -> newWindthrowXref(featureId, ChrConstants.ChrWindthrowTreatmentCode.PRUNING, null, userId));
    persistIf(feature.getWindthrowTechniqueFeathering(), () -> newWindthrowXref(featureId, ChrConstants.ChrWindthrowTreatmentCode.FEATHERING, null, userId));
    persistIf(feature.getWindthrowTechniqueTopping(), () -> newWindthrowXref(featureId, ChrConstants.ChrWindthrowTreatmentCode.TOPPING, null, userId));
    persistIf(feature.getOtherTechnique(), () -> newWindthrowXref(featureId, ChrConstants.ChrWindthrowTreatmentCode.OTHER, feature.getIfotherpleasedescribe(), userId));
  }

  private void saveAssociatedFeatures(Feature feature, long checklistId, String userId) {
    long fromId = Long.parseLong(feature.getId());
    List<ChrAssociatedFeatureXref> existing = entityManager.createQuery(
            "SELECT a FROM ChrAssociatedFeatureXref a WHERE a.id.fromChrFeatureId = :fid",
            ChrAssociatedFeatureXref.class)
        .setParameter("fid", fromId)
        .getResultList();
    for (ChrAssociatedFeatureXref a : existing) {
      entityManager.remove(a);
    }
    entityManager.flush();
    if (feature.getAssociatedFeatures() == null) {
      return;
    }
    for (String label : feature.getAssociatedFeatures()) {
      ChrFeatureIdentity to = findFeatureIdentity(checklistId, label);
      if (to == null) {
        continue;
      }
      ChrAssociatedFeatureXref xref = new ChrAssociatedFeatureXref();
      xref.setId(new ChrAssociatedFeatureXrefId(fromId, to.getChrFeatureId()));
      stampEntry(xref::setEntryTimestamp, xref::setEntryUserid, userId);
      stampUpdate(xref::setUpdateTimestamp, xref::setUpdateUserid, userId);
      entityManager.persist(xref);
    }
  }

  // --- factory helpers (mirror legacy RestDataManager new* methods) ---

  private ChrFeatureTypeXref newTypeXref(long featureId, String code, String otherDescription, String quantity, String userId) {
    ChrFeatureTypeXref xref = new ChrFeatureTypeXref();
    xref.setId(new ChrFeatureTypeXrefId(featureId, code));
    if (ChrStringUtils.hasAValue(otherDescription)) {
      xref.setOtherDescription(otherDescription);
    }
    if (ChrStringUtils.hasAValue(quantity)) {
      xref.setQuantity(Long.parseLong(quantity));
    }
    stampEntry(xref::setEntryTimestamp, xref::setEntryUserid, userId);
    stampUpdate(xref::setUpdateTimestamp, xref::setUpdateUserid, userId);
    return xref;
  }

  private ChrFeatureAgeXref newAgeXref(long featureId, String code, String userId) {
    ChrFeatureAgeXref xref = new ChrFeatureAgeXref();
    xref.setId(new ChrFeatureAgeXrefId(featureId, code));
    stampEntry(xref::setEntryTimestamp, xref::setEntryUserid, userId);
    stampUpdate(xref::setUpdateTimestamp, xref::setUpdateUserid, userId);
    return xref;
  }

  private ChrFeatureLocationDetail newLocationDetail(long featureId, String code, String otherDescription, ChrReserveTypeCode reserveType, String userId) {
    ChrFeatureLocationDetail detail = new ChrFeatureLocationDetail();
    detail.setId(new ChrFeatureLocationDetailId(featureId, code));
    detail.setOtherDescription(otherDescription);
    if (reserveType != null) {
      detail.setChrReserveTypeCode(reserveType);
    }
    stampEntry(detail::setEntryTimestamp, detail::setEntryUserid, userId);
    stampUpdate(detail::setUpdateTimestamp, detail::setUpdateUserid, userId);
    return detail;
  }

  private ChrFeatureDamageAgentXref newDamageAgentXref(long featureId, String code, String otherDescription, String userId) {
    ChrFeatureDamageAgentXref xref = new ChrFeatureDamageAgentXref();
    xref.setId(new ChrFeatureDamageAgentXrefId(featureId, code));
    xref.setOtherDescription(otherDescription);
    stampEntry(xref::setEntryTimestamp, xref::setEntryUserid, userId);
    stampUpdate(xref::setUpdateTimestamp, xref::setUpdateUserid, userId);
    return xref;
  }

  private ChrFeatWindthrTreatXref newWindthrowXref(long featureId, String code, String otherDescription, String userId) {
    ChrFeatWindthrTreatXref xref = new ChrFeatWindthrTreatXref();
    xref.setId(new ChrFeatWindthrTreatXrefId(featureId, code));
    xref.setOtherDescription(otherDescription);
    stampEntry(xref::setEntryTimestamp, xref::setEntryUserid, userId);
    stampUpdate(xref::setUpdateTimestamp, xref::setUpdateUserid, userId);
    return xref;
  }

  private ChrMgmtStrategyPlanned buildPlanned(ChrFeatureDetail detail, ChrMgmtStrategySourceCode source, String typeCode, String reserveCode, String bufferWidth, String otherStrategy, String userId) {
    ChrMgmtStrategyPlanned planned = new ChrMgmtStrategyPlanned();
    planned.setChrFeatureDetail(detail);
    planned.setChrMgmtStrategySourceCode(source);
    planned.setChrMgmtStrategyTypeCode(entityManager.find(ChrMgmtStrategyTypeCode.class, typeCode));
    if (ChrStringUtils.hasAValue(reserveCode)) {
      planned.setChrReserveTypeCode(entityManager.find(ChrReserveTypeCode.class, reserveCode));
    }
    if (ChrStringUtils.hasAValue(bufferWidth)) {
      planned.setBufferWidthMeters(BigDecimal.valueOf(Long.parseLong(bufferWidth)));
    }
    if (ChrStringUtils.hasAValue(otherStrategy)) {
      planned.setOtherStrategy(otherStrategy);
    }
    stampEntry(planned::setEntryTimestamp, planned::setEntryUserid, userId);
    stampUpdate(planned::setUpdateTimestamp, planned::setUpdateUserid, userId);
    return planned;
  }

  private ChrMgmtStrategyUsed buildUsed(ChrFeatureDetail detail, String typeCode, String reserveCode, Boolean fullyConserved, String bufferWidth, String otherStrategy, String userId) {
    ChrMgmtStrategyUsed used = new ChrMgmtStrategyUsed();
    used.setChrFeatureDetail(detail);
    used.setChrMgmtStrategyTypeCode(entityManager.find(ChrMgmtStrategyTypeCode.class, typeCode));
    if (ChrStringUtils.hasAValue(reserveCode)) {
      used.setChrReserveTypeCode(entityManager.find(ChrReserveTypeCode.class, reserveCode));
    }
    if (fullyConserved != null) {
      used.setFullyConservedInd(ChrStringUtils.booleanToIndictor(Boolean.toString(fullyConserved)));
    }
    if (ChrStringUtils.hasAValue(bufferWidth)) {
      used.setBufferWidthMeters(new BigDecimal(bufferWidth));
    }
    if (ChrStringUtils.hasAValue(otherStrategy)) {
      used.setOtherStrategy(otherStrategy);
    }
    stampEntry(used::setEntryTimestamp, used::setEntryUserid, userId);
    stampUpdate(used::setUpdateTimestamp, used::setUpdateUserid, userId);
    return used;
  }

  /**
   * Drop the eager-loaded child collections of a feature's identity/detail from the in-memory
   * graph. The per-feature save below rewrites every child collection via delete-then-reinsert;
   * those deletes ({@link #removeXrefsByFeatureId}/{@link #removeStrategies}) flush while the parent
   * (loaded eagerly with the checklist) still references the rows being deleted, which trips
   * Hibernate's flush-time integrity check. Clearing has no DML effect — the join columns are
   * insertable=false/updatable=false with no cascade — it only removes the stale references. The
   * graph is reloaded fresh from the database after the writes (the persistence context is cleared
   * in {@code saveFeaturesSection}).
   */
  @SuppressWarnings("unchecked")
  private void clearIdentityChildren(ChrFeatureIdentity identity) {
    identity.getChrFeatureInfoSourceXrefs().clear();
    identity.getChrAssociatedFeatureXrefsForFromChrFeatureId().clear();
    identity.getChrAssociatedFeatureXrefsForToChrFeatureId().clear();
  }

  @SuppressWarnings("unchecked")
  private void clearDetailChildren(ChrFeatureDetail detail) {
    detail.getChrFeatureTypeXrefs().clear();
    detail.getChrFeatureLocationDetails().clear();
    detail.getChrFeatureAgeXrefs().clear();
    detail.getChrFeatWindthrTreatXrefs().clear();
    detail.getChrFeatureDamageAgentXrefs().clear();
    detail.getChrMgmtStrategyUseds().clear();
    detail.getChrMgmtStrategyPlanneds().clear();
  }

  /**
   * Fully delete a feature that is no longer in the payload: its child cross-references first (the
   * detail's collections have no cascade, so they must be removed explicitly to satisfy the FKs),
   * then the identity — whose {@code chrFeatureDetail} cascade removes the detail row. The eager
   * child collections are cleared first so the deletes below don't flush while the still-loaded
   * identity/detail reference the rows being removed.
   */
  private void deleteFeature(ChrFeatureIdentity identity) {
    long featureId = identity.getChrFeatureId();
    detachComposedMembers(featureId);
    clearIdentityChildren(identity);
    ChrFeatureDetail detail = identity.getChrFeatureDetail();
    if (detail != null) {
      clearDetailChildren(detail);
    }
    removeXrefsByFeatureId("ChrFeatureInfoSourceXref", featureId);
    removeXrefsByFeatureId("ChrFeatureTypeXref", featureId);
    removeXrefsByFeatureId("ChrFeatureLocationDetail", featureId);
    removeXrefsByFeatureId("ChrFeatureAgeXref", featureId);
    removeXrefsByFeatureId("ChrFeatureDamageAgentXref", featureId);
    removeXrefsByFeatureId("ChrFeatWindthrTreatXref", featureId);
    removeStrategies("ChrMgmtStrategyPlanned", featureId);
    removeStrategies("ChrMgmtStrategyUsed", featureId);
    removeAssociatedFeatures(featureId);
    entityManager.remove(identity);
    entityManager.flush();
  }

  /**
   * Clears the composite back-reference on any feature that grouped under the feature being deleted.
   * {@code COMPOSITE_CHR_FEATURE_ID} is a self-FK on {@code CHR_FEATURE_IDENTITY}; without this, removing
   * a composite while its member features still point at it raises {@code ORA-02292} (child record
   * found) on the parent delete. The members become individual features, matching the user intent.
   * Runs on managed entities + flush (not a bulk update) so the persistence context stays consistent.
   */
  private void detachComposedMembers(long compositeFeatureId) {
    List<ChrFeatureIdentity> members = entityManager.createQuery(
            "SELECT fi FROM ChrFeatureIdentity fi WHERE fi.compositeChrFeatureIdentity = :cid",
            ChrFeatureIdentity.class)
        .setParameter("cid", compositeFeatureId)
        .getResultList();
    for (ChrFeatureIdentity member : members) {
      member.setCompositeChrFeatureIdentity(null);
    }
    if (!members.isEmpty()) {
      entityManager.flush();
    }
  }

  /** Removes associated-feature links pointing to or from a feature (both directions) and flushes. */
  private void removeAssociatedFeatures(long featureId) {
    List<ChrAssociatedFeatureXref> rows = entityManager.createQuery(
            "SELECT a FROM ChrAssociatedFeatureXref a "
                + "WHERE a.id.fromChrFeatureId = :fid OR a.id.toChrFeatureId = :fid",
            ChrAssociatedFeatureXref.class)
        .setParameter("fid", featureId)
        .getResultList();
    for (ChrAssociatedFeatureXref row : rows) {
      entityManager.remove(row);
    }
    entityManager.flush();
  }

  /** Removes all composite-id xrefs for a feature (keyed by {@code id.chrFeatureId}) and flushes. */
  private void removeXrefsByFeatureId(String entityName, long featureId) {
    List<?> rows = entityManager.createQuery(
            "SELECT x FROM " + entityName + " x WHERE x.id.chrFeatureId = :fid")
        .setParameter("fid", featureId)
        .getResultList();
    for (Object row : rows) {
      entityManager.remove(row);
    }
    entityManager.flush();
  }

  /** Removes all management strategies for a feature (keyed by the {@code chrFeatureDetail} FK) and flushes. */
  private void removeStrategies(String entityName, long featureId) {
    List<?> rows = entityManager.createQuery(
            "SELECT s FROM " + entityName + " s WHERE s.chrFeatureDetail.chrFeatureId = :fid")
        .setParameter("fid", featureId)
        .getResultList();
    for (Object row : rows) {
      entityManager.remove(row);
    }
    entityManager.flush();
  }

  private void persistIf(String indicator, java.util.function.Supplier<Object> entitySupplier) {
    if ("true".equals(indicator)) {
      entityManager.persist(entitySupplier.get());
    }
  }

  private void stampEntry(java.util.function.Consumer<Date> setTimestamp, java.util.function.Consumer<String> setUser, String userId) {
    setTimestamp.accept(new Date());
    setUser.accept(userId);
  }

  private void stampUpdate(java.util.function.Consumer<Date> setTimestamp, java.util.function.Consumer<String> setUser, String userId) {
    setTimestamp.accept(new Date());
    setUser.accept(userId);
  }

  private BigDecimal toBigDecimal(String value) {
    return ChrStringUtils.hasAValue(value) ? new BigDecimal(value) : null;
  }

  private Date parseDate(String value) {
    try {
      return ChrDateUtils.getDate(value);
    } catch (Exception ex) {
      return null;
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
}

package ca.bc.gov.nrs.frep.service.v1;

import ca.bc.gov.nrs.frep.ChrConstants;
import ca.bc.gov.nrs.frep.service.v1.ObjectStorageService;
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
import ca.bc.gov.nrs.frep.mapper.CheckListMapper;
import ca.bc.gov.nrs.frep.exception.EntityNotFoundException;
import ca.bc.gov.nrs.frep.exception.FrepApiRuntimeException;
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
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

  /**
   * The 3-letter Natural Resource District {@code org_unit_code} for a CHR checklist (via
   * checklist → resource value → selected site → org unit), used for district-scoped authorization.
   * Returns {@code null} if the checklist doesn't exist. Lightweight scalar projection — no entity
   * graph or photo bytes loaded.
   */
  public String getChecklistOrgUnitCode(long checklistId) {
    return entityManager.createQuery(
            "SELECT ou.orgUnitCode FROM ChrChecklist c "
                + "JOIN c.frepResourceValue rv JOIN rv.frepSelectedSite s JOIN s.orgUnit ou "
                + "WHERE c.chrChecklistId = :id",
            String.class)
        .setParameter("id", checklistId)
        .getResultStream()
        .findFirst()
        .orElse(null);
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
    // Photos are deliberately NOT written here. They are independent resources managed by
    // addPhoto/deletePhoto below; a checklist save must not touch them. The previous
    // savePictures(...) call reconciled the whole picture set, so a payload without pictures — which
    // is every payload now — deleted every photo row and every stored object. See
    // ChrChecklistPersistenceServiceTest#aChecklistSaveDoesNotDeleteExistingPhotoRows.

    entityManager.flush();
    resource.setRevisionCount(Long.toString(chrChecklist.getRevisionCount()));
    // Same reason as saveFeaturesSection below: saveFeatures rewrites the feature child xrefs by
    // delete-then-reinsert, and the new rows carry only their embedded ids — the code associations
    // the mapper reads are insertable=false. A caller that re-reads the checklist in this same
    // transaction (submit does, to return the submitted record) would otherwise map those cached
    // rows and get nulls back, so the response claimed the feature types, ages and information
    // source had been wiped when the flushed rows were fine. Dropping the context makes the re-read
    // load the whole graph fresh from the database.
    entityManager.clear();
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


  /**
   * Create a composite: insert the anchor, insert any features typed into the dialog, and point
   * every member at the anchor.
   *
   * <p>Order is forced by the self-FK on {@code COMPOSITE_CHR_FEATURE_ID}: the anchor has to exist
   * before anything can reference it, so it is inserted and flushed first. That is the whole reason
   * this is its own endpoint rather than a feature save that happens to mention a parent — and why
   * the payload needs no correlation id, since the request itself identifies the anchor.
   *
   * <p>Two rules the client also applies, enforced here because the database has no constraint for
   * either: a composite needs at least two members (an empty or single composite is a state the UI
   * cannot produce and cannot render sensibly), and creating one cannot take a feature that is
   * already assessed under a different composite — that would quietly empty the other group.
   *
   * @return the anchor and every member, so the caller can patch them all back
   */
  public List<Feature> createComposite(
      long checklistId,
      Feature anchorFeature,
      List<String> memberIds,
      List<Feature> newMembers,
      String userId)
      throws Exception {
    ChrChecklist chrChecklist = entityManager.find(ChrChecklist.class, checklistId);
    if (chrChecklist == null) {
      throw new EntityNotFoundException("Checklist " + checklistId + " was not found.");
    }
    if (anchorFeature == null) {
      throw new InvalidParameterException("A composite needs an anchor feature.");
    }
    List<String> existingIds = memberIds == null ? List.of() : memberIds;
    List<Feature> created = newMembers == null ? List.of() : newMembers;
    if (existingIds.size() + created.size() < 2) {
      throw new InvalidParameterException("A composite needs at least two features.");
    }

    List<ChrFeatureIdentity> members = new ArrayList<>();
    for (String memberId : existingIds) {
      ChrFeatureIdentity member =
          featureOnChecklist(chrChecklist, checklistId, Long.parseLong(memberId));
      if (member.getCompositeChrFeatureIdentity() != null) {
        throw new InvalidParameterException(
            "Feature " + memberId + " is already assessed under another composite.");
      }
      if ("Y".equalsIgnoreCase(member.getCompositeFeatureInd())) {
        throw new InvalidParameterException("Feature " + memberId + " is itself a composite.");
      }
      members.add(member);
    }

    // The anchor first, and flushed: everything below writes its id into a self-FK.
    anchorFeature.setCompositeFeatureInd("true");
    ChrFeatureIdentity anchor = createFeature(chrChecklist, anchorFeature, userId);
    entityManager.flush();

    for (Feature newMember : created) {
      newMember.setCompositeFeatureInd("false");
      members.add(createFeature(chrChecklist, newMember, userId));
    }

    Set<Long> touched = new LinkedHashSet<>();
    for (ChrFeatureIdentity member : members) {
      member.setCompositeChrFeatureIdentity(anchor.getChrFeatureId());
      touched.add(member.getChrFeatureId());
    }

    chrChecklist.setDeviceCheckoutGuid(null);
    stampChecklistUpdate(chrChecklist, userId);
    entityManager.flush();

    return mapFeaturesAfterWrite(checklistId, touched, anchor.getChrFeatureId());
  }

  /**
   * Dissolve a composite: delete the anchor, release its members, and delete the ones named.
   *
   * <p>The anchor goes in both cases. {@link #deleteFeature(ChrFeatureIdentity)} runs
   * {@code detachComposedMembers} on the way, which clears {@code COMPOSITE_CHR_FEATURE_ID} on
   * everything still pointing at it — that is what releases the survivors, and what keeps
   * {@code ORA-02292} off a self-FK that would otherwise still have children.
   *
   * <p>Every id in {@code deleteMemberIds} is checked to be a member of <em>this</em> composite
   * first. The client decides which members are undescribed, but it does not get to name arbitrary
   * features for deletion under cover of an ungroup.
   *
   * @return the members that survived, re-read with their membership cleared. The anchor and any
   *     deleted members are absent because they no longer exist; the caller asked for those and can
   *     drop them itself.
   */
  public List<Feature> ungroupComposite(
      long checklistId, long anchorId, List<String> deleteMemberIds, String userId)
      throws Exception {
    ChrChecklist chrChecklist = entityManager.find(ChrChecklist.class, checklistId);
    if (chrChecklist == null) {
      throw new EntityNotFoundException("Checklist " + checklistId + " was not found.");
    }
    ChrFeatureIdentity anchor = featureOnChecklist(chrChecklist, checklistId, anchorId);
    if (!"Y".equalsIgnoreCase(anchor.getCompositeFeatureInd())) {
      throw new InvalidParameterException("Feature " + anchorId + " is not a composite.");
    }

    Map<Long, ChrFeatureIdentity> members = new LinkedHashMap<>();
    for (Object candidate : chrChecklist.getChrFeatureIdentities()) {
      ChrFeatureIdentity existing = (ChrFeatureIdentity) candidate;
      if (Long.valueOf(anchorId).equals(existing.getCompositeChrFeatureIdentity())) {
        members.put(existing.getChrFeatureId(), existing);
      }
    }

    List<ChrFeatureIdentity> doomed = new ArrayList<>();
    for (String memberId : deleteMemberIds == null ? List.<String>of() : deleteMemberIds) {
      ChrFeatureIdentity member = members.get(Long.parseLong(memberId));
      if (member == null) {
        throw new InvalidParameterException(
            "Feature " + memberId + " is not a member of composite " + anchorId + ".");
      }
      doomed.add(member);
    }

    Set<Long> survivors = new LinkedHashSet<>(members.keySet());
    for (ChrFeatureIdentity member : doomed) {
      survivors.remove(member.getChrFeatureId());
      chrChecklist.getChrFeatureIdentities().remove(member);
      deleteFeature(member);
    }
    // The anchor last: its delete is what releases whatever is still pointing at it.
    chrChecklist.getChrFeatureIdentities().remove(anchor);
    deleteFeature(anchor);

    chrChecklist.setDeviceCheckoutGuid(null);
    stampChecklistUpdate(chrChecklist, userId);
    entityManager.flush();

    return mapFeaturesAfterWrite(checklistId, survivors, anchorId);
  }

  /**
   * Re-point an existing composite at a new set of members.
   *
   * <p>Three writes, and the asymmetry between them is the whole of this endpoint:
   *
   * <ul>
   *   <li><b>Take.</b> A member may currently sit under a different composite — moving one across
   *       is what the members dialog is for, so unlike {@code createComposite} this does not refuse
   *       an already-grouped feature.
   *   <li><b>Release.</b> A feature that was under <em>this</em> anchor and is no longer named goes
   *       back to standing on its own. Scoped to this anchor's own members on purpose: releasing by
   *       absence alone would strip features out of other groups the request never mentioned.
   *   <li><b>Create.</b> Features typed into the dialog are inserted here, for the same reason they
   *       are on create — the gesture is atomic in the UI.
   * </ul>
   *
   * <p>The other composite a member is taken from needs no write: membership lives on the child
   * row, so re-pointing the member is the entire move.
   *
   * @return the anchor, every member it now holds, and everything it released
   */
  public List<Feature> updateComposite(
      long checklistId,
      long anchorId,
      String featureDescriptionCode,
      String featureInfoSourceCode,
      List<String> memberIds,
      List<Feature> newMembers,
      String userId)
      throws Exception {
    ChrChecklist chrChecklist = entityManager.find(ChrChecklist.class, checklistId);
    if (chrChecklist == null) {
      throw new EntityNotFoundException("Checklist " + checklistId + " was not found.");
    }
    ChrFeatureIdentity anchor = featureOnChecklist(chrChecklist, checklistId, anchorId);
    if (!"Y".equalsIgnoreCase(anchor.getCompositeFeatureInd())) {
      throw new InvalidParameterException("Feature " + anchorId + " is not a composite.");
    }
    List<String> existingIds = memberIds == null ? List.of() : memberIds;
    List<Feature> created = newMembers == null ? List.of() : newMembers;
    if (existingIds.size() + created.size() < 2) {
      throw new InvalidParameterException("A composite needs at least two features.");
    }

    Set<Long> wanted = new LinkedHashSet<>();
    List<ChrFeatureIdentity> members = new ArrayList<>();
    for (String memberId : existingIds) {
      long id = Long.parseLong(memberId);
      if (id == anchorId) {
        throw new InvalidParameterException("A composite cannot be a member of itself.");
      }
      ChrFeatureIdentity member = featureOnChecklist(chrChecklist, checklistId, id);
      if ("Y".equalsIgnoreCase(member.getCompositeFeatureInd())) {
        throw new InvalidParameterException("Feature " + memberId + " is itself a composite.");
      }
      members.add(member);
      wanted.add(id);
    }

    Set<Long> touched = new LinkedHashSet<>(wanted);

    // Release first, and only this anchor's own members.
    for (Object candidate : chrChecklist.getChrFeatureIdentities()) {
      ChrFeatureIdentity existing = (ChrFeatureIdentity) candidate;
      if (Long.valueOf(anchorId).equals(existing.getCompositeChrFeatureIdentity())
          && !wanted.contains(existing.getChrFeatureId())) {
        existing.setCompositeChrFeatureIdentity(null);
        stampFeatureUpdate(existing, userId);
        touched.add(existing.getChrFeatureId());
      }
    }

    for (Feature newMember : created) {
      newMember.setCompositeFeatureInd("false");
      ChrFeatureIdentity inserted = createFeature(chrChecklist, newMember, userId);
      members.add(inserted);
      touched.add(inserted.getChrFeatureId());
    }

    for (ChrFeatureIdentity member : members) {
      member.setCompositeChrFeatureIdentity(anchorId);
      stampFeatureUpdate(member, userId);
    }

    anchor.setChrFeatureClassCode(ChrStringUtils.hasAValue(featureDescriptionCode)
        ? entityManager.find(ChrFeatureClassCode.class, featureDescriptionCode)
        : null);
    stampFeatureUpdate(anchor, userId);
    // The anchor's information source is an xref row, not a column on the identity.
    Feature anchorSource = new Feature();
    anchorSource.setFeatureInfoSourceCode(featureInfoSourceCode);
    saveFeatureInfoSource(anchorSource, anchorId, userId);

    chrChecklist.setDeviceCheckoutGuid(null);
    stampChecklistUpdate(chrChecklist, userId);
    entityManager.flush();

    return mapFeaturesAfterWrite(checklistId, touched, anchorId);
  }

  /**
   * Insert one feature and all nine of its child collections.
   *
   * <p>The new-identity half of pass 1, without the composite back-reference: whoever is creating
   * the feature owns that, and both callers set it after the anchor exists.
   */
  private ChrFeatureIdentity createFeature(
      ChrChecklist chrChecklist, Feature feature, String userId) {
    ChrFeatureIdentity identity = new ChrFeatureIdentity();
    identity.setEntryTimestamp(new Date());
    identity.setUpdateTimestamp(new Date());
    identity.setEntryUserid(userId);
    identity.setUpdateUserid(userId);
    identity.setChrFeatureClassCode(ChrStringUtils.hasAValue(feature.getFeatureDescriptionCode())
        ? entityManager.find(ChrFeatureClassCode.class, feature.getFeatureDescriptionCode())
        : null);
    identity.setComments(feature.getFeatureComment());
    identity.setChrChecklist(chrChecklist);
    identity.setFeatureLabel(feature.getFeatureLabel());
    identity.setCompositeFeatureInd(
        ChrStringUtils.booleanToIndictor(feature.getCompositeFeatureInd()));
    entityManager.persist(identity);
    chrChecklist.getChrFeatureIdentities().add(identity);
    feature.setId(identity.getChrFeatureId().toString());
    long featureId = identity.getChrFeatureId();

    clearIdentityChildren(identity);
    saveFeatureInfoSource(feature, featureId, userId);
    ChrFeatureDetail detail = saveFeatureDetail(feature, identity, userId);
    // saveFeatureDetail sets the owning side only, so a freshly created identity would otherwise
    // hold a null detail for the rest of the transaction. Reading a feature back, and
    // deleteFeature's cascade, both go through this reference. Set here rather than in
    // saveFeatureDetail so the bulk offline path keeps behaving exactly as it does today.
    identity.setChrFeatureDetail(detail);
    clearDetailChildren(detail);
    saveFeatureTypeXrefs(feature, detail, featureId, userId);
    saveFeatureLocationDetails(feature, detail, featureId, userId);
    saveFeatureAgeXrefs(feature, featureId, userId);
    savePlannedStrategies(feature, detail, featureId, userId);
    saveUsedStrategies(feature, detail, featureId, userId);
    saveDamageAgentXrefs(feature, featureId, userId);
    saveWindthrowTreatmentXrefs(feature, featureId, userId);
    return identity;
  }

  /**
   * Save one feature's own fields — the editor's Save.
   *
   * <p>The identity row and all nine per-feature child collections are rewritten, exactly as pass 1
   * of {@code saveFeatures} does for each feature. What it deliberately does <b>not</b> touch:
   *
   * <ul>
   *   <li><b>Composite membership.</b> {@code COMPOSITE_CHR_FEATURE_ID} and
   *       {@code COMPOSITE_FEATURE_IND} are left exactly as stored. The editor cannot change them —
   *       grouping is done from the composite dialog — so writing them here would mean resolving
   *       {@code compositeFeature} from a label, which is the reference style this endpoint set
   *       exists to retire.
   *   <li><b>Associations.</b> Their own endpoint, because a link names two features.
   * </ul>
   *
   * @return the saved feature, re-read so its code associations are populated
   */
  public List<Feature> saveFeature(long checklistId, long featureId, Feature feature, String userId)
      throws Exception {
    ChrChecklist chrChecklist = entityManager.find(ChrChecklist.class, checklistId);
    if (chrChecklist == null) {
      throw new EntityNotFoundException("Checklist " + checklistId + " was not found.");
    }
    ChrFeatureIdentity identity = featureOnChecklist(chrChecklist, checklistId, featureId);

    stampFeatureUpdate(identity, userId);
    // Null included — the row is updated in place, so a conditional setter would keep the previous
    // feature class when the user cleared it. Same reasoning as pass 1.
    identity.setChrFeatureClassCode(ChrStringUtils.hasAValue(feature.getFeatureDescriptionCode())
        ? entityManager.find(ChrFeatureClassCode.class, feature.getFeatureDescriptionCode())
        : null);
    identity.setComments(feature.getFeatureComment());
    identity.setFeatureLabel(feature.getFeatureLabel());
    entityManager.persist(identity);

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

    chrChecklist.setDeviceCheckoutGuid(null);
    stampChecklistUpdate(chrChecklist, userId);
    entityManager.flush();

    return mapFeaturesAfterWrite(checklistId, Set.of(), featureId);
  }

  /**
   * Replace the set of features one feature is associated with, in <b>both</b> directions.
   *
   * <p>{@code CHR_ASSOCIATED_FEATURE_XREF} is directed, and {@link #saveAssociatedFeatures} — the
   * bulk path — only ever rewrites rows where the subject is the <em>from</em> side. Symmetry
   * survived there only because the client put each label in the other feature's list and posted
   * every feature, so each wrote its own half. A per-feature write has no such guarantee, so the
   * server owns the invariant here: every link is written and removed as a pair.
   *
   * <p>Measured in PROD on 2026-09-02, all 802 rows were symmetric — 401 mutual pairs, no
   * exceptions. This endpoint is the thing most able to break that, which is why it does not
   * delegate to the one-sided writer.
   *
   * @return every feature the write touched — the subject plus each partner gained or lost
   */
  public List<Feature> saveFeatureAssociations(
      long checklistId, long featureId, List<String> targetIds, String userId) throws Exception {
    ChrChecklist chrChecklist = entityManager.find(ChrChecklist.class, checklistId);
    if (chrChecklist == null) {
      throw new EntityNotFoundException("Checklist " + checklistId + " was not found.");
    }
    ChrFeatureIdentity subject = featureOnChecklist(chrChecklist, checklistId, featureId);

    // Every target is resolved through the same checklist. A feature id from elsewhere is a
    // not-found, not a licence to link across checklists: PROD holds no such row today, and
    // findFeatureIdentity has always been checklist-scoped.
    Set<Long> wanted = new LinkedHashSet<>();
    for (String targetId : targetIds == null ? List.<String>of() : targetIds) {
      if (!ChrStringUtils.hasAValue(targetId)) {
        continue;
      }
      long target = Long.parseLong(targetId);
      if (target == featureId) {
        throw new InvalidParameterException(
            "Feature " + featureId + " cannot be associated with itself.");
      }
      featureOnChecklist(chrChecklist, checklistId, target);
      wanted.add(target);
    }

    // Everything currently linked either way, so the response can name the partners that were
    // dropped as well as the ones that remain.
    Set<Long> touched = new LinkedHashSet<>(wanted);
    List<ChrAssociatedFeatureXref> existing = entityManager.createQuery(
            "SELECT a FROM ChrAssociatedFeatureXref a "
                + "WHERE a.id.fromChrFeatureId = :fid OR a.id.toChrFeatureId = :fid",
            ChrAssociatedFeatureXref.class)
        .setParameter("fid", featureId)
        .getResultList();
    for (ChrAssociatedFeatureXref row : existing) {
      touched.add(row.getId().getFromChrFeatureId() == featureId
          ? row.getId().getToChrFeatureId()
          : row.getId().getFromChrFeatureId());
      entityManager.remove(row);
    }
    // Detach before the flush: the subject's eager xref sets still hold the rows just removed, and
    // a managed entity pointing at a removed one fails with TransientObjectException.
    clearIdentityChildren(subject);
    entityManager.flush();

    for (Long target : wanted) {
      persistAssociation(featureId, target, userId);
      persistAssociation(target, featureId, userId);
    }

    chrChecklist.setDeviceCheckoutGuid(null);
    stampFeatureUpdate(subject, userId);
    stampChecklistUpdate(chrChecklist, userId);
    entityManager.flush();

    return mapFeaturesAfterWrite(checklistId, touched, featureId);
  }

  private void persistAssociation(long fromId, long toId, String userId) {
    ChrAssociatedFeatureXref xref = new ChrAssociatedFeatureXref();
    xref.setId(new ChrAssociatedFeatureXrefId(fromId, toId));
    stampEntry(xref::setEntryTimestamp, xref::setEntryUserid, userId);
    stampUpdate(xref::setUpdateTimestamp, xref::setUpdateUserid, userId);
    entityManager.persist(xref);
  }

  private void stampFeatureUpdate(ChrFeatureIdentity identity, String userId) {
    identity.setUpdateTimestamp(new Date());
    identity.setUpdateUserid(userId);
  }

  /**
   * Find a feature on this checklist, or fail.
   *
   * <p>Scoped through the checklist's own set on purpose: a feature id belonging to another
   * checklist is a not-found here, not a licence to touch someone else's row — the same rule
   * {@link #deletePhoto} follows.
   */
  private ChrFeatureIdentity featureOnChecklist(
      ChrChecklist chrChecklist, long checklistId, long featureId) {
    // The eager collection is a raw Set (legacy mapping), so iterate and cast rather than stream.
    for (Object candidate : chrChecklist.getChrFeatureIdentities()) {
      ChrFeatureIdentity existing = (ChrFeatureIdentity) candidate;
      if (Long.valueOf(featureId).equals(existing.getChrFeatureId())) {
        return existing;
      }
    }
    throw new EntityNotFoundException(
        "Feature " + featureId + " was not found on checklist " + checklistId + ".");
  }

  /**
   * Re-read and map the features a write touched.
   *
   * <p>The persistence context is dropped first for the same reason {@code saveFeaturesSection}
   * drops it: rows written here carry only their embedded ids, and the code associations the mapper
   * reads are {@code insertable=false}, so a feature mapped from them would come back missing its
   * class and source. Reading after the clear is what populates them.
   */
  private List<Feature> mapFeaturesAfterWrite(long checklistId, Set<Long> touched, long subjectId)
      throws Exception {
    entityManager.flush();
    entityManager.clear();
    ChrChecklist fresh = entityManager.find(ChrChecklist.class, checklistId);
    List<Feature> mapped = new ArrayList<>();
    Set<Long> wanted = new LinkedHashSet<>();
    wanted.add(subjectId);
    wanted.addAll(touched);
    for (Object candidate : fresh.getChrFeatureIdentities()) {
      ChrFeatureIdentity identity = (ChrFeatureIdentity) candidate;
      if (wanted.contains(identity.getChrFeatureId())) {
        mapped.add(CheckListMapper.toFeature(identity, fresh.getChrFeatureIdentities()));
      }
    }
    return mapped;
  }

  /**
   * Remove one feature from a checklist, with everything that hangs off it.
   *
   * <p>The feature is looked up <em>through</em> the checklist's own set rather than by id alone:
   * a feature id belonging to another checklist is a not-found here, not a licence to delete
   * someone else's row — the same rule {@link #deletePhoto} follows.
   *
   * <p>Clears {@code deviceCheckoutGuid} and stamps the checklist exactly as a section save does, so
   * an online delete releases an offline checkout and advances the shared revision token.
   */
  public void deleteFeature(long checklistId, long featureId, String userId) {
    ChrChecklist chrChecklist = entityManager.find(ChrChecklist.class, checklistId);
    if (chrChecklist == null) {
      throw new EntityNotFoundException("Checklist " + checklistId + " was not found.");
    }
    // The eager collection is a raw Set (legacy mapping), so iterate and cast rather than stream.
    ChrFeatureIdentity identity = null;
    for (Object candidate : chrChecklist.getChrFeatureIdentities()) {
      ChrFeatureIdentity existing = (ChrFeatureIdentity) candidate;
      if (Long.valueOf(featureId).equals(existing.getChrFeatureId())) {
        identity = existing;
        break;
      }
    }
    if (identity == null) {
      throw new EntityNotFoundException(
          "Feature " + featureId + " was not found on checklist " + checklistId + ".");
    }

    chrChecklist.setDeviceCheckoutGuid(null);
    // Drop it from the eager set as well as the database: the checklist is still managed, and a
    // removed entity left in a loaded collection fails the flush.
    chrChecklist.getChrFeatureIdentities().remove(identity);
    deleteFeature(identity);
    stampChecklistUpdate(chrChecklist, userId);
    entityManager.flush();
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

    // Set unconditionally, null included. The checklist row is loaded and updated rather than
    // recreated, so skipping the setter on a blank value left the previous rating in place: the tab
    // accepted the clear, the save reported success, and the old rating came back on the re-read.
    chrChecklist.setChrSiteEvaluationCode(ChrStringUtils.hasAValue(resource.getRating())
        ? entityManager.find(ChrSiteEvaluationCode.class, resource.getRating())
        : null);
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


  /**
   * Attach one photo to a checklist: metadata row plus the stored object. Deliberately
   * <b>token-neutral</b> — it does not stamp or flush the parent checklist, so a photo upload never
   * advances the shared {@code revision_count} and can't make a client's in-flight checklist edit
   * conflict. The checklist save stays the only writer of that token.
   *
   * <p>If the object-storage write fails the metadata row is removed again, so a failed upload can't
   * leave a row pointing at bytes that were never stored (mirrors the Biodiversity attachment path).
   *
   * @param featureId optional — the feature this photo documents. Must be a feature of this same
   *                  checklist; anything else is rejected as a bad request rather than left to the
   *                  FK, which would accept another checklist's feature and reject a bogus id with
   *                  a raw constraint violation.
   */
  public ChrChecklistAttachment addPhoto(
      long checklistId, String fileName, String description, String fileDate, Long featureId,
      String mimeTypeCode, byte[] content, String userId) {
    ChrChecklist chrChecklist = entityManager.find(ChrChecklist.class, checklistId);
    if (chrChecklist == null) {
      throw new EntityNotFoundException("Checklist " + checklistId + " was not found.");
    }
    assertFeatureOnChecklist(chrChecklist, checklistId, featureId);
    String mimeType = deriveMimeType(mimeTypeCode).toUpperCase();

    ChrChecklistAttachment attachment = new ChrChecklistAttachment();
    attachment.setChrChecklist(chrChecklist);
    attachment.setChrFeatureId(featureId);
    attachment.setMimeTypeCode(mimeType);
    attachment.setDescription(description);
    attachment.setFileName(FilenameUtils.removeExtension(fileName) + "." + mimeType);
    attachment.setFileDate(parseDate(fileDate));
    attachment.setEntryTimestamp(new Date());
    attachment.setEntryUserid(userId);
    attachment.setUpdateTimestamp(new Date());
    attachment.setUpdateUserid(userId);
    entityManager.persist(attachment);
    // Flush to get the sequence-generated id, which the object key is built from. This writes only
    // the attachment row; no checklist column is touched.
    entityManager.flush();
    // Keeps the loaded collection consistent within this transaction. Note that this alone used to
    // bump the checklist's @Version: Hibernate increments the owner's version whenever an owned
    // collection is dirty, no column change required. ChrChecklist.chrChecklistAttachments is
    // @OptimisticLock(excluded = true) precisely so this line stays token-neutral.
    chrChecklist.getChrChecklistAttachments().add(attachment);

    try {
      objectStorageService.putObject(
          photoObjectKey(checklistId, attachment), mimeTypeCode, content);
    } catch (RuntimeException ex) {
      entityManager.remove(attachment);
      chrChecklist.getChrChecklistAttachments().remove(attachment);
      entityManager.flush();
      throw new FrepApiRuntimeException(
          "Could not store photo " + fileName + "; nothing was saved.", ex);
    }
    return attachment;
  }

  /**
   * A photo may only point at a feature of its own checklist. The FK to CHR_FEATURE_DETAIL is
   * satisfied by <em>any</em> existing feature, so without this a caller could hang a photo off
   * another checklist's feature — and a non-existent id would surface as an Oracle constraint
   * violation rather than a 400.
   */
  private void assertFeatureOnChecklist(ChrChecklist chrChecklist, long checklistId,
      Long featureId) {
    if (featureId == null) {
      return;
    }
    // The mapped collection is a raw Set (legacy mapping), so iterate and cast rather than stream.
    for (Object candidate : chrChecklist.getChrFeatureIdentities()) {
      if (featureId.equals(((ChrFeatureIdentity) candidate).getChrFeatureId())) {
        return;
      }
    }
    throw new InvalidParameterException(
        "Feature " + featureId + " is not a feature of checklist " + checklistId + ".");
  }

  /** Feature id → label for this checklist, so photo metadata can name the feature it points at. */
  private Map<Long, String> featureLabelsById(ChrChecklist chrChecklist) {
    Map<Long, String> labels = new HashMap<>();
    for (Object candidate : chrChecklist.getChrFeatureIdentities()) {
      ChrFeatureIdentity feature = (ChrFeatureIdentity) candidate;
      labels.put(feature.getChrFeatureId(), feature.getFeatureLabel());
    }
    return labels;
  }

  /**
   * The checklist's photo metadata — no bytes, no object-storage reads. Used by submit validation,
   * which must check what the record actually holds rather than what the client sent.
   */
  public List<Picture> getPhotoMetadata(long checklistId) {
    ChrChecklist chrChecklist = entityManager.find(ChrChecklist.class, checklistId);
    if (chrChecklist == null) {
      return List.of();
    }
    // Newest first, by creation time, with the id as tiebreaker.
    //
    // Descending is a UX decision, not an arbitrary one: with ascending order a newly uploaded photo
    // lands on the *last* page, so a user sitting on page 1 sees nothing change after uploading and
    // reasonably concludes it failed. Newest-first puts it where they are looking.
    //
    // The tiebreaker is required, not cosmetic — entry_timestamp is an Oracle DATE (second
    // precision), so photos added in the same second would otherwise page non-deterministically,
    // repeating on one page and vanishing from another. The mapped collection is an unordered Set,
    // so the sort happens here rather than in a query.
    List<ChrChecklistAttachment> ordered = new ArrayList<>();
    for (Object candidate : chrChecklist.getChrChecklistAttachments()) {
      ordered.add((ChrChecklistAttachment) candidate);
    }
    ordered.sort(Comparator
        .comparing(ChrChecklistAttachment::getEntryTimestamp,
            Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(ChrChecklistAttachment::getChrchecklistAttachmentId,
            Comparator.nullsLast(Comparator.reverseOrder())));

    Map<Long, String> featureLabels = featureLabelsById(chrChecklist);
    List<Picture> pictures = new ArrayList<>();
    for (ChrChecklistAttachment attachment : ordered) {
      Picture picture = new Picture();
      picture.setId(String.valueOf(attachment.getChrchecklistAttachmentId()));
      picture.setDescription(attachment.getDescription());
      picture.setFileName(attachment.getFileName());
      picture.setMimeTypeCode("image/" + attachment.getMimeTypeCode().toLowerCase());
      if (attachment.getChrFeatureId() != null) {
        picture.setFeatureId(String.valueOf(attachment.getChrFeatureId()));
        // Null when the feature was deleted out from under the photo — the id still round-trips.
        picture.setFeatureLabel(featureLabels.get(attachment.getChrFeatureId()));
      }
      try {
        picture.setDate(ChrDateUtils.formatDate(attachment.getFileDate()));
      } catch (ParseException ex) {
        // A malformed stored date must not block submit, which validates descriptions only.
        log.warn("Could not format file date for photo {}",
            attachment.getChrchecklistAttachmentId(), ex);
      }
      pictures.add(picture);
    }
    return pictures;
  }

  /** Remove one photo: its metadata row and its stored object. Token-neutral, as {@link #addPhoto}. */
  public void deletePhoto(long checklistId, long photoId, String userId) {
    ChrChecklist chrChecklist = entityManager.find(ChrChecklist.class, checklistId);
    if (chrChecklist == null) {
      throw new EntityNotFoundException("Checklist " + checklistId + " was not found.");
    }
    // The eager collection is a raw Set (legacy mapping), so iterate and cast rather than stream.
    ChrChecklistAttachment attachment = null;
    for (Object candidate : chrChecklist.getChrChecklistAttachments()) {
      ChrChecklistAttachment existing = (ChrChecklistAttachment) candidate;
      if (Long.valueOf(photoId).equals(existing.getChrchecklistAttachmentId())) {
        attachment = existing;
        break;
      }
    }
    if (attachment == null) {
      // Scoped to the checklist on purpose: a photo id from another checklist is a not-found here,
      // not a licence to delete someone else's row.
      throw new EntityNotFoundException(
          "Photo " + photoId + " was not found on checklist " + checklistId + ".");
    }

    String key = photoObjectKey(checklistId, attachment);
    chrChecklist.getChrChecklistAttachments().remove(attachment);
    entityManager.remove(attachment);
    entityManager.flush();
    // Best-effort: a stray object with no row is invisible to the app, whereas failing here would
    // leave the row behind after the user was told the photo was deleted.
    try {
      objectStorageService.deleteObject(key);
    } catch (RuntimeException ex) {
      log.warn("Removed photo {} from checklist {} but could not delete object {}",
          photoId, checklistId, key, ex);
    }
  }

  /**
   * Object key for a stored photo: {@code {checklistId}-{attachmentId}.{ext}} — the same key
   * {@code populatePhotoBytes} reads back with.
   */
  private String photoObjectKey(long checklistId, ChrChecklistAttachment attachment) {
    return checklistId + "-" + attachment.getChrchecklistAttachmentId() + "."
        + deriveMimeType(attachment.getMimeTypeCode());
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
      // Null included — an existing identity row is updated in place, so a conditional setter would
      // keep the previous feature class when the user cleared it. See the note in
      // applyBlockSummaryFields.
      identity.setChrFeatureClassCode(ChrStringUtils.hasAValue(feature.getFeatureDescriptionCode())
          ? entityManager.find(ChrFeatureClassCode.class, feature.getFeatureDescriptionCode())
          : null);
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
    String onFeature = " for feature " + feature.getFeatureLabel();
    detail.setAreaWidthMeters(toBigDecimal(feature.getWidthofFeature(), "Width (m)" + onFeature, 6));
    detail.setAreaLengthMeters(toBigDecimal(feature.getLengthofFeature(), "Length (m)" + onFeature, 6));
    detail.setAreaHectares(toBigDecimal(feature.getAreaofFeature(), "Area (ha)" + onFeature, 6));
    detail.setFnMgmtRecommendationsInd(ChrStringUtils.booleanToIndictor(feature.getManagementStrategyFN()));
    detail.setSitePlanStratsRecommndInd(ChrStringUtils.booleanToIndictor(feature.getManagementStrategySP()));
    detail.setPermitIssuedInd(ChrStringUtils.booleanToIndictorInverseLogic(feature.getSitePermitIssued()));
    detail.setBordenNo(feature.getBorden());
    detail.setPermitNumber(feature.getPermit());
    detail.setEvidenceOfDamageInd(
        ChrStringUtils.booleanToIndictor(feature.getQ1Isthereevidenceofdamagetothesiteorfeature()));
    detail.setDamageDescription(feature.getDescriptionofdamage());
    // DAMAGE_IRREVERSIBLE_ANSWER_CD is NOT NULL, so an unanswered Q3 still has to be written as
    // something and "N" is the least-claiming code available. Reached only when the answer is
    // genuinely absent or unrecognised — `find` is not called with a null key, which throws.
    String q3 = feature.getQ3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse();
    FrepChecklistAnswerCode answer =
        q3 == null || q3.isBlank() ? null : entityManager.find(FrepChecklistAnswerCode.class, q3);
    if (answer == null) {
      answer = entityManager.find(FrepChecklistAnswerCode.class, "N");
    }
    detail.setDamageIrreversibleAnswerCd(answer);
    detail.setWindthrowMgmtApplicableInd(ChrStringUtils.booleanToIndictor(feature.getWindthrowManagement()));
    detail.setAreaWindfirmInd(ChrStringUtils.booleanToIndictor(feature.getWindthrow()));
    detail.setEstWindthrowPercent(
        toShort(feature.getEstwindthrow(), "Estimated windthrow (%)" + onFeature, 999));
    detail.setTrailFeaturesApplicableInd(ChrStringUtils.booleanToIndictor(feature.getTrailfeatures()));
    detail.setTrailLocatableInd(ChrStringUtils.booleanToIndictor(feature.getCanthetrailstillbelocated()));
    detail.setTrailLessPassableInd(ChrStringUtils.booleanToIndictor(feature.getHasthetrailbeenmadelesspassble()));
    detail.setTrailAreaDamagedInd(ChrStringUtils.booleanToIndictor(feature.getIsthereevidenceofdamage()));
    detail.setEstTrailDamagePercent(
        toShort(feature.getTrailLength(), "Estimated trail damage (%)" + onFeature, 999));
    detail.setLimitingOperatnlFactorsInd(ChrStringUtils.booleanToIndictor(
        feature.getQ4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature()));
    detail.setLimitingOperatnlFactorsDesc(feature.getQ4Description());
    detail.setEffectiveStratsUsedInd(ChrStringUtils.booleanToIndictor(
        feature.getQ5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective()));
    detail.setEffectiveStratsUsedDesc(feature.getQ5Description());
    detail.setAlternateStratsAvailInd(ChrStringUtils.booleanToIndictor(
        feature.getQ6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature()));
    detail.setAlternateStratsAvailDesc(feature.getQ6Description());
    detail.setChrSiteEvaluationCode(ChrStringUtils.hasAValue(feature.getFeatureRating())
        ? entityManager.find(ChrSiteEvaluationCode.class, feature.getFeatureRating())
        : null);
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
      // Evict the row from BOTH endpoint features' EAGER xref collections before removing it. The
      // *retained* sibling (the endpoint that isn't being deleted) is still managed, and would
      // otherwise reference a removed row at flush time -> TransientObjectException. Mirrors the same
      // eager-inverse-set clearing already done for chrFeatureIdentities / participations /
      // attachments. (The deleted feature's own collections were already emptied by
      // clearIdentityChildren, so those removals are no-ops.)
      evictAssociatedXrefFromIdentities(row);
      entityManager.remove(row);
    }
    entityManager.flush();
  }

  /**
   * Drops an associated-feature xref from the eager collections of its {@code from} and {@code to}
   * feature identities, keeping the managed graph consistent with the row's removal before flush.
   */
  private void evictAssociatedXrefFromIdentities(ChrAssociatedFeatureXref row) {
    ChrFeatureIdentity fromIdentity =
        entityManager.find(ChrFeatureIdentity.class, row.getId().getFromChrFeatureId());
    if (fromIdentity != null) {
      fromIdentity.getChrAssociatedFeatureXrefsForFromChrFeatureId().remove(row);
    }
    ChrFeatureIdentity toIdentity =
        entityManager.find(ChrFeatureIdentity.class, row.getId().getToChrFeatureId());
    if (toIdentity != null) {
      toIdentity.getChrAssociatedFeatureXrefsForToChrFeatureId().remove(row);
    }
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

  /**
   * Parse a decimal feature field, naming the field when the value cannot be stored. Blank becomes
   * null, for the same reason as {@link #toShort}.
   *
   * <p>Digits after the point are left alone: Oracle rounds them to the column's scale, and refusing
   * a row over a rounding nicety would reject data the database would have taken. Digits before it
   * are checked, because past the column's precision the insert fails with ORA-01438 — which reaches
   * the caller as a database error naming neither the field nor the value.
   */
  private BigDecimal toBigDecimal(String value, String label, int units) {
    if (!ChrStringUtils.hasAValue(value)) {
      return null;
    }
    String entered = value.trim();
    BigDecimal parsed;
    try {
      parsed = new BigDecimal(entered);
    } catch (NumberFormatException ex) {
      throw new InvalidParameterException(
          label + " must be a number (received \"" + entered + "\").");
    }
    if (parsed.signum() < 0 || parsed.precision() - parsed.scale() > units) {
      throw new InvalidParameterException(
          label + " must be from 0 to " + "9".repeat(units) + " (received \"" + entered + "\").");
    }
    return parsed;
  }

  /**
   * Parse a whole-number feature field, naming the field when the value cannot be stored.
   *
   * <p>A blank value becomes null rather than being skipped, so clearing a number clears the column.
   * The detail row is loaded and updated in place, so anything not written keeps its old value.
   *
   * <p>{@link NumberFormatException} is an {@link IllegalArgumentException}, which the REST exception
   * handler answers as a bad request carrying the exception's own text — so an entry of "tset" used
   * to reach the user as {@code For input string: "tset"}, naming neither the field nor the feature.
   * The feature editor blocks these before the save now; this is the backstop for the offline
   * check-in path, which reaches the same code with no editor in front of it.
   *
   * <p>{@code max} is the column's range rather than the form's: the editor caps a percentage at 100,
   * while anything a {@code NUMBER(3)} column holds is accepted here, so a legacy row carrying a
   * larger value can still be saved. Without the check a five-digit entry parses and then fails at
   * insert with ORA-01438, naming neither the field nor the value.
   */
  private Short toShort(String value, String label, int max) {
    if (!ChrStringUtils.hasAValue(value)) {
      return null;
    }
    String entered = value.trim();
    // Parsed wide, then range-checked: Short.parseShort reports a five-digit entry as a malformed
    // number, which is the wrong thing to tell someone who typed a number that is merely too big.
    long parsed;
    try {
      parsed = Long.parseLong(entered);
    } catch (NumberFormatException ex) {
      throw new InvalidParameterException(
          label + " must be a whole number (received \"" + entered + "\").");
    }
    if (parsed < 0 || parsed > max) {
      throw new InvalidParameterException(
          label + " must be from 0 to " + max + " (received \"" + entered + "\").");
    }
    return (short) parsed;
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

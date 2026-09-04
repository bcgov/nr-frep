package ca.bc.gov.nrs.frep.service.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import ca.bc.gov.nrs.frep.entity.ChrChecklistAttachment;
import ca.bc.gov.nrs.frep.struct.v1.frep.Picture;
import java.time.Instant;
import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
import ca.bc.gov.nrs.frep.struct.v1.frep.Feature;
import ca.bc.gov.nrs.frep.entity.ChrAssociatedFeatureXref;
import ca.bc.gov.nrs.frep.entity.ChrAssociatedFeatureXrefId;
import ca.bc.gov.nrs.frep.entity.ChrChecklist;
import ca.bc.gov.nrs.frep.entity.ChrFeatureAgeXref;
import ca.bc.gov.nrs.frep.entity.ChrFeatureDetail;
import ca.bc.gov.nrs.frep.entity.ChrFeatureIdentity;
import ca.bc.gov.nrs.frep.entity.ChrFeatureTypeXref;
import ca.bc.gov.nrs.frep.util.ChrStringUtils;
import ca.bc.gov.nrs.frep.entity.FrepChecklistAnswerCode;
import ca.bc.gov.nrs.frep.entity.FrepChecklistStatusCode;
import ca.bc.gov.nrs.frep.entity.FrepResourceValue;
import ca.bc.gov.nrs.frep.entity.FrepResourceValueStatCode;
import ca.bc.gov.nrs.frep.exception.EntityNotFoundException;
import ca.bc.gov.nrs.frep.exception.InvalidParameterException;
import ca.bc.gov.nrs.frep.service.v1.ObjectStorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import ca.bc.gov.nrs.frep.entity.ChrFeatureClassCode;
import ca.bc.gov.nrs.frep.entity.ChrSiteEvaluationCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Verifies the {@code saveFeatures} port against a mocked {@link EntityManager}: feature identity
 * and detail are persisted, the generated id is written back to the DTO, inverse-logic indicators
 * are applied, and code-keyed xrefs are created. (Real Oracle round-trip is verified in dev.)
 */
class ChrChecklistPersistenceServiceTest {

  private EntityManager entityManager;
  private ObjectStorageService objectStorage;
  private ChrChecklist checklist;
  private ChrChecklistPersistenceService service;
  private final List<Object> persisted = new ArrayList<>();

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    entityManager = mock(EntityManager.class);
    objectStorage = mock(ObjectStorageService.class);
    service = new ChrChecklistPersistenceService(objectStorage);
    ReflectionTestUtils.setField(service, "entityManager", entityManager);

    // Managed checklist returned by find(...).
    checklist = new ChrChecklist();
    checklist.setChrChecklistId(1001L);
    FrepResourceValueStatCode statCode = new FrepResourceValueStatCode();
    statCode.setFrepResourceValueStatCode("ACC");
    FrepResourceValue resourceValue = new FrepResourceValue();
    resourceValue.setFrepResourceValueStatCode(statCode);
    checklist.setFrepResourceValue(resourceValue);
    checklist.setChrChecklistAttachments(new HashSet<>());

    when(entityManager.find(ChrChecklist.class, 1001L)).thenReturn(checklist);
    lenient().when(entityManager.find(eq(FrepChecklistStatusCode.class), any()))
        .thenReturn(new FrepChecklistStatusCode());
    // DAMAGE_IRREVERSIBLE_ANSWER_CD is NOT NULL: an unanswered Q3 falls back to the "N" code row,
    // which the mapper then dereferences. The code table has it; the mock has to as well.
    FrepChecklistAnswerCode answerCode = new FrepChecklistAnswerCode();
    answerCode.setFrepChecklistAnswerCode("N");
    lenient().when(entityManager.find(eq(FrepChecklistAnswerCode.class), any()))
        .thenReturn(answerCode);
    // All other find(...) lookups (code tables, existing detail) resolve to null.
    lenient().when(entityManager.find(eq(ChrFeatureDetail.class), any())).thenReturn(null);

    // Typed query: existing feature identities for the checklist -> none.
    TypedQuery<Object> typedQuery = mock(TypedQuery.class);
    lenient().when(entityManager.createQuery(anyString(), any(Class.class))).thenReturn(typedQuery);
    lenient().when(typedQuery.setParameter(anyString(), any())).thenReturn(typedQuery);
    lenient().when(typedQuery.getResultList()).thenReturn(List.of());

    // Untyped query: xref/strategy delete sweeps -> none.
    Query query = mock(Query.class);
    lenient().when(entityManager.createQuery(anyString())).thenReturn(query);
    lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
    lenient().when(query.getResultList()).thenReturn(List.of());

    // persist(...) captures entities and assigns the sequence id Hibernate would generate.
    doAnswer(invocation -> {
      Object entity = invocation.getArgument(0);
      if (entity instanceof ChrFeatureIdentity identity && identity.getChrFeatureId() == null) {
        identity.setChrFeatureId(5000L);
      }
      persisted.add(entity);
      return null;
    }).when(entityManager).persist(any());
  }

  @Test
  void persistsFeatureIdentityDetailAndXrefsWithInverseIndicators() {
    Feature feature = new Feature();
    feature.setFeatureLabel("1");
    feature.setCompositeFeatureInd("false");
    feature.setUnabletoLocate("true");   // inverse logic -> featureLocatedInd = "N"
    feature.setNoManagement("false");    // inverse logic -> managementAppliedInd = "Y"
    feature.setPre1846("true");          // -> ChrFeatureAgeXref code PRE1846
    feature.setBurialSite("true");       // -> ChrFeatureTypeXref code BURIALSITE

    CheckList resource = new CheckList();
    resource.setChecklistID("1001");
    resource.setStatus("ACT");
    resource.setEvaluationDate("2026-05-01");
    resource.setFeatures(new ArrayList<>(List.of(feature)));

    service.saveChecklist(resource, "IDIR\\tester");

    assertTrue(persisted.stream().anyMatch(o -> o instanceof ChrFeatureIdentity),
        "feature identity should be persisted");
    assertEquals("5000", feature.getId(), "generated id should be written back to the DTO");

    ChrFeatureDetail detail = persisted.stream()
        .filter(ChrFeatureDetail.class::isInstance)
        .map(ChrFeatureDetail.class::cast)
        .findFirst()
        .orElseThrow();
    assertEquals("N", detail.getFeatureLocatedInd(), "unabletoLocate=true -> featureLocatedInd=N");
    assertEquals("Y", detail.getManagementAppliedInd(), "noManagement=false -> managementAppliedInd=Y");

    assertTrue(persisted.stream().anyMatch(o -> o instanceof ChrFeatureAgeXref age
            && "PRE1846".equals(age.getId().getChrFeatureAgeCode())),
        "checked age PRE1846 should create an age xref");
    assertTrue(persisted.stream().anyMatch(o -> o instanceof ChrFeatureTypeXref type
            && "BURIALSITE".equals(type.getId().getChrFeatureTypeCode())),
        "checked type BURIALSITE should create a type xref");
  }

  /**
   * Regression for the save that answered with the JVM's own parse failure.
   *
   * <p>{@code NumberFormatException} is an {@link IllegalArgumentException}, which the REST exception
   * handler answers as a bad request carrying the exception's text — so a percentage typed as "tset"
   * reached the user as {@code For input string: "tset"}, naming neither the field nor the feature.
   * The feature editor blocks this before the save now, but the offline check-in path reaches the
   * same code with no editor in front of it.
   */
  @Test
  void aNonNumericPercentageIsRefusedByNameRatherThanAsAParseFailure() {
    InvalidParameterException ex = assertThrows(InvalidParameterException.class,
        () -> saveWith(f -> f.setTrailLength("tset")));

    assertTrue(ex.getMessage().contains("Estimated trail damage (%) for feature 1"), ex.getMessage());
    assertTrue(ex.getMessage().contains("tset"), ex.getMessage());
  }

  /**
   * A number too wide for its column, told apart from a malformed one. {@code EST_WINDTHROW_PERCENT}
   * is {@code NUMBER(3)}: five digits parse as a Short only to fail at insert with ORA-01438, and
   * "must be a whole number" would be the wrong thing to tell someone who typed a number.
   */
  @Test
  void aPercentageWiderThanItsColumnIsRefusedBeforeTheInsert() {
    InvalidParameterException ex = assertThrows(InvalidParameterException.class,
        () -> saveWith(f -> f.setEstwindthrow("40000")));

    assertTrue(ex.getMessage().contains("must be from 0 to 999"), ex.getMessage());
  }

  /** Same for the decimal side: past the precision of {@code AREA_HECTARES}, ORA-01438 at insert. */
  @Test
  void anAreaWiderThanItsColumnIsRefusedBeforeTheInsert() {
    InvalidParameterException ex = assertThrows(InvalidParameterException.class,
        () -> saveWith(f -> f.setAreaofFeature("12345678.5")));

    assertTrue(ex.getMessage().contains("Area (ha) for feature 1"), ex.getMessage());
  }

  /** Decimals within the column's scale are ordinary values, not errors. */
  @Test
  void anAreaWithinItsColumnIsStored() {
    saveWith(f -> f.setAreaofFeature("2.5"));

    ChrFeatureDetail detail = persisted.stream()
        .filter(ChrFeatureDetail.class::isInstance)
        .map(ChrFeatureDetail.class::cast)
        .findFirst()
        .orElseThrow();
    assertEquals(new BigDecimal("2.5"), detail.getAreaHectares());
  }

  /** One feature, saved through the section port, with whatever the caller sets on it. */
  private void saveWith(java.util.function.Consumer<Feature> setUp) {
    Feature feature = new Feature();
    feature.setFeatureLabel("1");
    feature.setCompositeFeatureInd("false");
    setUp.accept(feature);

    CheckList resource = new CheckList();
    resource.setChecklistID("1001");
    resource.setStatus("ACT");
    resource.setEvaluationDate("2026-05-01");
    resource.setFeatures(new ArrayList<>(List.of(feature)));

    service.saveChecklist(resource, "IDIR\\tester");
  }

  /**
   * Regression for values that could be entered but never removed.
   *
   * <p>An existing feature's {@code CHR_FEATURE_DETAIL} row (and its identity row, and the checklist
   * row) is loaded and updated in place rather than recreated, so a setter guarded by
   * "only when the payload has a value" silently kept the previous value whenever the user cleared
   * the field. The tab accepted the edit and the save reported success, but the old number or code
   * came back on the next read. Every column involved is nullable, so a blank is written as NULL.
   */
  @Test
  void clearingANumberOrCodeOnAnExistingFeatureWritesNullRatherThanKeepingTheOldValue() {
    ChrFeatureIdentity existingIdentity = new ChrFeatureIdentity();
    existingIdentity.setChrFeatureId(7001L);
    existingIdentity.setChrFeatureClassCode(new ChrFeatureClassCode());
    lenient().when(entityManager.find(eq(ChrFeatureIdentity.class), any()))
        .thenReturn(existingIdentity);

    ChrFeatureDetail existingDetail = new ChrFeatureDetail();
    existingDetail.setAreaHectares(new BigDecimal("2.5000"));
    existingDetail.setEstWindthrowPercent((short) 40);
    existingDetail.setEstTrailDamagePercent((short) 15);
    existingDetail.setChrSiteEvaluationCode(new ChrSiteEvaluationCode());
    lenient().when(entityManager.find(eq(ChrFeatureDetail.class), any()))
        .thenReturn(existingDetail);

    // Everything the user could clear, cleared.
    Feature feature = new Feature();
    feature.setId("7001");
    feature.setFeatureLabel("1");
    feature.setCompositeFeatureInd("false");
    feature.setFeatureDescriptionCode("");
    feature.setAreaofFeature("");
    feature.setEstwindthrow("");
    feature.setTrailLength("");
    feature.setFeatureRating("");

    CheckList resource = new CheckList();
    resource.setChecklistID("1001");
    resource.setStatus("ACT");
    resource.setEvaluationDate("2026-05-01");
    resource.setRating("");
    resource.setFeatures(new ArrayList<>(List.of(feature)));

    service.saveChecklist(resource, "IDIR\\tester");

    assertNull(existingDetail.getAreaHectares(), "cleared area should be nulled");
    assertNull(existingDetail.getEstWindthrowPercent(), "cleared windthrow % should be nulled");
    assertNull(existingDetail.getEstTrailDamagePercent(), "cleared trail damage % should be nulled");
    assertNull(existingDetail.getChrSiteEvaluationCode(), "cleared feature rating should be nulled");
    assertNull(existingIdentity.getChrFeatureClassCode(), "cleared feature class should be nulled");
    assertNull(checklist.getChrSiteEvaluationCode(), "cleared block rating should be nulled");
  }

  /**
   * Regression for the submit round-trip that came back claiming the feature types, ages and
   * information source had been wiped.
   *
   * <p>{@link ChrChecklistPersistenceService#saveChecklist} rewrites the feature child xrefs by
   * delete-then-reinsert, and the new rows carry only their embedded ids — the code associations the
   * mapper reads are declared {@code insertable = false}. Submit saves and then re-reads the
   * checklist in the same transaction to return it, so without dropping the persistence context that
   * re-read mapped the cached rows and produced nulls: the flushed rows were correct, but the
   * response said three required feature answers were missing. The per-section feature save already
   * clears for exactly this reason; the whole-checklist save has to as well.
   */
  @Test
  void aChecklistSaveDropsThePersistenceContextSoAReReadSeesTheFlushedXrefs() {
    Feature feature = new Feature();
    feature.setFeatureLabel("1");
    feature.setCompositeFeatureInd("false");
    feature.setPre1846("true");
    feature.setBurialSite("true");

    CheckList resource = new CheckList();
    resource.setChecklistID("1001");
    resource.setStatus("ACT");
    resource.setEvaluationDate("2026-05-01");
    resource.setFeatures(new ArrayList<>(List.of(feature)));

    service.saveChecklist(resource, "IDIR\\tester");

    InOrder inOrder = inOrder(entityManager);
    inOrder.verify(entityManager).flush();
    inOrder.verify(entityManager).clear();
  }

  /**
   * Regression for the composite-removal ORA-02292: when a composite feature is removed, every member
   * feature that grouped under it (self-FK {@code COMPOSITE_CHR_FEATURE_ID}) must have that reference
   * nulled before the composite row is deleted, otherwise the parent delete hits the FK.
   */
  @Test
  @SuppressWarnings("unchecked")
  void removingCompositeDetachesMemberBackReferencesBeforeDelete() {
    // Existing composite feature — absent from the payload below, so it gets deleted.
    ChrFeatureIdentity composite = new ChrFeatureIdentity();
    composite.setChrFeatureId(7000L);
    // Member feature grouped under the composite via the self-FK.
    ChrFeatureIdentity member = new ChrFeatureIdentity();
    member.setChrFeatureId(8000L);
    member.setCompositeChrFeatureIdentity(7000L);

    // existing-identities sweep -> [composite]; detach query -> [member].
    stubFeatureIdentityQuery("fi.chrChecklist.chrChecklistId", List.of(composite));
    stubFeatureIdentityQuery("fi.compositeChrFeatureIdentity", List.of(member));

    CheckList resource = new CheckList();
    resource.setChecklistID("1001");
    resource.setStatus("ACT");
    resource.setEvaluationDate("2026-05-01");
    resource.setFeatures(new ArrayList<>()); // composite removed, nothing re-added

    service.saveChecklist(resource, "IDIR\\tester");

    assertNull(member.getCompositeChrFeatureIdentity(),
        "member COMPOSITE_CHR_FEATURE_ID must be cleared before the composite is deleted (FK safety)");
    verify(entityManager).remove(composite);
  }

  /**
   * Regression for the associated-feature {@code TransientObjectException}: deleting a feature that is
   * linked to a *retained* sibling must evict the shared {@code CHR_ASSOCIATED_FEATURE_XREF} row from
   * the sibling's EAGER collection before the row is removed + flushed — otherwise the managed sibling
   * still references a removed row at flush time and Hibernate raises the exception.
   */
  @Test
  @SuppressWarnings("unchecked")
  void deletingAnAssociatedFeatureEvictsTheSharedXrefFromTheRetainedSibling() {
    // Feature being deleted (absent from the payload below).
    ChrFeatureIdentity deleted = new ChrFeatureIdentity();
    deleted.setChrFeatureId(8000L);
    // Retained sibling associated with the deleted feature.
    ChrFeatureIdentity retained = new ChrFeatureIdentity();
    retained.setChrFeatureId(9000L);

    // Shared association row (from = retained, to = deleted), held by the retained sibling's eager
    // "from" collection.
    ChrAssociatedFeatureXref xref = new ChrAssociatedFeatureXref();
    xref.setId(new ChrAssociatedFeatureXrefId(9000L, 8000L));
    retained.getChrAssociatedFeatureXrefsForFromChrFeatureId().add(xref);

    stubFeatureIdentityQuery("fi.chrChecklist.chrChecklistId", List.of(deleted));
    stubFeatureIdentityQuery("fi.compositeChrFeatureIdentity", List.of());
    stubAssociatedXrefQuery(List.of(xref));
    when(entityManager.find(ChrFeatureIdentity.class, 9000L)).thenReturn(retained);
    when(entityManager.find(ChrFeatureIdentity.class, 8000L)).thenReturn(deleted);

    CheckList resource = new CheckList();
    resource.setChecklistID("1001");
    resource.setStatus("ACT");
    resource.setEvaluationDate("2026-05-01");
    resource.setFeatures(new ArrayList<>()); // deleted feature removed, nothing re-added

    service.saveChecklist(resource, "IDIR\\tester");

    assertFalse(retained.getChrAssociatedFeatureXrefsForFromChrFeatureId().contains(xref),
        "the shared xref must be evicted from the retained sibling's eager collection before flush");
    verify(entityManager).remove(xref);
  }

  /**
   * Regression for the "uncombine doesn't detach" gap: saving a feature with no composite must clear
   * its {@code COMPOSITE_CHR_FEATURE_ID} (the legacy port only ever *set* it, leaving a stale link).
   */
  @Test
  void savingFeatureWithoutCompositeClearsTheCompositeLink() {
    Feature feature = new Feature();
    feature.setFeatureLabel("1");
    feature.setCompositeFeatureInd("false"); // no composite — link must end up null

    CheckList resource = new CheckList();
    resource.setChecklistID("1001");
    resource.setStatus("ACT");
    resource.setEvaluationDate("2026-05-01");
    resource.setFeatures(new ArrayList<>(List.of(feature)));

    service.saveChecklist(resource, "IDIR\\tester");

    ChrFeatureIdentity persistedIdentity = persisted.stream()
        .filter(ChrFeatureIdentity.class::isInstance)
        .map(ChrFeatureIdentity.class::cast)
        .findFirst()
        .orElseThrow();
    assertNull(persistedIdentity.getCompositeChrFeatureIdentity(),
        "feature with no composite should persist a null composite link");
  }

  /** Stubs the {@code ChrFeatureIdentity} typed query whose JPQL contains the given fragment. */
  @SuppressWarnings("unchecked")
  private void stubFeatureIdentityQuery(String jpqlFragment, List<ChrFeatureIdentity> result) {
    TypedQuery<ChrFeatureIdentity> q = mock(TypedQuery.class);
    when(entityManager.createQuery(contains(jpqlFragment), eq(ChrFeatureIdentity.class))).thenReturn(q);
    lenient().when(q.setParameter(anyString(), any())).thenReturn(q);
    when(q.getResultList()).thenReturn(result);
  }

  @SuppressWarnings("unchecked")
  private void stubAssociatedXrefQuery(List<ChrAssociatedFeatureXref> result) {
    TypedQuery<ChrAssociatedFeatureXref> q = mock(TypedQuery.class);
    when(entityManager.createQuery(anyString(), eq(ChrAssociatedFeatureXref.class))).thenReturn(q);
    lenient().when(q.setParameter(anyString(), any())).thenReturn(q);
    when(q.getResultList()).thenReturn(result);
  }

  // ── Photos must survive a checklist save (regression) ────────────────
  //
  // The photo write path is being split out to dedicated per-photo endpoints. Until this test
  // passed, savePictures ran inside every checklist save and reconciled the *whole* picture set:
  // rows absent from the payload were deleted, and an empty upload list made syncChecklistPhotos
  // delete every object under the checklist's S3 prefix. Once photo metadata left the JSON contract
  // the save would carry no pictures — so an ordinary save, an offline check-in, or a submit would
  // silently destroy every photo, DB row and stored bytes alike, with no way to recover the bytes.
  //
  // Submit and offline check-in both funnel through this same persistence-level saveChecklist, so
  // one invariant covers all three entry points: a checklist save must not touch photos at all.

  /** Give the managed checklist one existing photo, as a save would find in the database. */
  private ChrChecklistAttachment givenAnExistingPhoto() {
    ChrChecklistAttachment photo = new ChrChecklistAttachment();
    photo.setChrchecklistAttachmentId(77L);
    photo.setFileName("site.JPG");
    photo.setDescription("Existing site photo");
    photo.setChrChecklist(checklist);
    checklist.getChrChecklistAttachments().add(photo);
    return photo;
  }

  private static CheckList aChecklistSaveWithNoPictures() {
    CheckList resource = new CheckList();
    resource.setChecklistID("1001");
    resource.setStatus("ACT");
    resource.setEvaluationDate("2026-05-01");
    return resource; // pictures == null, exactly what a post-split payload carries
  }

  @Test
  void aChecklistSaveDoesNotDeleteExistingPhotoRows() {
    ChrChecklistAttachment photo = givenAnExistingPhoto();

    service.saveChecklist(aChecklistSaveWithNoPictures(), "IDIR\\tester");

    verify(entityManager, never()).remove(photo);
    assertTrue(checklist.getChrChecklistAttachments().contains(photo),
        "the photo must still be attached to the checklist after an unrelated save");
  }

  @Test
  void aChecklistSaveDoesNotTouchObjectStorage() {
    givenAnExistingPhoto();

    service.saveChecklist(aChecklistSaveWithNoPictures(), "IDIR\\tester");

    // Photo bytes are written and deleted only by the dedicated photo endpoints. A checklist save
    // reaching object storage at all is the bug: the delete is out-of-transaction and unrecoverable.
    verifyNoInteractions(objectStorage);
  }

  // ── Photo operations must not advance the checklist's optimistic-lock token ──
  //
  // revision_count is a JPA @Version shared by every tab. If a photo upload stamped or flushed the
  // checklist entity, a user editing another tab would have their next save rejected as "modified by
  // another user" purely because they added a photo. The checklist save stays the sole token writer.

  @Test
  void addingAPhotoDoesNotStampTheChecklist() {
    Date before = new Date(0);
    checklist.setUpdateTimestamp(before);
    checklist.setUpdateUserid("ORIGINAL");

    service.addPhoto(1001L, "site.jpg", "A description", null, null, "image/jpeg",
        new byte[] {1, 2, 3}, "IDIR\\tester");

    assertEquals(before, checklist.getUpdateTimestamp(),
        "a photo upload must not restamp the parent checklist");
    assertEquals("ORIGINAL", checklist.getUpdateUserid());
  }

  @Test
  void deletingAPhotoDoesNotStampTheChecklist() {
    ChrChecklistAttachment photo = givenAnExistingPhoto();
    photo.setMimeTypeCode("JPG");
    Date before = new Date(0);
    checklist.setUpdateTimestamp(before);
    checklist.setUpdateUserid("ORIGINAL");

    service.deletePhoto(1001L, photo.getChrchecklistAttachmentId(), "IDIR\\tester");

    assertEquals(before, checklist.getUpdateTimestamp());
    assertEquals("ORIGINAL", checklist.getUpdateUserid());
  }

  @Test
  void deletingAPhotoRemovesItsStoredObjectByExactKey() {
    // The key must match what populatePhotoBytes reads back with. A prefix-based delete is what made
    // the old syncChecklistPhotos able to take out a neighbouring checklist's photos.
    ChrChecklistAttachment photo = givenAnExistingPhoto();
    photo.setMimeTypeCode("JPG");

    service.deletePhoto(1001L, photo.getChrchecklistAttachmentId(), "IDIR\\tester");

    verify(objectStorage).deleteObject("1001-77.jpg");
  }

  // ── Photo ordering ───────────────────────────────────────────────────
  //
  // Newest first: with ascending order a newly uploaded photo lands on the last page, so a user on
  // page 1 sees nothing change after uploading. The mapped collection is an unordered Set, so this
  // ordering is imposed here and is what the pager slices.

  private ChrChecklistAttachment photoAddedAt(long id, String isoInstant) {
    ChrChecklistAttachment photo = new ChrChecklistAttachment();
    photo.setChrchecklistAttachmentId(id);
    photo.setFileName("p" + id + ".JPG");
    photo.setMimeTypeCode("JPG");
    photo.setDescription("Photo " + id);
    photo.setEntryTimestamp(Date.from(Instant.parse(isoInstant)));
    photo.setChrChecklist(checklist);
    checklist.getChrChecklistAttachments().add(photo);
    return photo;
  }

  @Test
  void photoMetadataIsNewestFirst() {
    photoAddedAt(1L, "2026-08-01T10:00:00Z");
    photoAddedAt(2L, "2026-08-03T10:00:00Z");
    photoAddedAt(3L, "2026-08-02T10:00:00Z");

    List<Picture> photos = service.getPhotoMetadata(1001L);

    assertEquals(List.of("2", "3", "1"), photos.stream().map(Picture::getId).toList());
  }

  // ── The photo → feature association (CHR_FEATURE_ID) ─────────────────
  //
  // An optional FK to CHR_FEATURE_DETAIL: which feature the photo documents. Written once at upload
  // and read back with the metadata; there is no edit path, which is why photo rows need no
  // optimistic lock.

  private ChrFeatureIdentity featureOnTheChecklist(long featureId, String label) {
    ChrFeatureIdentity feature = new ChrFeatureIdentity();
    feature.setChrFeatureId(featureId);
    feature.setFeatureLabel(label);
    feature.setChrChecklist(checklist);
    checklist.getChrFeatureIdentities().add(feature);
    return feature;
  }

  @Test
  void addPhotoRecordsTheFeatureItDocuments() {
    featureOnTheChecklist(5001L, "3");

    service.addPhoto(1001L, "site.jpg", "A description", null, 5001L, "image/jpeg",
        new byte[] {1, 2, 3}, "IDIR\\tester");

    ChrChecklistAttachment stored = persisted.stream()
        .filter(ChrChecklistAttachment.class::isInstance)
        .map(ChrChecklistAttachment.class::cast)
        .findFirst().orElseThrow();
    assertEquals(5001L, stored.getChrFeatureId());
  }

  @Test
  void addPhotoLeavesTheFeatureUnsetWhenNoneIsGiven() {
    // The association is optional — the column is nullable and the upload UI need not supply one.
    service.addPhoto(1001L, "site.jpg", "A description", null, null, "image/jpeg",
        new byte[] {1, 2, 3}, "IDIR\\tester");

    ChrChecklistAttachment stored = persisted.stream()
        .filter(ChrChecklistAttachment.class::isInstance)
        .map(ChrChecklistAttachment.class::cast)
        .findFirst().orElseThrow();
    assertNull(stored.getChrFeatureId());
  }

  @Test
  void addPhotoRejectsAFeatureBelongingToAnotherChecklist() {
    // The FK is satisfied by any existing feature, so without this check a photo could be hung off
    // another checklist's feature. Nothing may be persisted or written to object storage.
    featureOnTheChecklist(5001L, "3");

    InvalidParameterException ex = assertThrows(InvalidParameterException.class,
        () -> service.addPhoto(1001L, "site.jpg", "A description", null, 9999L, "image/jpeg",
            new byte[] {1, 2, 3}, "IDIR\\tester"));

    assertTrue(ex.getMessage().contains("9999"));
    verify(entityManager, never()).persist(any(ChrChecklistAttachment.class));
    verifyNoInteractions(objectStorage);
  }

  @Test
  void photoMetadataCarriesTheFeatureIdAndItsLabel() {
    featureOnTheChecklist(5001L, "3");
    photoAddedAt(1L, "2026-08-01T10:00:00Z").setChrFeatureId(5001L);

    Picture picture = service.getPhotoMetadata(1001L).getFirst();

    assertEquals("5001", picture.getFeatureId());
    assertEquals("3", picture.getFeatureLabel(),
        "the label is resolved on read so a client can name the feature without a second lookup");
  }

  @Test
  void photoMetadataOmitsTheFeatureWhenThePhotoHasNone() {
    photoAddedAt(1L, "2026-08-01T10:00:00Z");

    Picture picture = service.getPhotoMetadata(1001L).getFirst();

    assertNull(picture.getFeatureId());
    assertNull(picture.getFeatureLabel());
  }

  @Test
  void photoMetadataKeepsTheFeatureIdWhenTheFeatureIsGone() {
    // Deleting a feature does not clear the photo's column, so the id must still round-trip; only
    // the label is unresolvable.
    photoAddedAt(1L, "2026-08-01T10:00:00Z").setChrFeatureId(5001L);

    Picture picture = service.getPhotoMetadata(1001L).getFirst();

    assertEquals("5001", picture.getFeatureId());
    assertNull(picture.getFeatureLabel());
  }

  @Test
  void photosAddedInTheSameSecondFallBackToIdDescending() {
    // entry_timestamp is an Oracle DATE (second precision), so same-second photos are common. Without
    // a tiebreaker the page boundary would be non-deterministic: a row could repeat on one page and
    // vanish from another.
    photoAddedAt(10L, "2026-08-01T10:00:00Z");
    photoAddedAt(12L, "2026-08-01T10:00:00Z");
    photoAddedAt(11L, "2026-08-01T10:00:00Z");

    List<Picture> photos = service.getPhotoMetadata(1001L);

    assertEquals(List.of("12", "11", "10"), photos.stream().map(Picture::getId).toList());
  }
  // ---------------------------------------------------------------------------------------------
  // deleteFeature(checklistId, featureId, userId) — the online delete endpoint's port.
  // ---------------------------------------------------------------------------------------------

  /** Puts one feature on the fixture checklist and returns it. */
  @SuppressWarnings("unchecked")
  private ChrFeatureIdentity givenStoredFeature(long featureId) {
    ChrFeatureIdentity identity = new ChrFeatureIdentity();
    identity.setChrFeatureId(featureId);
    identity.setFeatureLabel("1");
    checklist.getChrFeatureIdentities().add(identity);
    return identity;
  }

  @Test
  void deleteFeatureRemovesTheRowAndDropsItFromTheChecklist() {
    ChrFeatureIdentity identity = givenStoredFeature(7001L);

    service.deleteFeature(1001L, 7001L, "TESTUSER");

    verify(entityManager).remove(identity);
    assertFalse(checklist.getChrFeatureIdentities().contains(identity),
        "a removed entity left in the eager set fails the flush");
  }

  @Test
  void deleteFeatureRefusesAFeatureBelongingToAnotherChecklist() {
    ChrFeatureIdentity mine = givenStoredFeature(7001L);

    // 9999 exists somewhere, just not on this checklist: scoped through the checklist's own set, so
    // it is a not-found here rather than a licence to delete someone else's row.
    EntityNotFoundException thrown = assertThrows(EntityNotFoundException.class,
        () -> service.deleteFeature(1001L, 9999L, "TESTUSER"));

    assertTrue(thrown.getMessage().contains("9999"));
    verify(entityManager, never()).remove(any());
    assertTrue(checklist.getChrFeatureIdentities().contains(mine));
  }

  @Test
  void deleteFeatureReleasesAnOfflineCheckoutAndStampsTheChecklist() {
    checklist.setDeviceCheckoutGuid(new byte[] {1, 2, 3});
    givenStoredFeature(7001L);

    service.deleteFeature(1001L, 7001L, "TESTUSER");

    assertNull(checklist.getDeviceCheckoutGuid(),
        "an online delete releases the checkout, exactly as a section save does");
    assertEquals("TESTUSER", checklist.getUpdateUserid());
    // The cascade flushes repeatedly as it clears each child table; the stamp just has to land.
    verify(entityManager, atLeastOnce()).flush();
  }

  @Test
  void deleteFeatureReportsAMissingChecklistRatherThanNullPointing() {
    when(entityManager.find(ChrChecklist.class, 4242L)).thenReturn(null);

    assertThrows(EntityNotFoundException.class,
        () -> service.deleteFeature(4242L, 7001L, "TESTUSER"));
  }
  // ---------------------------------------------------------------------------------------------
  // saveFeatureAssociations — the associations endpoint's port. An association names two features,
  // so the server writes and removes both directions; nothing else maintains that invariant once a
  // write addresses one feature at a time.
  // ---------------------------------------------------------------------------------------------

  /** A feature complete enough for CheckListMapper.toFeature to read back without null-pointing. */
  @SuppressWarnings("unchecked")
  private ChrFeatureIdentity givenMappableFeature(long featureId, String label) {
    ChrFeatureIdentity identity = new ChrFeatureIdentity();
    identity.setChrFeatureId(featureId);
    identity.setFeatureLabel(label);
    identity.setCompositeFeatureInd("N");
    ChrFeatureDetail detail = new ChrFeatureDetail();
    // DAMAGE_IRREVERSIBLE_ANSWER_CD is NOT NULL, and the mapper dereferences it unguarded.
    FrepChecklistAnswerCode answer = new FrepChecklistAnswerCode();
    answer.setFrepChecklistAnswerCode("N");
    detail.setDamageIrreversibleAnswerCd(answer);
    identity.setChrFeatureDetail(detail);
    checklist.getChrFeatureIdentities().add(identity);
    return identity;
  }

  @Test
  void savingAnAssociationWritesBothDirections() throws Exception {
    givenMappableFeature(7001L, "1");
    givenMappableFeature(7002L, "2");

    service.saveFeatureAssociations(1001L, 7001L, List.of("7002"), "TESTUSER");

    List<ChrAssociatedFeatureXrefId> written = persisted.stream()
        .filter(ChrAssociatedFeatureXref.class::isInstance)
        .map(entity -> ((ChrAssociatedFeatureXref) entity).getId())
        .toList();
    assertEquals(2, written.size(), "an association is a pair, not a row");
    assertTrue(written.stream().anyMatch(
        id -> id.getFromChrFeatureId() == 7001L && id.getToChrFeatureId() == 7002L));
    assertTrue(written.stream().anyMatch(
        id -> id.getFromChrFeatureId() == 7002L && id.getToChrFeatureId() == 7001L));
  }

  @Test
  @SuppressWarnings("unchecked")
  void droppingAnAssociationRemovesBothDirections() throws Exception {
    givenMappableFeature(7001L, "1");
    givenMappableFeature(7002L, "2");
    ChrAssociatedFeatureXref forward = new ChrAssociatedFeatureXref();
    forward.setId(new ChrAssociatedFeatureXrefId(7001L, 7002L));
    ChrAssociatedFeatureXref reverse = new ChrAssociatedFeatureXref();
    reverse.setId(new ChrAssociatedFeatureXrefId(7002L, 7001L));
    TypedQuery<Object> existing = mock(TypedQuery.class);
    when(entityManager.createQuery(contains("ChrAssociatedFeatureXref"), any(Class.class)))
        .thenReturn(existing);
    when(existing.setParameter(anyString(), any())).thenReturn(existing);
    when(existing.getResultList()).thenReturn(List.of(forward, reverse));

    // An empty list clears the feature's associations.
    service.saveFeatureAssociations(1001L, 7001L, List.of(), "TESTUSER");

    verify(entityManager).remove(forward);
    verify(entityManager).remove(reverse);
    assertTrue(persisted.stream().noneMatch(ChrAssociatedFeatureXref.class::isInstance));
  }

  @Test
  void savingAnAssociationRefusesToLinkAFeatureToItself() {
    givenMappableFeature(7001L, "1");

    assertThrows(InvalidParameterException.class,
        () -> service.saveFeatureAssociations(1001L, 7001L, List.of("7001"), "TESTUSER"));
  }

  @Test
  void savingAnAssociationRefusesATargetOnAnotherChecklist() {
    givenMappableFeature(7001L, "1");

    // 9999 is not on this checklist: PROD holds no cross-checklist xref and this is what keeps it
    // that way.
    assertThrows(EntityNotFoundException.class,
        () -> service.saveFeatureAssociations(1001L, 7001L, List.of("9999"), "TESTUSER"));
    assertTrue(persisted.stream().noneMatch(ChrAssociatedFeatureXref.class::isInstance));
  }

  @Test
  void savingAnAssociationRefusesASubjectOnAnotherChecklist() {
    givenMappableFeature(7001L, "1");

    assertThrows(EntityNotFoundException.class,
        () -> service.saveFeatureAssociations(1001L, 9999L, List.of("7001"), "TESTUSER"));
  }

  @Test
  void savingAnAssociationReleasesAnOfflineCheckout() throws Exception {
    checklist.setDeviceCheckoutGuid(new byte[] {1, 2, 3});
    givenMappableFeature(7001L, "1");
    givenMappableFeature(7002L, "2");

    service.saveFeatureAssociations(1001L, 7001L, List.of("7002"), "TESTUSER");

    assertNull(checklist.getDeviceCheckoutGuid());
  }

  @Test
  void savingAnAssociationReturnsBothFeaturesSoNeitherIsLeftStale() throws Exception {
    givenMappableFeature(7001L, "1");
    givenMappableFeature(7002L, "2");

    List<Feature> touched =
        service.saveFeatureAssociations(1001L, 7001L, List.of("7002"), "TESTUSER");

    // The partner's stored state moved too — returning only the subject would leave the client
    // showing a stale copy of the other row.
    assertEquals(2, touched.size());
    assertTrue(touched.stream().anyMatch(f -> "7001".equals(f.getId())));
    assertTrue(touched.stream().anyMatch(f -> "7002".equals(f.getId())));
  }
  // ---------------------------------------------------------------------------------------------
  // saveFeature — the editor's Save. Writes the feature's own fields and its nine child
  // collections, and deliberately leaves its relationships to their own endpoints.
  // ---------------------------------------------------------------------------------------------

  @Test
  void savingAFeatureRewritesItsOwnFieldsAndChildCollections() throws Exception {
    ChrFeatureIdentity stored = givenMappableFeature(7001L, "1");
    stored.setComments("before");

    Feature edited = new Feature();
    edited.setId("7001");
    edited.setFeatureLabel("1");
    edited.setFeatureComment("after");
    edited.setPre1846("true");

    service.saveFeature(1001L, 7001L, edited, "TESTUSER");

    assertEquals("after", stored.getComments());
    assertEquals("TESTUSER", stored.getUpdateUserid());
    assertTrue(persisted.stream().anyMatch(ChrFeatureAgeXref.class::isInstance),
        "the age xref is one of the nine child collections the editor owns");
  }

  @Test
  void savingAFeatureLeavesItsCompositeMembershipAlone() throws Exception {
    ChrFeatureIdentity stored = givenMappableFeature(7001L, "1");
    // Grouped under another feature, by the composite dialog.
    stored.setCompositeChrFeatureIdentity(7002L);
    stored.setCompositeFeatureInd("N");

    Feature edited = new Feature();
    edited.setId("7001");
    edited.setFeatureLabel("1");
    // A stale or absent compositeFeature on the payload must not silently un-group the feature —
    // that is exactly how renaming an anchor used to orphan its members.
    edited.setCompositeFeature(null);

    service.saveFeature(1001L, 7001L, edited, "TESTUSER");

    assertEquals(7002L, stored.getCompositeChrFeatureIdentity());
  }

  @Test
  void savingAFeatureLeavesItsAssociationsAlone() throws Exception {
    givenMappableFeature(7001L, "1");
    Feature edited = new Feature();
    edited.setId("7001");
    edited.setFeatureLabel("1");

    service.saveFeature(1001L, 7001L, edited, "TESTUSER");

    // Associations are a two-feature write with their own endpoint; this one must not touch them.
    assertTrue(persisted.stream().noneMatch(ChrAssociatedFeatureXref.class::isInstance));
  }

  @Test
  void savingAFeatureRefusesOneOnAnotherChecklist() {
    givenMappableFeature(7001L, "1");
    Feature edited = new Feature();
    edited.setId("9999");

    assertThrows(EntityNotFoundException.class,
        () -> service.saveFeature(1001L, 9999L, edited, "TESTUSER"));
  }

  @Test
  void savingAFeatureReleasesAnOfflineCheckoutAndReturnsTheSavedRow() throws Exception {
    checklist.setDeviceCheckoutGuid(new byte[] {9});
    givenMappableFeature(7001L, "1");
    Feature edited = new Feature();
    edited.setId("7001");
    edited.setFeatureLabel("1");

    List<Feature> saved = service.saveFeature(1001L, 7001L, edited, "TESTUSER");

    assertNull(checklist.getDeviceCheckoutGuid());
    assertEquals(1, saved.size(), "an editor save touches exactly one feature");
    assertEquals("7001", saved.get(0).getId());
  }
  // ---------------------------------------------------------------------------------------------
  // createComposite — the only write where a reference target has no id until the call happens.
  // ---------------------------------------------------------------------------------------------

  private Feature anchorPayload() {
    Feature anchor = new Feature();
    anchor.setFeatureLabel("11");
    anchor.setFeatureDescriptionCode("CP");
    anchor.setFeatureInfoSourceCode("SP");
    return anchor;
  }

  @Test
  void creatingACompositeInsertsTheAnchorAndPointsMembersAtIt() throws Exception {
    ChrFeatureIdentity one = givenMappableFeature(7001L, "1");
    ChrFeatureIdentity two = givenMappableFeature(7002L, "2");

    service.createComposite(
        1001L, anchorPayload(), List.of("7001", "7002"), List.of(), "TESTUSER");

    ChrFeatureIdentity anchor = persisted.stream()
        .filter(ChrFeatureIdentity.class::isInstance)
        .map(ChrFeatureIdentity.class::cast)
        .filter(identity -> "Y".equals(identity.getCompositeFeatureInd()))
        .findFirst()
        .orElseThrow();
    assertEquals("11", anchor.getFeatureLabel());
    // Membership lives on the child row, pointing up at the anchor.
    assertEquals(anchor.getChrFeatureId(), one.getCompositeChrFeatureIdentity());
    assertEquals(anchor.getChrFeatureId(), two.getCompositeChrFeatureIdentity());
  }

  @Test
  void creatingACompositeAlsoInsertsTheFeaturesTypedIntoTheDialog() throws Exception {
    givenMappableFeature(7001L, "1");
    Feature typedIn = new Feature();
    typedIn.setFeatureLabel("12");

    service.createComposite(1001L, anchorPayload(), List.of("7001"), List.of(typedIn), "TESTUSER");

    // The dialog is one gesture, so its new features are created in the same call rather than
    // needing a prior round trip that could half-fail.
    assertTrue(ChrStringUtils.hasAValue(typedIn.getId()));
  }

  @Test
  void creatingACompositeRefusesFewerThanTwoMembers() {
    givenMappableFeature(7001L, "1");

    // An empty or single-member composite is a state the UI cannot produce and cannot render.
    assertThrows(InvalidParameterException.class,
        () -> service.createComposite(1001L, anchorPayload(), List.of("7001"), List.of(),
            "TESTUSER"));
  }

  @Test
  void creatingACompositeWillNotTakeAFeatureFromAnotherComposite() {
    ChrFeatureIdentity one = givenMappableFeature(7001L, "1");
    ChrFeatureIdentity two = givenMappableFeature(7002L, "2");
    two.setCompositeChrFeatureIdentity(6000L);

    // Creating a group offers only unattached features, so it cannot quietly empty an existing one.
    assertThrows(InvalidParameterException.class,
        () -> service.createComposite(1001L, anchorPayload(), List.of("7001", "7002"), List.of(),
            "TESTUSER"));
    assertNull(one.getCompositeChrFeatureIdentity());
  }

  @Test
  void creatingACompositeRefusesAMemberOnAnotherChecklist() {
    givenMappableFeature(7001L, "1");

    assertThrows(EntityNotFoundException.class,
        () -> service.createComposite(1001L, anchorPayload(), List.of("7001", "9999"), List.of(),
            "TESTUSER"));
  }

  @Test
  void creatingACompositeReturnsTheAnchorAndEveryMember() throws Exception {
    givenMappableFeature(7001L, "1");
    givenMappableFeature(7002L, "2");

    List<Feature> touched = service.createComposite(
        1001L, anchorPayload(), List.of("7001", "7002"), List.of(), "TESTUSER");

    // Anchor plus both members: every row the gesture moved.
    assertEquals(3, touched.size());
  }
  // ---------------------------------------------------------------------------------------------
  // updateComposite — take, release and create. The asymmetry with createComposite is the point:
  // this one may move a feature across from another group, and has members to let go of.
  // ---------------------------------------------------------------------------------------------

  private ChrFeatureIdentity givenAnchor(long featureId, String label) {
    ChrFeatureIdentity anchor = givenMappableFeature(featureId, label);
    anchor.setCompositeFeatureInd("Y");
    return anchor;
  }

  @Test
  void updatingACompositeReleasesTheMembersItNoLongerNames() throws Exception {
    ChrFeatureIdentity anchor = givenAnchor(7100L, "11");
    ChrFeatureIdentity kept = givenMappableFeature(7001L, "1");
    ChrFeatureIdentity dropped = givenMappableFeature(7002L, "2");
    ChrFeatureIdentity added = givenMappableFeature(7003L, "3");
    kept.setCompositeChrFeatureIdentity(7100L);
    dropped.setCompositeChrFeatureIdentity(7100L);

    service.updateComposite(
        1001L, 7100L, "CP", "SP", List.of("7001", "7003"), List.of(), "TESTUSER");

    assertEquals(7100L, kept.getCompositeChrFeatureIdentity());
    assertEquals(7100L, added.getCompositeChrFeatureIdentity());
    assertNull(dropped.getCompositeChrFeatureIdentity(), "dropped members stand on their own again");
  }

  @Test
  void updatingACompositeTakesAMemberFromAnotherComposite() throws Exception {
    givenAnchor(7100L, "11");
    ChrFeatureIdentity otherAnchor = givenAnchor(7200L, "12");
    ChrFeatureIdentity moving = givenMappableFeature(7001L, "1");
    ChrFeatureIdentity stayput = givenMappableFeature(7002L, "2");
    moving.setCompositeChrFeatureIdentity(7200L);
    stayput.setCompositeChrFeatureIdentity(7200L);
    ChrFeatureIdentity own = givenMappableFeature(7003L, "3");
    own.setCompositeChrFeatureIdentity(7100L);

    service.updateComposite(
        1001L, 7100L, "CP", "SP", List.of("7001", "7003"), List.of(), "TESTUSER");

    // Moving one across is the point of the members dialog...
    assertEquals(7100L, moving.getCompositeChrFeatureIdentity());
    // ...but a release never reaches beyond this anchor's own members.
    assertEquals(7200L, stayput.getCompositeChrFeatureIdentity());
    assertEquals("Y", otherAnchor.getCompositeFeatureInd());
  }

  @Test
  void updatingACompositeRefusesAFeatureThatIsNotAComposite() {
    givenMappableFeature(7001L, "1");
    givenMappableFeature(7002L, "2");

    assertThrows(InvalidParameterException.class,
        () -> service.updateComposite(1001L, 7001L, "CP", "SP", List.of("7002"), List.of(),
            "TESTUSER"));
  }

  @Test
  void updatingACompositeRefusesToMakeItAMemberOfItself() {
    givenAnchor(7100L, "11");
    givenMappableFeature(7001L, "1");

    assertThrows(InvalidParameterException.class,
        () -> service.updateComposite(1001L, 7100L, "CP", "SP", List.of("7100", "7001"), List.of(),
            "TESTUSER"));
  }

  @Test
  void updatingACompositeRefusesFewerThanTwoMembers() {
    givenAnchor(7100L, "11");
    givenMappableFeature(7001L, "1");

    assertThrows(InvalidParameterException.class,
        () -> service.updateComposite(1001L, 7100L, "CP", "SP", List.of("7001"), List.of(),
            "TESTUSER"));
  }

  @Test
  void updatingACompositeReturnsTheAnchorEveryMemberAndEveryRelease() throws Exception {
    givenAnchor(7100L, "11");
    ChrFeatureIdentity kept = givenMappableFeature(7001L, "1");
    ChrFeatureIdentity dropped = givenMappableFeature(7002L, "2");
    givenMappableFeature(7003L, "3");
    kept.setCompositeChrFeatureIdentity(7100L);
    dropped.setCompositeChrFeatureIdentity(7100L);

    List<Feature> touched = service.updateComposite(
        1001L, 7100L, "CP", "SP", List.of("7001", "7003"), List.of(), "TESTUSER");

    // Anchor, the two it now holds, and the one it let go — a client patching by id needs all four.
    assertEquals(4, touched.size());
  }
  // ---------------------------------------------------------------------------------------------
  // ungroupComposite — the anchor goes either way; the keep/delete choice is only about members
  // that were never assessed in their own right.
  // ---------------------------------------------------------------------------------------------

  @Test
  void ungroupingDeletesTheAnchorAndKeepsTheMembersByDefault() throws Exception {
    ChrFeatureIdentity anchor = givenAnchor(7100L, "11");
    ChrFeatureIdentity one = givenMappableFeature(7001L, "1");
    ChrFeatureIdentity two = givenMappableFeature(7002L, "2");
    one.setCompositeChrFeatureIdentity(7100L);
    two.setCompositeChrFeatureIdentity(7100L);

    service.ungroupComposite(1001L, 7100L, List.of(), "TESTUSER");

    // Dissolved, not emptied: an anchor with no members is a state the UI cannot render.
    verify(entityManager).remove(anchor);
    assertFalse(checklist.getChrFeatureIdentities().contains(anchor));
    verify(entityManager, never()).remove(one);
    verify(entityManager, never()).remove(two);
  }

  @Test
  void ungroupingDeletesOnlyTheMembersItIsToldTo() throws Exception {
    givenAnchor(7100L, "11");
    ChrFeatureIdentity kept = givenMappableFeature(7001L, "1");
    ChrFeatureIdentity undescribed = givenMappableFeature(7002L, "2");
    kept.setCompositeChrFeatureIdentity(7100L);
    undescribed.setCompositeChrFeatureIdentity(7100L);

    service.ungroupComposite(1001L, 7100L, List.of("7002"), "TESTUSER");

    verify(entityManager).remove(undescribed);
    verify(entityManager, never()).remove(kept);
  }

  @Test
  void ungroupingRefusesToDeleteAFeatureThatIsNotOneOfItsMembers() {
    givenAnchor(7100L, "11");
    ChrFeatureIdentity member = givenMappableFeature(7001L, "1");
    ChrFeatureIdentity bystander = givenMappableFeature(7002L, "2");
    member.setCompositeChrFeatureIdentity(7100L);

    // The client decides which members are undescribed; it does not get to name arbitrary features
    // for deletion under cover of an ungroup.
    assertThrows(InvalidParameterException.class,
        () -> service.ungroupComposite(1001L, 7100L, List.of("7002"), "TESTUSER"));
    verify(entityManager, never()).remove(bystander);
  }

  @Test
  void ungroupingRefusesAFeatureThatIsNotAComposite() {
    givenMappableFeature(7001L, "1");

    assertThrows(InvalidParameterException.class,
        () -> service.ungroupComposite(1001L, 7001L, List.of(), "TESTUSER"));
  }

  @Test
  void ungroupingReleasesTheSurvivorsAndReturnsThem() throws Exception {
    givenAnchor(7100L, "11");
    ChrFeatureIdentity one = givenMappableFeature(7001L, "1");
    ChrFeatureIdentity two = givenMappableFeature(7002L, "2");
    one.setCompositeChrFeatureIdentity(7100L);
    two.setCompositeChrFeatureIdentity(7100L);

    List<Feature> survivors = service.ungroupComposite(1001L, 7100L, List.of("7002"), "TESTUSER");

    // Only the member that stayed: the anchor and the deleted member no longer exist, and the
    // caller asked for both so it can drop them itself.
    assertEquals(1, survivors.size());
    assertEquals("7001", survivors.get(0).getId());
  }
  // ---------------------------------------------------------------------------------------------
  // createStandaloneFeature — the editor's Save on a feature the server has never seen. Without it
  // the editor fell back to the whole-document save, so building a checklist stayed quadratic.
  // ---------------------------------------------------------------------------------------------

  @Test
  void creatingAFeatureInsertsItAndHandsBackItsId() throws Exception {
    Feature added = new Feature();
    added.setFeatureLabel("4");
    added.setFeatureDescriptionCode("CMT");
    added.setPre1846("true");

    List<Feature> saved = service.createStandaloneFeature(1001L, added, "TESTUSER");

    assertTrue(ChrStringUtils.hasAValue(added.getId()), "the client needs the assigned id");
    assertEquals(1, saved.size(), "creating one feature touches one feature");
    assertTrue(persisted.stream().anyMatch(ChrFeatureAgeXref.class::isInstance),
        "its child collections are written too, not just the identity row");
  }

  @Test
  void creatingAFeatureLeavesTheOthersAlone() throws Exception {
    ChrFeatureIdentity untouched = givenMappableFeature(7001L, "1");
    untouched.setComments("as it was");

    Feature added = new Feature();
    added.setFeatureLabel("2");
    service.createStandaloneFeature(1001L, added, "TESTUSER");

    // The whole point: adding the tenth feature must not rewrite the other nine.
    assertEquals("as it was", untouched.getComments());
    assertNull(untouched.getUpdateUserid());
  }

  @Test
  void creatingAFeatureNeverGroupsIt() throws Exception {
    Feature added = new Feature();
    added.setFeatureLabel("2");
    // A stale flag on the payload must not make a composite by accident — grouping belongs to the
    // composite endpoints, and the editor cannot express it.
    added.setCompositeFeatureInd("true");

    service.createStandaloneFeature(1001L, added, "TESTUSER");

    ChrFeatureIdentity created = persisted.stream()
        .filter(ChrFeatureIdentity.class::isInstance)
        .map(ChrFeatureIdentity.class::cast)
        .findFirst()
        .orElseThrow();
    assertEquals("N", created.getCompositeFeatureInd());
    assertNull(created.getCompositeChrFeatureIdentity());
  }

  @Test
  void creatingAFeatureReleasesAnOfflineCheckout() throws Exception {
    checklist.setDeviceCheckoutGuid(new byte[] {7});
    Feature added = new Feature();
    added.setFeatureLabel("2");

    service.createStandaloneFeature(1001L, added, "TESTUSER");

    assertNull(checklist.getDeviceCheckoutGuid());
  }
}

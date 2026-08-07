package ca.bc.gov.nrs.frep.service.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import ca.bc.gov.nrs.frep.entity.ChrChecklistAttachment;
import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
import ca.bc.gov.nrs.frep.struct.v1.frep.Feature;
import ca.bc.gov.nrs.frep.entity.ChrAssociatedFeatureXref;
import ca.bc.gov.nrs.frep.entity.ChrAssociatedFeatureXrefId;
import ca.bc.gov.nrs.frep.entity.ChrChecklist;
import ca.bc.gov.nrs.frep.entity.ChrFeatureAgeXref;
import ca.bc.gov.nrs.frep.entity.ChrFeatureDetail;
import ca.bc.gov.nrs.frep.entity.ChrFeatureIdentity;
import ca.bc.gov.nrs.frep.entity.ChrFeatureTypeXref;
import ca.bc.gov.nrs.frep.entity.FrepChecklistStatusCode;
import ca.bc.gov.nrs.frep.entity.FrepResourceValue;
import ca.bc.gov.nrs.frep.entity.FrepResourceValueStatCode;
import ca.bc.gov.nrs.frep.service.v1.ObjectStorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    service.addPhoto(1001L, "site.jpg", "A description", null, "image/jpeg",
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
}

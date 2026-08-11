package ca.bc.gov.nrs.frep.service.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import ca.bc.gov.nrs.frep.entity.ChrChecklist;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Token neutrality against a <b>real</b> persistence context.
 *
 * <p>{@code revisionCount} is a JPA {@code @Version}, and Hibernate — not our code — decides when to
 * increment it. The mocked-EntityManager tests can only assert that we don't hand-stamp
 * {@code updateTimestamp}/{@code updateUserid}; they cannot observe a version bump at all, because
 * nothing in a mock implements the flush-time dirty check that produces one. This test exists to
 * cover exactly that blind spot.
 */
@DataJpaTest
// Keep the configured URL: @DataJpaTest otherwise swaps in its own embedded datasource and drops the
// INIT clause that creates the THE schema the entities are mapped to.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:tokentest;DB_CLOSE_DELAY=-1;MODE=Oracle"
        + ";INIT=CREATE SCHEMA IF NOT EXISTS THE",
    "spring.jpa.hibernate.ddl-auto=create-drop",
})
class ChrPhotoTokenNeutralityJpaTest {

  @PersistenceContext
  private EntityManager entityManager;

  private ChrChecklistPersistenceService serviceOn(EntityManager em) {
    ChrChecklistPersistenceService service =
        new ChrChecklistPersistenceService(mock(ObjectStorageService.class));
    ReflectionTestUtils.setField(service, "entityManager", em);
    return service;
  }

  /**
   * Seeded natively because {@code revisionCount} is mapped {@code @Column(insertable = false)} —
   * the real Oracle column carries {@code DEFAULT 0 NOT NULL} and generated H2 DDL does not, so a
   * JPA persist would leave the version null.
   */
  private long givenAChecklist() {
    entityManager.createNativeQuery(
            "insert into THE.CHR_CHECKLIST (CHR_CHECKLIST_ID, REVISION_COUNT) values (1001, 0)")
        .executeUpdate();
    entityManager.flush();
    entityManager.clear();
    return 1001L;
  }

  private long revisionCountOf(long checklistId) {
    entityManager.clear();
    return entityManager.find(ChrChecklist.class, checklistId).getRevisionCount();
  }

  @Test
  void addingAPhotoDoesNotAdvanceTheChecklistVersion() {
    long checklistId = givenAChecklist();
    long before = revisionCountOf(checklistId);

    serviceOn(entityManager).addPhoto(checklistId, "site.jpg", "A description", null, null,
        "image/jpeg", new byte[] {1, 2, 3}, "IDIR\\tester");
    entityManager.flush();

    assertEquals(before, revisionCountOf(checklistId),
        "a photo upload must not advance the checklist's shared @Version token");
  }

  @Test
  void aRealChecklistEditStillAdvancesTheVersion() {
    // Control. Without this the two tests above could pass for the wrong reason — a harness that
    // never observes a version bump at all would report token neutrality no matter what the code
    // did. The token must still work; it must only ignore photos.
    long checklistId = givenAChecklist();
    long before = revisionCountOf(checklistId);

    entityManager.find(ChrChecklist.class, checklistId).setBlockComments("edited");
    entityManager.flush();

    assertEquals(before + 1, revisionCountOf(checklistId));
  }

  @Test
  void deletingAPhotoDoesNotAdvanceTheChecklistVersion() {
    long checklistId = givenAChecklist();
    ChrChecklistPersistenceService service = serviceOn(entityManager);
    long photoId = service.addPhoto(checklistId, "site.jpg", "A description", null, null,
        "image/jpeg", new byte[] {1, 2, 3}, "IDIR\\tester").getChrchecklistAttachmentId();
    entityManager.flush();
    long before = revisionCountOf(checklistId);

    service.deletePhoto(checklistId, photoId, "IDIR\\tester");
    entityManager.flush();

    assertEquals(before, revisionCountOf(checklistId),
        "a photo delete must not advance the checklist's shared @Version token");
  }
}

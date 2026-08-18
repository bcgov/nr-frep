package ca.bc.gov.nrs.frep.service.v1.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.repository.v1.ProtocolChecklistWriteRepository;
import ca.bc.gov.nrs.frep.service.v1.ObjectStorageService;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentContent;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioAttachmentMigrationResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioAttachmentRef;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioAttachmentVerifyResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Cutover tooling — delete alongside {@link BioAttachmentMigrationService} once Phase 4b ships.
 *
 * <p>The cases that matter are the ones that would silently corrupt the cutover: skipping rows that
 * already landed (so a re-run resumes rather than repeats), never writing zero-byte objects over
 * legacy empty rows, surviving a single failed row, and — above all — verify() distinguishing "no
 * object but Oracle still has bytes" (a real miss) from "no object and no bytes either" (expected).
 */
@ExtendWith(MockitoExtension.class)
class BioAttachmentMigrationServiceTest {

  @Mock private ProtocolChecklistWriteRepository writeRepository;
  @Mock private ObjectStorageService objectStorage;
  @InjectMocks private BioAttachmentMigrationService service;

  private static BioAttachmentRef ref(String id) {
    return new BioAttachmentRef(id, "900" + id, "SLB");
  }

  private static AttachmentContent content(byte[] bytes) {
    return new AttachmentContent("f.pdf", "application/pdf", bytes);
  }

  @Test
  void migrateWritesBlobBytesUnderTheSlrKeyTheReadPathExpects() {
    when(writeRepository.listBioAttachmentsForMigration("0", 250)).thenReturn(List.of(ref("77")));
    when(objectStorage.objectExists("slr/77")).thenReturn(false);
    when(writeRepository.getAttachmentContentFromBlob("90077", "SLB", "77"))
        .thenReturn(content(new byte[] {1, 2, 3}));

    BioAttachmentMigrationResult result = service.migrate("0", 250, false);

    verify(objectStorage).putObject(eq("slr/77"), eq("application/pdf"), eq(new byte[] {1, 2, 3}));
    assertEquals(1, result.migrated());
    assertEquals(3L, result.bytesWritten());
    assertEquals("77", result.lastId());
    assertFalse(result.hasMore());
  }

  @Test
  void migrateSkipsRowsAlreadyInObjectStorageSoRerunsResume() {
    when(writeRepository.listBioAttachmentsForMigration("0", 250)).thenReturn(List.of(ref("77")));
    when(objectStorage.objectExists("slr/77")).thenReturn(true);

    BioAttachmentMigrationResult result = service.migrate("0", 250, false);

    assertEquals(1, result.skippedExisting());
    assertEquals(0, result.migrated());
    // The BLOB is never even read for a row that already landed.
    verify(writeRepository, never()).getAttachmentContentFromBlob(anyString(), anyString(), anyString());
    verify(objectStorage, never()).putObject(anyString(), anyString(), any());
  }

  @Test
  void migrateRecordsEmptyBlobsInsteadOfWritingZeroByteObjects() {
    when(writeRepository.listBioAttachmentsForMigration("0", 250)).thenReturn(List.of(ref("11")));
    when(objectStorage.objectExists("slr/11")).thenReturn(false);
    when(writeRepository.getAttachmentContentFromBlob("90011", "SLB", "11"))
        .thenReturn(content(new byte[0]));

    BioAttachmentMigrationResult result = service.migrate("0", 250, false);

    assertEquals(List.of("11"), result.skippedEmptyIds());
    assertEquals(0, result.migrated());
    verify(objectStorage, never()).putObject(anyString(), anyString(), any());
  }

  @Test
  void dryRunReportsWithoutWriting() {
    when(writeRepository.listBioAttachmentsForMigration("0", 250)).thenReturn(List.of(ref("77")));
    when(objectStorage.objectExists("slr/77")).thenReturn(false);
    when(writeRepository.getAttachmentContentFromBlob("90077", "SLB", "77"))
        .thenReturn(content(new byte[] {1, 2, 3}));

    BioAttachmentMigrationResult result = service.migrate("0", 250, true);

    assertEquals(1, result.wouldMigrate());
    assertEquals(0, result.migrated());
    assertEquals(0L, result.bytesWritten());
    verify(objectStorage, never()).putObject(anyString(), anyString(), any());
  }

  @Test
  void oneFailingRowIsRecordedAndTheBatchContinues() {
    when(writeRepository.listBioAttachmentsForMigration("0", 250))
        .thenReturn(List.of(ref("77"), ref("78")));
    when(objectStorage.objectExists("slr/77")).thenReturn(false);
    when(objectStorage.objectExists("slr/78")).thenReturn(false);
    when(writeRepository.getAttachmentContentFromBlob("90077", "SLB", "77"))
        .thenThrow(new IllegalStateException("ORA-boom"));
    when(writeRepository.getAttachmentContentFromBlob("90078", "SLB", "78"))
        .thenReturn(content(new byte[] {9}));

    BioAttachmentMigrationResult result = service.migrate("0", 250, false);

    assertEquals(1, result.migrated());
    assertEquals(1, result.failed().size());
    assertTrue(result.failed().get(0).startsWith("77: "));
    // The batch still advances past the failure, so the loop does not stall on it.
    assertEquals("78", result.lastId());
  }

  @Test
  void hasMoreIsTrueOnlyWhenTheBatchCameBackFull() {
    when(writeRepository.listBioAttachmentsForMigration("0", 2))
        .thenReturn(List.of(ref("77"), ref("78")));
    when(objectStorage.objectExists(anyString())).thenReturn(true);

    assertTrue(service.migrate("0", 2, false).hasMore());
  }

  @Test
  void limitIsClampedSoOneCallCannotScanTheWholeTable() {
    when(writeRepository.listBioAttachmentsForMigration("0", 1000)).thenReturn(List.of());

    service.migrate("0", 999_999, true);

    verify(writeRepository).listBioAttachmentsForMigration("0", 1000);
  }

  @Test
  void verifySplitsRealMissesFromRowsWithNoBytesInOracle() {
    when(writeRepository.listBioAttachmentsForMigration("0", 500))
        .thenReturn(List.of(ref("77"), ref("78"), ref("11")));
    when(objectStorage.objectExists("slr/77")).thenReturn(true);
    when(objectStorage.objectExists("slr/78")).thenReturn(false);
    when(objectStorage.objectExists("slr/11")).thenReturn(false);
    // 78 still has bytes in Oracle -> a genuine miss that must block the gate.
    when(writeRepository.getAttachmentContentFromBlob("90078", "SLB", "78"))
        .thenReturn(content(new byte[] {5}));
    // 11 has none -> expected, excluded from the gate.
    when(writeRepository.getAttachmentContentFromBlob("90011", "SLB", "11"))
        .thenReturn(content(new byte[0]));

    BioAttachmentVerifyResult result = service.verify("0", 500);

    assertEquals(1, result.present());
    assertEquals(List.of("78"), result.missingIds());
    assertEquals(List.of("11"), result.emptyInOracleIds());
  }

  /**
   * The resource type must come from the row, not a constant. SLB and SLR are both live Biodiversity
   * codes mid-rename, and GET_BLOB takes the code as a parameter — hardcoding SLB would address the
   * wrong protocol for every already-renamed checklist.
   */
  @Test
  void resourceTypeIsTakenPerRowNotAssumedToBeSlb() {
    when(writeRepository.listBioAttachmentsForMigration("0", 250)).thenReturn(
        List.of(new BioAttachmentRef("77", "90077", "SLB"),
                new BioAttachmentRef("78", "90078", "SLR")));
    when(objectStorage.objectExists(anyString())).thenReturn(false);
    when(writeRepository.getAttachmentContentFromBlob("90077", "SLB", "77"))
        .thenReturn(content(new byte[] {1}));
    when(writeRepository.getAttachmentContentFromBlob("90078", "SLR", "78"))
        .thenReturn(content(new byte[] {2}));

    BioAttachmentMigrationResult result = service.migrate("0", 250, false);

    assertEquals(2, result.migrated());
    verify(writeRepository).getAttachmentContentFromBlob("90077", "SLB", "77");
    verify(writeRepository).getAttachmentContentFromBlob("90078", "SLR", "78");
  }

  @Test
  void verifyNeverWrites() {
    when(writeRepository.listBioAttachmentsForMigration("0", 500)).thenReturn(List.of(ref("77")));
    when(objectStorage.objectExists("slr/77")).thenReturn(true);

    service.verify("0", 500);

    verify(objectStorage).objectExists("slr/77");
    verifyNoMoreInteractions(objectStorage);
  }
}

package ca.bc.gov.nrs.frep.service.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the Biodiversity attachment object key.
 *
 * <p>This is a small function with an outsized failure mode. Four paths resolve a key for the same
 * attachment — the upload and delete in {@code ProtocolChecklistWriteRepositoryImpl}, the download
 * in the same class, the size HEAD in {@code ProtocolChecklistService}, and the one-time BLOB
 * migration in {@code BioAttachmentMigrationService} — and they must agree exactly.
 *
 * <p>They now all call {@link ObjectStorageService#bioObjectKey}, so disagreement is
 * unrepresentable. It was not always so, and the reason this test exists rather than the four
 * call-site tests being considered enough is that a divergence would not have looked like a bug:
 * the download falls back to the Oracle BLOB when an object is absent, and the migration copies
 * bytes rather than moving them, so every migrated row still has its BLOB. A migration writing to a
 * key the download never read would have produced correct downloads, a passing completeness gate,
 * and a bucket full of orphans — with the first real symptom arriving when Phase 4b removes the
 * fallback.
 */
class ObjectStorageServiceTest {

  @Test
  @DisplayName("key is the flat slr/ namespace plus the attachment id")
  void bioObjectKey_prefixesId() {
    assertEquals("slr/42076", ObjectStorageService.bioObjectKey("42076"));
  }

  @Test
  @DisplayName("id is trimmed — a trailing space would write where nothing reads")
  void bioObjectKey_trimsId() {
    assertEquals("slr/42076", ObjectStorageService.bioObjectKey(" 42076 "));
    assertEquals("slr/42076", ObjectStorageService.bioObjectKey("42076\n"));
  }

  @Test
  @DisplayName("keys are flat: no id is nested under another id's prefix")
  void bioObjectKey_isFlat() {
    // The size lookup deliberately HEADs one key per row rather than listing by prefix, which is
    // only sound while keys carry no additional separator. `slr/4` must not be a prefix boundary
    // of `slr/42`, i.e. nothing may append a delimiter after the id.
    assertEquals("slr/4", ObjectStorageService.bioObjectKey("4"));
    assertEquals("slr/42", ObjectStorageService.bioObjectKey("42"));
  }
}

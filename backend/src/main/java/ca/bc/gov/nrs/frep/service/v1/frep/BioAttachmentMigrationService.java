package ca.bc.gov.nrs.frep.service.v1.frep;

import ca.bc.gov.nrs.frep.repository.v1.ProtocolChecklistWriteRepository;
import ca.bc.gov.nrs.frep.service.v1.ObjectStorageService;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentContent;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioAttachmentMigrationResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioAttachmentRef;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioAttachmentVerifyResult;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * ONE-TIME CUTOVER TOOLING — delete this class, its endpoint/controller, the two repository methods
 * it uses and its tests once the migration is verified complete and Phase 4b has shipped.
 *
 * <p>Copies existing Biodiversity attachment bytes from the Oracle BLOB to object storage under the
 * key the read path already expects ({@code slr/<attachmentId>}). New uploads have gone straight to
 * object storage since the storage switch; this only backfills rows the legacy app wrote.
 *
 * <p><b>Why this runs inside the application</b> rather than as a standalone script: the JVM already
 * holds a working TCPS connection to Oracle. An external client hits two independent walls — the DB
 * certificate is 1024-bit RSA (below OpenSSL 3's default floor, but above the JDK's) and it is issued
 * by a private CA — and would additionally need a new {@code SELECT} grant on
 * {@code BIODIVERSITY_ATTACH_CONTENT}, because it could not use the definer-rights package. Reading
 * bytes via {@code GET_BLOB} sidesteps the grant entirely.
 *
 * <p><b>Sizing (measured in PROD 2026-08-17):</b> 5,005 attachment rows, 2.0 GB total, largest single
 * BLOB 3.3 MB, 3 rows with an empty BLOB. So 5,002 objects to write. At that size the work is bounded
 * enough to run in the serving pod during the cutover freeze: one attachment at a time, ~3.3 MB peak
 * against a 400 MB heap. It is deliberately NOT parallel — throughput is not the constraint, and
 * keeping it serial keeps the memory ceiling equal to the largest single file.
 *
 * <p>Batched by design: {@code limit} rows per call, feeding {@code lastId} back as {@code afterId},
 * so no single request outlives the route timeout. Idempotent — an attachment whose object already
 * exists is skipped, so a re-run resumes rather than repeats.
 */
@Service
public class BioAttachmentMigrationService {

  private static final Logger log = LoggerFactory.getLogger(BioAttachmentMigrationService.class);

  private static final String KEY_PREFIX = "slr/";
  private static final int MAX_BATCH = 1000;

  private final ProtocolChecklistWriteRepository writeRepository;
  private final ObjectStorageService objectStorage;

  public BioAttachmentMigrationService(
      ProtocolChecklistWriteRepository writeRepository, ObjectStorageService objectStorage) {
    this.writeRepository = writeRepository;
    this.objectStorage = objectStorage;
  }

  /** Object key for an attachment; must match the read path exactly or migrated bytes are invisible. */
  private static String objectKey(String attachmentId) {
    return KEY_PREFIX + attachmentId.trim();
  }

  private static int clampLimit(int limit) {
    return Math.max(1, Math.min(limit, MAX_BATCH));
  }

  /**
   * Migrate one batch. With {@code dryRun} nothing is written — it reports what would move, which is
   * how you size the real run and confirm the batch boundaries behave before touching the bucket.
   */
  public BioAttachmentMigrationResult migrate(String afterId, int limit, boolean dryRun) {
    List<BioAttachmentRef> refs =
        writeRepository.listBioAttachmentsForMigration(afterId, clampLimit(limit));

    int migrated = 0;
    int wouldMigrate = 0;
    int skippedExisting = 0;
    long bytesWritten = 0L;
    List<String> skippedEmpty = new ArrayList<>();
    List<String> failed = new ArrayList<>();
    String lastId = afterId;

    for (BioAttachmentRef ref : refs) {
      lastId = ref.attachmentId();
      try {
        String key = objectKey(ref.attachmentId());
        if (objectStorage.objectExists(key)) {
          skippedExisting++;
          continue;
        }

        AttachmentContent blob = writeRepository.getAttachmentContentFromBlob(
            ref.checklistId(), ref.resourceType(), ref.attachmentId());
        byte[] bytes = blob.data();

        // No bytes on either side: legacy rows that never had content. Writing a zero-byte object
        // would only launder bad data into the new store, so they are recorded and left alone.
        if (bytes == null || bytes.length == 0) {
          skippedEmpty.add(ref.attachmentId());
          continue;
        }

        if (dryRun) {
          wouldMigrate++;
          continue;
        }

        objectStorage.putObject(key, blob.mimeType(), bytes);
        migrated++;
        bytesWritten += bytes.length;
      } catch (RuntimeException ex) {
        // One bad row must not abort the batch: record it and continue, so a single failure costs
        // one attachment rather than the rest of the run. Re-running retries exactly these.
        log.error("BIO attachment migration failed for id {}", ref.attachmentId(), ex);
        failed.add(ref.attachmentId() + ": " + ex.getMessage());
      }
    }

    boolean hasMore = refs.size() == clampLimit(limit);
    log.info("BIO attachment migration batch: scanned={} migrated={} wouldMigrate={} "
            + "skippedExisting={} skippedEmpty={} failed={} lastId={} hasMore={}",
        refs.size(), migrated, wouldMigrate, skippedExisting, skippedEmpty.size(), failed.size(),
        lastId, hasMore);

    return new BioAttachmentMigrationResult(refs.size(), migrated, wouldMigrate, skippedExisting,
        skippedEmpty, failed, lastId, hasMore, bytesWritten);
  }

  /**
   * The completeness gate: every row with bytes in Oracle must have an object.
   *
   * <p>A row with no object is only a real miss if Oracle still holds bytes for it, so absences are
   * re-read from the BLOB and split into {@code missingIds} (must be fixed) and
   * {@code emptyInOracleIds} (nothing to migrate, excluded by design). Folding the latter into the
   * former would make the gate impossible to pass and train people to ignore it.
   */
  public BioAttachmentVerifyResult verify(String afterId, int limit) {
    List<BioAttachmentRef> refs =
        writeRepository.listBioAttachmentsForMigration(afterId, clampLimit(limit));

    int present = 0;
    List<String> missing = new ArrayList<>();
    List<String> emptyInOracle = new ArrayList<>();
    String lastId = afterId;

    for (BioAttachmentRef ref : refs) {
      lastId = ref.attachmentId();
      if (objectStorage.objectExists(objectKey(ref.attachmentId()))) {
        present++;
        continue;
      }
      AttachmentContent blob = writeRepository.getAttachmentContentFromBlob(
          ref.checklistId(), ref.resourceType(), ref.attachmentId());
      if (blob.data() == null || blob.data().length == 0) {
        emptyInOracle.add(ref.attachmentId());
      } else {
        missing.add(ref.attachmentId());
      }
    }

    boolean hasMore = refs.size() == clampLimit(limit);
    if (!missing.isEmpty()) {
      log.warn("BIO attachment verify: {} row(s) still have bytes in Oracle but no object: {}",
          missing.size(), missing);
    }
    return new BioAttachmentVerifyResult(
        refs.size(), present, missing, emptyInOracle, lastId, hasMore);
  }
}

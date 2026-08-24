package ca.bc.gov.nrs.frep.service.v1.frep;

import ca.bc.gov.nrs.frep.configuration.BioAttachmentMigrationProperties;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioAttachmentMigrationResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioAttachmentVerifyResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * ONE-TIME CUTOVER TOOLING — drives {@link BioAttachmentMigrationService} to completion, once, when
 * the pod boots with the migration switched on. Delete with the rest of the tooling once Phase 4b
 * has shipped.
 *
 * <p><b>DELETE-AFTER-BIO-ATTACHMENT-MIGRATION</b> — grep that tag to find every file and method that must go; the
 * checklist is in {@code backend/tools/bio-attachment-migration-runbook.md}.
 *
 * <p><b>Why a scheduler and not an {@code ApplicationRunner}.</b> This runs inside the serving pod,
 * and a runner executes on the main thread before the context finishes refreshing — the pod would
 * not become ready until the migration finished, and the readiness probe (30s initial delay, then
 * 20 × 10s) would fail it partway through and restart the pod mid-run. A {@code @Scheduled} method
 * runs on a scheduler thread after refresh, so startup, readiness and traffic are untouched.
 *
 * <p><b>Why it is not an HTTP endpoint.</b> The API requires a Cognito access token on every
 * request ({@code anyRequest().authenticated()}), and those are only obtainable through an
 * interactive IDIR login in a browser — FAM's pool has no machine client. Driving a migration from
 * a hand-copied, short-lived token is a poor way to run a production cutover, and an endpoint open
 * enough to avoid that is worse. Calling the service directly removes the token, the CSRF handshake
 * and the NetworkPolicy question in one move, and leaves nothing exposed.
 *
 * <p><b>Replicas.</b> {@code @Scheduled} fires on <em>every</em> pod — TEST runs 2 and PROD 3
 * ({@code merge.yml}). That is safe rather than merely tolerable: object keys are derived from the
 * attachment id, the bytes are identical whichever pod reads them, and each row is skipped once an
 * object exists. Two pods therefore interleave and converge on the same result; the cost is
 * duplicated reads and writes, not corruption, and the verify gate reads the bucket rather than any
 * pod's tally, so it stays authoritative however many ran.
 *
 * <p>What concurrency does spoil is the log: interleaved totals from anonymous pods cannot be read.
 * Hence {@link #POD} on every line. Pinning to one replica is still worth it in PROD, where a clean
 * single-pod record of a production data migration is worth two commands — see the runbook.
 *
 * <p>Runs once per pod boot: the delay lets the connection pool and object-storage client settle
 * first, and the {@code started} latch means a re-fire can never overlap an in-flight run.
 */
public class BioAttachmentMigrationScheduler {

  private static final Logger log = LoggerFactory.getLogger(BioAttachmentMigrationScheduler.class);

  /** Keyset cursor start. The scan is {@code WHERE id > ?}, so "0" means "from the beginning". */
  private static final String START_ID = "0";

  /**
   * Runaway guard. At the measured 5,005 rows even a 1-row batch would finish inside this; it exists
   * so a cursor bug degrades into a loud stop rather than an endless loop against production.
   */
  private static final int MAX_BATCHES = 10_000;

  /** Ids are logged for follow-up, not for reading in bulk — a full 5,000-id line helps nobody. */
  private static final int MAX_IDS_LOGGED = 50;

  /**
   * Pod name, stamped on every line this class logs. When more than one replica is running they all
   * migrate concurrently and their output interleaves in {@code oc logs}, at which point totals with
   * no pod on them are unreadable. OpenShift sets {@code HOSTNAME} to the pod name.
   */
  private static final String POD =
      System.getenv("HOSTNAME") == null ? "local" : System.getenv("HOSTNAME");

  private final BioAttachmentMigrationService migrationService;
  private final BioAttachmentMigrationProperties properties;
  private final AtomicBoolean started = new AtomicBoolean(false);

  public BioAttachmentMigrationScheduler(
      BioAttachmentMigrationService migrationService,
      BioAttachmentMigrationProperties properties) {
    this.migrationService = migrationService;
    this.properties = properties;
  }

  /**
   * Fires once, {@code initial-delay} after this pod's context is ready, and then not again for a
   * year — i.e. never, since the pod will not live that long. {@code fixedDelayString} is used
   * rather than an enormous {@code fixedDelay} because Spring adds the delay to an {@code Instant},
   * where {@code Long.MAX_VALUE} milliseconds overflows.
   */
  @Scheduled(
      initialDelayString = "${frep.bio-attachment-migration.initial-delay:60s}",
      fixedDelayString = "P365D")
  public void run() {
    if (!started.compareAndSet(false, true)) {
      log.warn("[{}] BIO attachment migration already ran in this pod — ignoring repeat trigger", POD);
      return;
    }
    try {
      boolean dryRun = properties.dryRun();
      migrateAll(dryRun);
      // A dry run writes nothing, so every row would come back a "miss" — the pass would be a long,
      // alarming, meaningless report. The migrate totals already say what would move.
      if (dryRun) {
        log.info("[{}] BIO attachment migration: dry run — verify pass skipped", POD);
      } else {
        verifyAll();
      }
    } catch (RuntimeException ex) {
      // Nothing above this catches: an escaping exception would kill the scheduler thread and the
      // run would end with no summary at all, which is the one outcome that must not happen quietly.
      log.error("[{}] BIO attachment migration aborted", POD, ex);
    }
  }

  /** Walks every batch, feeding {@code lastId} back as {@code afterId}, and logs one summary. */
  private void migrateAll(boolean dryRun) {
    log.info("==== [{}] BIO attachment migration START (dryRun={} batchSize={}) ====",
        POD, dryRun, properties.batchSize());

    String afterId = START_ID;
    int batches = 0;
    int scanned = 0;
    int migrated = 0;
    int wouldMigrate = 0;
    int skippedExisting = 0;
    long bytesWritten = 0L;
    List<String> skippedEmpty = new ArrayList<>();
    List<String> failed = new ArrayList<>();

    while (batches < MAX_BATCHES) {
      batches++;
      BioAttachmentMigrationResult result =
          migrationService.migrate(afterId, properties.batchSize(), dryRun);

      scanned += result.scanned();
      migrated += result.migrated();
      wouldMigrate += result.wouldMigrate();
      skippedExisting += result.skippedExisting();
      bytesWritten += result.bytesWritten();
      skippedEmpty.addAll(result.skippedEmptyIds());
      failed.addAll(result.failed());

      if (!result.hasMore()) {
        break;
      }
      if (afterId.equals(result.lastId())) {
        // Cannot happen against the real scan (WHERE id > ? ORDER BY id), but a cursor that stops
        // advancing while claiming more rows would otherwise spin on the same batch forever.
        log.error("[{}] BIO attachment migration: cursor stuck at id {} — stopping", POD, afterId);
        break;
      }
      afterId = result.lastId();
    }

    if (batches >= MAX_BATCHES) {
      log.error("[{}] BIO attachment migration: hit the {}-batch ceiling — stopped early, "
          + "NOT complete", POD, MAX_BATCHES);
    }

    log.info("==== [{}] BIO attachment migration DONE (dryRun={}) batches={} scanned={} "
            + "migrated={} wouldMigrate={} skippedExisting={} skippedEmpty={} failed={} "
            + "bytesWritten={} ====",
        POD, dryRun, batches, scanned, migrated, wouldMigrate, skippedExisting, skippedEmpty.size(),
        failed.size(), bytesWritten);

    if (!skippedEmpty.isEmpty()) {
      log.info("[{}] BIO attachment migration: {} row(s) empty in Oracle, no object written: {}",
          POD, skippedEmpty.size(), truncate(skippedEmpty));
    }
    if (!failed.isEmpty()) {
      log.error("[{}] BIO attachment migration: {} row(s) FAILED — re-run to retry exactly "
          + "these: {}", POD, failed.size(), truncate(failed));
    }
  }

  /** The completeness gate: every row still holding bytes in Oracle must have an object. */
  private void verifyAll() {
    log.info("==== [{}] BIO attachment verify START (batchSize={}) ====",
        POD, properties.verifyBatchSize());

    String afterId = START_ID;
    int batches = 0;
    int scanned = 0;
    int present = 0;
    List<String> missing = new ArrayList<>();
    List<String> emptyInOracle = new ArrayList<>();

    while (batches < MAX_BATCHES) {
      batches++;
      BioAttachmentVerifyResult result =
          migrationService.verify(afterId, properties.verifyBatchSize());

      scanned += result.scanned();
      present += result.present();
      missing.addAll(result.missingIds());
      emptyInOracle.addAll(result.emptyInOracleIds());

      if (!result.hasMore()) {
        break;
      }
      if (afterId.equals(result.lastId())) {
        log.error("[{}] BIO attachment verify: cursor stuck at id {} — stopping", POD, afterId);
        break;
      }
      afterId = result.lastId();
    }

    log.info("==== [{}] BIO attachment verify DONE batches={} scanned={} present={} missing={} "
            + "emptyInOracle={} ====",
        POD, batches, scanned, present, missing.size(), emptyInOracle.size());

    if (missing.isEmpty()) {
      log.info("[{}] BIO ATTACHMENT MIGRATION GATE: PASS — every row with bytes in Oracle has an "
          + "object ({} present, {} empty in Oracle and excluded by design)",
          POD, present, emptyInOracle.size());
    } else {
      log.error("[{}] BIO ATTACHMENT MIGRATION GATE: FAIL — {} row(s) still have bytes in Oracle "
          + "but no object: {}", POD, missing.size(), truncate(missing));
    }
  }

  private static List<String> truncate(List<String> ids) {
    if (ids.size() <= MAX_IDS_LOGGED) {
      return ids;
    }
    List<String> head = new ArrayList<>(ids.subList(0, MAX_IDS_LOGGED));
    head.add("… and " + (ids.size() - MAX_IDS_LOGGED) + " more");
    return head;
  }
}

package ca.bc.gov.nrs.frep.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ONE-TIME CUTOVER TOOLING — settings for the Biodiversity attachment BLOB → object-storage
 * backfill. Delete with the rest of the migration tooling once Phase 4b has shipped.
 *
 * <p><b>DELETE-AFTER-BIO-ATTACHMENT-MIGRATION</b> — grep that tag to find every file and method that must go; the
 * checklist is in {@code backend/tools/bio-attachment-migration-runbook.md}.
 *
 * <p>Defaults live in {@code application.yml} and are env-overridable, so the whole run is driven by
 * {@code oc set env} against the Deployment — no rebuild, no code change. Everything is off unless
 * {@code enabled} is explicitly {@code true}, and {@code dryRun} defaults to {@code true} so the
 * accident case is a report rather than a write.
 *
 * <p>{@code frep.bio-attachment-migration.initial-delay} is deliberately absent here: it is read
 * straight from the environment by the {@code @Scheduled} annotation on the scheduler, which
 * cannot take a value from a bean. It is still a live setting — do not remove it from
 * {@code application.yml} on the grounds that nothing binds it.
 *
 * @param enabled         master switch; when false no scheduling infrastructure is created at all
 * @param dryRun          report what would move without writing; also suppresses the verify pass,
 *                        which would otherwise report every unmigrated row as a miss
 * @param batchSize       rows per migrate call (service clamps to 1000)
 * @param verifyBatchSize rows per verify call (service clamps to 1000)
 */
@ConfigurationProperties(prefix = "frep.bio-attachment-migration")
public record BioAttachmentMigrationProperties(
    boolean enabled,
    boolean dryRun,
    int batchSize,
    int verifyBatchSize
) {

  public BioAttachmentMigrationProperties {
    // Defensive defaults: a missing or zero value must not turn into a one-row-per-batch crawl.
    batchSize = batchSize <= 0 ? 250 : batchSize;
    verifyBatchSize = verifyBatchSize <= 0 ? 500 : verifyBatchSize;
  }
}

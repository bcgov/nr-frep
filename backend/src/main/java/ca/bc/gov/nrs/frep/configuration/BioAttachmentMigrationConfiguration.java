package ca.bc.gov.nrs.frep.configuration;

import ca.bc.gov.nrs.frep.service.v1.frep.BioAttachmentMigrationScheduler;
import ca.bc.gov.nrs.frep.service.v1.frep.BioAttachmentMigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ONE-TIME CUTOVER TOOLING — the single on/off switch for the Biodiversity attachment migration.
 * Delete this class, the scheduler, the service, the two repository methods they use and their
 * tests once the migration is verified and Phase 4b has shipped.
 *
 * <p><b>DELETE-AFTER-BIO-ATTACHMENT-MIGRATION</b> — grep that tag to find every file and method that must go; the
 * checklist is in {@code backend/tools/bio-attachment-migration-runbook.md}.
 *
 * <p>Everything hangs off {@code frep.bio-attachment-migration.enabled}. When it is false — which is
 * the default, in every environment — this configuration is not created, so there is no scheduler
 * bean and <b>no scheduling infrastructure in the context at all</b>. Nothing else in the
 * application uses {@code @Scheduled}, so {@link EnableScheduling} is deliberately scoped here
 * rather than added to the main application class: leaving it on permanently would keep a scheduler
 * thread alive in every pod for tooling that runs once.
 *
 * <p>Enabling it requires a Deployment env change and therefore a rollout, which is intentional —
 * the migration should never start because something got merged, only because someone turned it on.
 * See {@code backend/tools/bio-attachment-migration-runbook.md}.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(BioAttachmentMigrationProperties.class)
@ConditionalOnProperty(
    prefix = "frep.bio-attachment-migration", name = "enabled", havingValue = "true")
public class BioAttachmentMigrationConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(BioAttachmentMigrationConfiguration.class);

  @Bean
  public BioAttachmentMigrationScheduler bioAttachmentMigrationScheduler(
      BioAttachmentMigrationService migrationService,
      BioAttachmentMigrationProperties properties) {
    // Logged at startup so an operator can confirm the flag actually took effect — and in which
    // mode — without waiting out the initial delay to find out.
    String pod = System.getenv("HOSTNAME") == null ? "local" : System.getenv("HOSTNAME");
    log.warn("[{}] BIO attachment migration is ARMED on this pod: dryRun={} batchSize={} "
            + "verifyBatchSize={} — it will run once, shortly after startup",
        pod, properties.dryRun(), properties.batchSize(), properties.verifyBatchSize());
    return new BioAttachmentMigrationScheduler(migrationService, properties);
  }
}

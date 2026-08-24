package ca.bc.gov.nrs.frep.service.v1.frep;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.configuration.BioAttachmentMigrationProperties;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioAttachmentMigrationResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioAttachmentVerifyResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Cutover tooling — delete alongside {@link BioAttachmentMigrationScheduler} once Phase 4b ships.
 *
 * <p><b>DELETE-AFTER-BIO-ATTACHMENT-MIGRATION</b> — grep that tag to find every file and method that must go; the
 * checklist is in {@code backend/tools/bio-attachment-migration-runbook.md}.
 *
 * <p>The scheduler's whole job is the batch loop, so these cover the ways that loop could quietly
 * leave production half-migrated: stopping before the last batch, never advancing the cursor,
 * spinning forever on a cursor that does not move, verifying a dry run (which would report every
 * row as a miss), skipping the gate after a real run, or letting an exception kill the scheduler
 * thread before anything is logged.
 */
@ExtendWith(MockitoExtension.class)
class BioAttachmentMigrationSchedulerTest {

  @Mock private BioAttachmentMigrationService migrationService;

  private static BioAttachmentMigrationProperties props(boolean dryRun) {
    return new BioAttachmentMigrationProperties(true, dryRun, 250, 500);
  }

  private BioAttachmentMigrationScheduler scheduler(boolean dryRun) {
    return new BioAttachmentMigrationScheduler(migrationService, props(dryRun));
  }

  private static BioAttachmentMigrationResult migrated(String lastId, boolean hasMore) {
    return new BioAttachmentMigrationResult(
        250, 250, 0, 0, List.of(), List.of(), lastId, hasMore, 1024L);
  }

  private static BioAttachmentVerifyResult verified(
      String lastId, boolean hasMore, List<String> missing) {
    return new BioAttachmentVerifyResult(500, 500 - missing.size(), missing, List.of(), lastId,
        hasMore);
  }

  @Test
  void migrateWalksEveryBatchFeedingLastIdBackAsAfterId() {
    when(migrationService.migrate("0", 250, false)).thenReturn(migrated("250", true));
    when(migrationService.migrate("250", 250, false)).thenReturn(migrated("500", true));
    when(migrationService.migrate("500", 250, false)).thenReturn(migrated("640", false));
    when(migrationService.verify(anyString(), anyInt()))
        .thenReturn(verified("640", false, List.of()));

    scheduler(false).run();

    verify(migrationService).migrate("0", 250, false);
    verify(migrationService).migrate("250", 250, false);
    verify(migrationService).migrate("500", 250, false);
    // hasMore=false on the third batch must end it — a fourth call would re-scan from a cursor the
    // service already said was exhausted.
    verify(migrationService, times(3)).migrate(anyString(), anyInt(), anyBoolean());
  }

  @Test
  void verifyWalksEveryBatchAndUsesItsOwnBatchSize() {
    when(migrationService.migrate("0", 250, false)).thenReturn(migrated("640", false));
    when(migrationService.verify("0", 500)).thenReturn(verified("500", true, List.of()));
    when(migrationService.verify("500", 500)).thenReturn(verified("640", false, List.of()));

    scheduler(false).run();

    verify(migrationService).verify("0", 500);
    verify(migrationService).verify("500", 500);
    verify(migrationService, times(2)).verify(anyString(), anyInt());
  }

  @Test
  void dryRunMigratesButSkipsVerifyBecauseEveryRowWouldReadAsAMiss() {
    when(migrationService.migrate("0", 250, true))
        .thenReturn(new BioAttachmentMigrationResult(
            12, 0, 12, 0, List.of(), List.of(), "12", false, 0L));

    scheduler(true).run();

    verify(migrationService).migrate("0", 250, true);
    verify(migrationService, never()).verify(anyString(), anyInt());
    verifyNoMoreInteractions(migrationService);
  }

  @Test
  void aFailedGateStillCompletesTheRunRatherThanThrowing() {
    when(migrationService.migrate("0", 250, false)).thenReturn(migrated("640", false));
    when(migrationService.verify("0", 500))
        .thenReturn(verified("640", false, List.of("101", "102")));

    // The gate failing is a reportable outcome, not a crash: the operator needs the ids logged.
    assertDoesNotThrow(() -> scheduler(false).run());
    verify(migrationService).verify("0", 500);
  }

  @Test
  void aStuckCursorStopsInsteadOfLoopingForeverOnTheSameBatch() {
    // hasMore=true with lastId unchanged: the loop would otherwise re-issue this call indefinitely.
    when(migrationService.migrate("0", 250, false)).thenReturn(migrated("0", true));
    when(migrationService.verify("0", 500))
        .thenReturn(verified("640", false, List.of("101")));

    scheduler(false).run();

    verify(migrationService, times(1)).migrate("0", 250, false);
    // Stopping early leaves the migration incomplete, so the gate must still run and fail rather
    // than the run ending silently.
    verify(migrationService).verify("0", 500);
  }

  @Test
  void aSecondTriggerIsIgnoredSoRunsCanNeverOverlap() {
    when(migrationService.migrate("0", 250, true))
        .thenReturn(new BioAttachmentMigrationResult(
            0, 0, 0, 0, List.of(), List.of(), "0", false, 0L));

    BioAttachmentMigrationScheduler scheduler = scheduler(true);
    scheduler.run();
    scheduler.run();

    verify(migrationService, times(1)).migrate("0", 250, true);
  }

  @Test
  void aServiceFailureIsLoggedRatherThanKillingTheSchedulerThread() {
    when(migrationService.migrate("0", 250, false)).thenThrow(new IllegalStateException("boom"));

    assertDoesNotThrow(() -> scheduler(false).run());
    verify(migrationService, never()).verify(anyString(), anyInt());
  }
}

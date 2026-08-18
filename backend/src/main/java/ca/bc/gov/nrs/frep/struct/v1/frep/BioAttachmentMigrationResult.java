package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * Outcome of one migration batch (Oracle BLOB → object storage) for Biodiversity attachments.
 *
 * <p>Cutover tooling — remove with the rest of the migration endpoint once the BLOBs are gone.
 *
 * @param scanned          rows examined in this batch
 * @param migrated         objects written (always 0 when {@code dryRun})
 * @param wouldMigrate     rows that would be written — populated only when {@code dryRun}
 * @param skippedExisting  rows whose object was already present (re-run / idempotence)
 * @param skippedEmptyIds  ids whose Oracle BLOB is empty; they get no object, by design, and are
 *                         excluded from the completeness gate
 * @param failed           {@code "<id>: <error>"} per failure; re-running retries only these
 * @param lastId           highest attachment id processed — feed back as {@code afterId}
 * @param hasMore          whether a full batch came back, i.e. keep going
 * @param bytesWritten     total bytes put to object storage in this batch
 */
public record BioAttachmentMigrationResult(
    int scanned,
    int migrated,
    int wouldMigrate,
    int skippedExisting,
    List<String> skippedEmptyIds,
    List<String> failed,
    String lastId,
    boolean hasMore,
    long bytesWritten) {}

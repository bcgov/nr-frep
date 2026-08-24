package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * Completeness check for one batch: every Biodiversity attachment row whose Oracle BLOB holds bytes
 * must have an object in storage. This is the go-live gate.
 *
 * <p>{@code emptyInOracleIds} are rows with no bytes on either side — legacy junk that can never
 * have an object. They are reported separately rather than counted as misses, because folding them
 * into {@code missingIds} would make the gate permanently unpassable.
 *
 * <p>Cutover tooling — remove with the rest of the migration code once the BLOBs are gone.
 *
 * <p><b>DELETE-AFTER-BIO-ATTACHMENT-MIGRATION</b> — grep that tag to find every file and method that must go; the
 * checklist is in {@code backend/tools/bio-attachment-migration-runbook.md}.
 *
 * @param scanned          rows examined in this batch
 * @param present          rows with an object in storage
 * @param missingIds       rows with bytes in Oracle but NO object — these must be re-migrated
 * @param emptyInOracleIds rows with no object and no bytes in Oracle either — expected, excluded
 * @param lastId           highest attachment id processed — feed back as {@code afterId}
 * @param hasMore          whether a full batch came back, i.e. keep going
 */
public record BioAttachmentVerifyResult(
    int scanned,
    int present,
    List<String> missingIds,
    List<String> emptyInOracleIds,
    String lastId,
    boolean hasMore) {}

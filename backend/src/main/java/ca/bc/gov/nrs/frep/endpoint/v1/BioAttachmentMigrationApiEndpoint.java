package ca.bc.gov.nrs.frep.endpoint.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.BioAttachmentMigrationResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioAttachmentVerifyResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * ONE-TIME CUTOVER TOOLING — HTTP contract for the Biodiversity attachment BLOB → object-storage
 * migration. Implemented by
 * {@link ca.bc.gov.nrs.frep.controller.v1.BioAttachmentMigrationApiController}.
 *
 * <p><b>Delete this interface, the controller, the service, the two repository methods they use and
 * their tests once the migration is verified and Phase 4b has shipped.</b>
 *
 * <p><b>⚠️ Authorization is deliberately left open</b> — no {@code @PreAuthorize}, so any
 * authenticated caller can invoke these. That is a conscious, temporary choice for a pre-go-live
 * migration on an application that is not yet public. To gate it, add one line per method:
 *
 * <pre>{@code   @PreAuthorize(FrepAuthorities.ADMIN)}</pre>
 *
 * <p>Note that URL-level rules no longer cover {@code /api/v1/admin/**} — the old coarse rule was
 * dropped in favour of per-endpoint {@code @PreAuthorize} (see {@code ApiAuthorizationCustomizer}),
 * so sitting under {@code /admin} confers no protection by itself. Authentication is still required:
 * {@code anyRequest().authenticated()} applies.
 *
 * <p>Usage — drive the batches with a loop, feeding {@code lastId} back as {@code afterId}:
 * <pre>
 *   POST /api/v1/admin/bio-attachments/migrate?afterId=0&amp;limit=250&amp;dryRun=true
 *   POST /api/v1/admin/bio-attachments/migrate?afterId=0&amp;limit=250
 *   GET  /api/v1/admin/bio-attachments/verify?afterId=0&amp;limit=500
 * </pre>
 * Continue while {@code hasMore} is true. Both operations are safe to re-run.
 */
@RequestMapping("/api/v1/admin/bio-attachments")
public interface BioAttachmentMigrationApiEndpoint {

  /**
   * Migrate one batch of attachment bytes to object storage.
   *
   * @param afterId process ids strictly greater than this; start at {@code "0"}
   * @param limit   rows per batch (clamped to 1000)
   * @param dryRun  when true, report what would move without writing anything
   */
  @PostMapping("/migrate")
  ResponseEntity<BioAttachmentMigrationResult> migrate(
      @RequestParam(defaultValue = "0") String afterId,
      @RequestParam(defaultValue = "250") int limit,
      @RequestParam(defaultValue = "false") boolean dryRun);

  /**
   * Confirm every row holding bytes in Oracle has an object. The go-live gate, and the gate on
   * removing the read-path BLOB fallback in Phase 4b.
   */
  @GetMapping("/verify")
  ResponseEntity<BioAttachmentVerifyResult> verify(
      @RequestParam(defaultValue = "0") String afterId,
      @RequestParam(defaultValue = "500") int limit);
}

package ca.bc.gov.nrs.frep.controller.v1;

import ca.bc.gov.nrs.frep.endpoint.v1.BioAttachmentMigrationApiEndpoint;
import ca.bc.gov.nrs.frep.service.v1.frep.BioAttachmentMigrationService;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioAttachmentMigrationResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioAttachmentVerifyResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * ONE-TIME CUTOVER TOOLING — mappings declared on {@link BioAttachmentMigrationApiEndpoint}, which
 * also documents why authorization is left open and how to gate it.
 *
 * <p><b>Delete with the rest of the migration tooling once Phase 4b has shipped.</b>
 */
@RestController
public class BioAttachmentMigrationApiController implements BioAttachmentMigrationApiEndpoint {

  private final BioAttachmentMigrationService migrationService;

  public BioAttachmentMigrationApiController(BioAttachmentMigrationService migrationService) {
    this.migrationService = migrationService;
  }

  @Override
  public ResponseEntity<BioAttachmentMigrationResult> migrate(
      String afterId, int limit, boolean dryRun) {
    return ResponseEntity.ok(migrationService.migrate(afterId, limit, dryRun));
  }

  @Override
  public ResponseEntity<BioAttachmentVerifyResult> verify(String afterId, int limit) {
    return ResponseEntity.ok(migrationService.verify(afterId, limit));
  }
}

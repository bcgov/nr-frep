package ca.bc.gov.nrs.frep.repository.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentContent;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioAttachmentRef;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlot;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlotRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratum;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratumRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BiodiversityOpening;
import ca.bc.gov.nrs.frep.struct.v1.frep.RiparianNotes;
import ca.bc.gov.nrs.frep.struct.v1.frep.StratumComputed;
import java.util.List;
import java.util.UUID;

/**
 * Contract for protocol-checklist writes (FREP210/211/212 + administration/notes/attachments +
 * submit/unsubmit). Implemented by
 * {@link ca.bc.gov.nrs.frep.repository.v1.impl.ProtocolChecklistWriteRepositoryImpl}.
 */
public interface ProtocolChecklistWriteRepository {
  String submit(String resourceValueType, String checklistId, String userId);
  String unsubmit(String resourceValueType, String checklistId, String userId);

  /**
   * Check a Biodiversity checklist out to a field device: ACT → RDO, stamping the device's token.
   * The proc guards the ACT precondition in its WHERE clause, so a second checkout of an
   * already-checked-out checklist returns an error rather than stealing it. Returns the (possibly
   * empty) error message, as {@link #submit} does.
   *
   * <p>The token is minted in Java and only stored here — the comparison lives in the service, so
   * the rule exists in exactly one place.
   */
  String takeOffline(String checklistId, UUID deviceCheckoutGuid, String userId);

  /**
   * Return a checked-out Biodiversity checklist to ACT and clear its token. One proc for all three
   * callers — the holder releasing its own checkout, an admin recovering a stranded device, and the
   * RDO → ACT flip at the end of a sync — because at the database level they are the same write.
   * Authorization and the token comparison differ, and both live above this layer.
   */
  String activate(String checklistId, String userId);
  BiodiversityOpening getBiodiversityOpening(String checklistId);
  BiodiversityOpening saveBiodiversityOpening(BiodiversityOpening o, String userId);
  void assignBiodiversityLead(String checklistId, String resourceType, String newLead,
      String oldLead, String oldRevision, String userId);
  List<BioStratumRow> listBioStrata(String checklistId);
  BioStratum getBioStratum(String stratumId);
  BioStratum saveBioStratum(BioStratum s, String userId);
  String deleteBioStratum(String stratumId, String revisionCount);
  StratumComputed getStratumComputed(String stratumId);
  StratumComputed getNewStratumComputed(String checklistId);
  List<BioPlotRow> listBioPlots(String stratumId);
  BioPlot getBioPlot(String plotId);
  BioPlot saveBioPlot(BioPlot p, String userId);
  String deleteBioPlot(String plotId, String revisionCount);
  /** Resolve the parent biodiversity checklist id for a stratum / plot — for the view-only guard. */
  String checklistIdForStratum(String stratumId);
  String checklistIdForPlot(String plotId);
  RiparianNotes getNotes(String checklistId, String resourceType);
  RiparianNotes saveNotes(RiparianNotes o, String resourceType, String userId);
  /** One page of attachment metadata, ordered by id. Sizes are filled from object storage. */
  List<AttachmentRow> getAttachments(String checklistId, String resourceType, int page, int size);

  /** Total attachments on the checklist, for the pager. */
  int countAttachments(String checklistId, String resourceType);
  AttachmentContent getAttachmentContent(
      String checklistId, String resourceType, String attachmentId);

  /**
   * One keyset page of every Biodiversity attachment in the schema, ascending by id — the scan
   * behind the one-time BLOB → object-storage migration. Unlike {@link #getAttachments}, this is
   * not scoped to a checklist.
   *
   * <p>Cutover tooling — {@code DELETE-AFTER-BIO-ATTACHMENT-MIGRATION}. Remove this method with the
   * rest of the migration code; nothing in the application calls it.
   */
  List<BioAttachmentRef> listBioAttachmentsForMigration(String afterId, int limit);

  /**
   * The attachment's bytes as stored in the Oracle BLOB, bypassing the object-storage read path.
   * Migrated Biodiversity rows return an empty array here, which is exactly how the migration
   * detects what still needs copying.
   *
   * <p>Cutover tooling — {@code DELETE-AFTER-BIO-ATTACHMENT-MIGRATION}. Remove this method with the
   * rest of the migration code; nothing in the application calls it.
   */
  AttachmentContent getAttachmentContentFromBlob(
      String checklistId, String resourceType, String attachmentId);
  void saveAttachment(
      String checklistId, String resourceType, String fileName, String description, String mimeType,
      byte[] bytes, String userId);
  void deleteAttachment(String checklistId, String resourceType, String attachmentId);
}

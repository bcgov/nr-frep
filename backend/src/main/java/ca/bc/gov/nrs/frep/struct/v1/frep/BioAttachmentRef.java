package ca.bc.gov.nrs.frep.struct.v1.frep;

/**
 * The minimum needed to migrate one Biodiversity attachment's bytes to object storage: the
 * attachment id (which is the object key), its parent checklist id, and that checklist's resource
 * type — all three required by {@code FREP_CHECKLIST_ATTACHMENTS.GET_BLOB}.
 *
 * <p>{@code resourceType} is read per row rather than assumed. Biodiversity is <em>not</em> a single
 * constant: {@code SLB} and {@code SLR} both count as Biodiversity while the SLB→SLR rename is in
 * flight, and the whole app resolves the code from {@code FREP_RESOURCE_VALUE} instead of hardcoding
 * it. Passing the wrong code to the legacy package would look up the wrong protocol.
 *
 * <p>Cutover tooling — remove with the rest of the migration code once the BLOBs are gone.
 *
 * <p><b>DELETE-AFTER-BIO-ATTACHMENT-MIGRATION</b> — grep that tag to find every file and method that must go; the
 * checklist is in {@code backend/tools/bio-attachment-migration-runbook.md}.
 *
 * @param attachmentId {@code BIODIVERSITY_CHKLST_ATTACH_ID}, trimmed of Oracle's NUMBER ".0"
 * @param checklistId  {@code BIODIVERSITY_CHECKLIST_ID}
 * @param resourceType {@code FREP_RESOURCE_VALUE.FREP_RESOURCE_VALUE_TYPE_CODE} for that checklist
 */
public record BioAttachmentRef(String attachmentId, String checklistId, String resourceType) {}

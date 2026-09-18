package ca.bc.gov.nrs.frep.repository.v1;

import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistSectionData;
import java.util.UUID;

/** Contract for read-only checklist section loads (FREP210/211/212 reads). */
public interface ChecklistRepository {
  ChecklistSectionData getBioOpening(String checklistId);
  ChecklistSectionData getBioStratum(String checklistId);
  ChecklistSectionData getBioPlots(String checklistId);

  /** The record's actual protocol code (SLB legacy / SLR going forward), resolved from the DB. */
  String resolveResourceType(String checklistId);

  /**
   * The checklist's status code — {@code ACT} / {@code RDO} / {@code SUB}. Null when the row does not
   * exist, which callers leave to the downstream proc to report as not-found.
   *
   * <p>Biodiversity had no status read at all before this: status reached the UI only via the
   * checklist header, and no write path consulted it. That is why a {@code SUB} SLR checklist was
   * still writable through the API.
   */
  String getBioChecklistStatus(String checklistId);

  /**
   * The GUID of the device currently holding this checklist's checkout, or null when it is not
   * checked out. Reading is a direct SELECT; setting and clearing it is proc work
   * ({@code FREP_TOMBSTONE.take_offline} / {@code .activate}) because no UPDATE grant exists here.
   */
  UUID getBioDeviceCheckoutGuid(String checklistId);
}

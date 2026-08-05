package ca.bc.gov.nrs.frep.repository.v1;

import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistSectionData;

/** Contract for read-only checklist section loads (FREP210/211/212 reads). */
public interface ChecklistRepository {
  ChecklistSectionData getBioOpening(String checklistId);
  ChecklistSectionData getBioStratum(String checklistId);
  ChecklistSectionData getBioPlots(String checklistId);

  /** The record's actual protocol code (SLB legacy / SLR going forward), resolved from the DB. */
  String resolveResourceType(String checklistId);
}

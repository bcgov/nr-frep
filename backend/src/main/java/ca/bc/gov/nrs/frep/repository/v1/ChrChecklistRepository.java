package ca.bc.gov.nrs.frep.repository.v1;

import java.util.UUID;

/** Contract for CHR checklist stored-proc reads / status + unsubmit ({@code FREP_CHR_CHECKLIST_PKG}). */
public interface ChrChecklistRepository {
  String getChecklistStatus(long checklistId);
  long getRevisionCount(long checklistId);
  String getLastUpdatedUser(long checklistId);
  UUID parseDeviceCheckoutGuid(byte[] bytes);
  UUID getDeviceCheckoutGuid(long checklistId);
  String unsubmitChecklist(String checklistId, String userId);
  void throwIfUnsubmitError(String checklistId, String userId);
}

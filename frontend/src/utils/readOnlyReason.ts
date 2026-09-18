// The "Read only" banner copy, shared by the CHR and SLR checklist pages.
//
// Both pages lock for the same two reasons — checked out to a device, or submitted — and both used to
// spell them out themselves. The submitted sentence stayed identical by luck; the checked-out one
// drifted into two descriptions of the same state ("checked out offline" vs "checked out to a field
// device", "Upload it" vs "Check it in"), which read as two different situations to anyone who works
// across both protocols. Protocol-specific reasons (SLB historical, CHR's non-active statuses) stay
// on their own pages — only what is genuinely common lives here.

/**
 * Checked out to a device (`RDO`).
 *
 * Written for the reader this banner actually has: someone looking at the **online** copy, which is
 * never the person holding the device. So it says what has to happen rather than instructing them to
 * press something they cannot reach — which also keeps it honest across the two protocols, whose
 * buttons differ (CHR "Sync changes", SLR "Check in") while the effect is the same: `RDO` → `ACT`.
 */
export const READ_ONLY_CHECKED_OUT =
  'This checklist is checked out to a field device, so the online copy is read-only. It becomes ' +
  'editable again when that device checks it in, or when an administrator reactivates it.';

/** Submitted (`SUB`). Already word-for-word identical on both pages; kept that way by sharing it. */
export const READ_ONLY_SUBMITTED =
  'This checklist has been submitted and is read-only. Unsubmit it to make changes.';

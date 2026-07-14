import type { CheckList } from '@/types/chrChecklist';

import { CHR_STATUS } from '@/types/chrChecklist';
import { formatShortDate } from '@/utils/date';

// Proactive staleness detection for offline CHR copies. This is a UX layer only — the backend's
// deviceCheckoutGuid upload guard remains the source of truth, so a probe-vs-upload race is harmless.

export type StalenessVerdict =
  | 'CURRENT' // your checkout still matches the server
  | 'RECLAIMED' // checkout was reset/reactivated (or re-checked-out elsewhere) — server guid differs
  | 'SUBMITTED_ELSEWHERE' // already submitted on the server
  | 'GONE' // no longer exists on the server (404)
  | 'UNVERIFIED'; // couldn't check (offline / probe failed)

export type UpdateAudit = {
  updateUserid?: string;
  updateTimestamp?: string;
};

/**
 * Compare a locally-held offline copy against the authoritative server checklist. Only the
 * "still checked out to me" (deviceCheckoutGuid) and "already submitted" signals matter — the same
 * signals the upload guard uses. GONE/UNVERIFIED are decided by the caller (404 / probe failure).
 */
export const classifyStaleness = (
  localGuid: string | undefined,
  server: Pick<CheckList, 'status' | 'deviceCheckoutGuid'>,
): StalenessVerdict => {
  if (server.status === CHR_STATUS.SUBMITTED) return 'SUBMITTED_ELSEWHERE';
  if ((server.deviceCheckoutGuid ?? '') !== (localGuid ?? '')) return 'RECLAIMED';
  return 'CURRENT';
};

export const isStale = (verdict: StalenessVerdict): boolean =>
  verdict === 'RECLAIMED' || verdict === 'SUBMITTED_ELSEWHERE' || verdict === 'GONE';

/**
 * "Last updated by {user} on {date}." — strips the IDIR\ prefix and formats the date to the app
 * standard (MMM d, yyyy) via {@link formatShortDate}. The server timestamp is "yyyy-MM-dd HH:mm:ss";
 * we display the date portion. Omits missing parts.
 */
export const formatUpdatedBy = (audit: UpdateAudit | undefined): string => {
  const who = audit?.updateUserid?.replace(/^IDIR\\/i, '').trim();
  const raw = audit?.updateTimestamp?.trim();
  const when = raw ? formatShortDate(raw.slice(0, 10)) : '';
  if (who && when) return `Last updated by ${who} on ${when}.`;
  if (who) return `Last updated by ${who}.`;
  if (when) return `Last updated on ${when}.`;
  return '';
};

export type StalenessBanner = { kind: 'warning' | 'info'; title: string; subtitle: string };

/** Banner content for an offline copy's verdict, or null when CURRENT (no banner needed). */
export const stalenessBanner = (
  verdict: StalenessVerdict,
  audit?: UpdateAudit,
): StalenessBanner | null => {
  const suffix = formatUpdatedBy(audit);
  switch (verdict) {
    case 'SUBMITTED_ELSEWHERE':
      return {
        kind: 'warning',
        title: 'Offline copy out of date',
        subtitle:
          `This checklist has been submitted on the server, so your offline copy can't be uploaded. ${suffix}`.trim(),
      };
    case 'RECLAIMED':
      return {
        kind: 'warning',
        title: 'Offline copy out of date',
        subtitle:
          `This checklist's checkout was reset on the server, so your offline copy can't be uploaded. ${suffix}`.trim(),
      };
    case 'GONE':
      return {
        kind: 'warning',
        title: 'Checklist no longer exists',
        subtitle:
          "This checklist no longer exists on the server, so your offline copy can't be uploaded.",
      };
    case 'UNVERIFIED':
      return {
        kind: 'info',
        title: "Can't verify offline copy",
        subtitle: "You're offline, so we can't check whether this offline copy is up to date.",
      };
    default:
      return null; // CURRENT
  }
};

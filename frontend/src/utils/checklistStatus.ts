// UI labels + Carbon Tag colours for FREP checklist status codes — the single source of truth shared
// by the Checklist Search and Accepted Sites tables (and the status filter dropdown).

export const STATUS_LABELS: Record<string, string> = {
  ACT: 'Active',
  SUB: 'Submitted',
  RDO: 'Read-only',
};

/** Friendly label for a status code; falls back to the backend description, then the raw code. */
export const statusLabel = (code: string | undefined, fallback?: string): string => {
  const c = (code ?? '').toUpperCase();
  return STATUS_LABELS[c] ?? (fallback || c);
};

export type StatusTagType = 'green' | 'blue' | 'cool-gray' | 'gray';

/**
 * Carbon Tag colour for a status code. Mirrors legacy ("green indicates the checklist is submitted"):
 * SUB → green; ACT (active) → blue; RDO (read-only) → cool-gray; anything else → gray.
 */
export const statusTagType = (code: string | undefined): StatusTagType => {
  switch ((code ?? '').toUpperCase()) {
    case 'SUB':
      return 'green';
    case 'ACT':
      return 'blue';
    case 'RDO':
      return 'cool-gray';
    default:
      return 'gray';
  }
};
